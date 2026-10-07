#!/usr/bin/env python3
"""Check one release jar's contents and internal class references.

Usage: check_release_jar.py <jar> <loader> <minecraft-version>

The loader is fabric, forge, neoforge, paper or velocity; Velocity passes `-` as its version.
Prints `<jar file name>: <rule>: <detail>` lines to stderr and exits 1 when the jar breaks a rule,
exits 0 silently when it is clean, and exits 2 on bad arguments.
"""
from __future__ import annotations

import json
import re
import struct
import sys
import zipfile
from pathlib import Path

PREFIX = "_959/server_waypoint/"
LOADERS = ("fabric", "forge", "neoforge", "paper", "velocity")
# Descriptors, signatures and annotation values name classes as `L<internal name>;`. jdeps misses the
# annotation-only ones, such as `@JsonAdapter(NavigationMethodSetJsonAdapter.class)`.
DESCRIPTOR_REFERENCE = re.compile(rb"L(_959/server_waypoint/[^;<>]+)")
MULTI_RELEASE_PREFIX = re.compile(r"^META-INF/versions/(\d+)/")
MANIFEST = "META-INF/MANIFEST.MF"
CLASS_MAGIC = b"\xca\xfe\xba\xbe"
PROXY_ONLY_CLASS = re.compile(
    r"^_959/server_waypoint/crossserver/transport/(TcpCoordinator|CoordinatorTransport)(\$[^/]*)?\.class$")
CHAT_SPRITES = "assets/server_waypoint/chat-sprites.json"
VANILLA_CHAT_SPRITES = "_959/server_waypoint/text/chat/VanillaChatSprites.class"
CHAT_SPRITES_SINCE = (1, 21, 9)
VELOCITY_FORBIDDEN_PREFIXES = ("lang/", "assets/") + tuple(
    PREFIX + package + "/" for package in ("command", "config", "navigation", "text", "translation"))
VELOCITY_FORBIDDEN_ENTRIES = ("SERVER_WAYPOINT_CREDITS.txt", "_959/server_waypoint/core/WaypointServerCore.class")
BACKEND_REQUIRED_ENTRIES = ("lang/en_us.json", "SERVER_WAYPOINT_CREDITS.txt")
MIXIN_CONFIG_SUFFIX = ".mixins.json"
MIXIN_CLASS_LISTS = ("mixins", "client", "server")

# Constant-pool entry sizes after the tag byte, except Utf8 (tag 1), whose size is in its first two bytes.
FIXED_SIZES = {7: 2, 8: 2, 16: 2, 19: 2, 20: 2, 3: 4, 4: 4, 9: 4, 10: 4, 11: 4, 12: 4, 17: 4, 18: 4,
               5: 8, 6: 8, 15: 3}


def class_references(data: bytes) -> set[str]:
    """Internal names under `_959/server_waypoint/` that one class file references."""

    def read(offset: int, size: int) -> bytes:
        if offset + size > len(data):
            raise ValueError(f"class file ends early at byte {len(data)}")
        return data[offset:offset + size]

    def u2(offset: int) -> int:
        return struct.unpack(">H", read(offset, 2))[0]

    def skip_attributes(offset: int) -> int:
        for _ in range(u2(offset)):
            length = struct.unpack(">I", read(offset + 4, 4))[0]
            read(offset + 8, length)
            offset += 6 + length
        return offset + 2

    if read(0, 4) != CLASS_MAGIC:
        raise ValueError("not a class file")
    count = struct.unpack(">H", read(8, 2))[0]
    offset = 10
    index = 1
    utf8 = {}
    class_name_indexes = []
    while index < count:
        tag = read(offset, 1)[0]
        offset += 1
        if tag == 1:
            length = struct.unpack(">H", read(offset, 2))[0]
            utf8[index] = read(offset + 2, length)
            offset += 2 + length
        elif tag in FIXED_SIZES:
            entry = read(offset, FIXED_SIZES[tag])
            if tag == 7:
                class_name_indexes.append(struct.unpack(">H", entry)[0])
            offset += FIXED_SIZES[tag]
            if tag in (5, 6):
                index += 1  # Long and Double take two constant-pool slots.
        else:
            raise ValueError(f"unknown constant-pool tag {tag} at entry {index}")
        index += 1

    # The rest only needs to be whole: flags, this and super class, interfaces, fields, methods, attributes.
    offset += 6
    offset += 2 + 2 * u2(offset)
    for _ in range(2):  # fields, then methods
        members = u2(offset)
        offset += 2
        for _ in range(members):
            read(offset, 6)  # flags, name and descriptor
            offset = skip_attributes(offset + 6)
    offset = skip_attributes(offset)
    if offset != len(data):
        raise ValueError(f"{len(data) - offset} bytes follow the end of the class file")

    references = set()
    for name_index in class_name_indexes:
        if name_index not in utf8:
            raise ValueError(f"class entry points to constant {name_index}, which is not a Utf8 entry")
        name = utf8[name_index].decode("utf-8", "replace").lstrip("[")
        if name.startswith("L") and name.endswith(";"):
            name = name[1:-1]
        references.add(name)
    for value in utf8.values():
        references.update(match.decode("utf-8", "replace") for match in DESCRIPTOR_REFERENCE.findall(value))
    return {name for name in references if name.startswith(PREFIX)}


def mixin_classes(config: bytes) -> list[str]:
    """Class file paths a mixin config names in its `mixins`, `client` and `server` arrays."""
    try:
        document = json.loads(config.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"not JSON: {error}") from None
    if not isinstance(document, dict):
        raise ValueError("not a JSON object")
    package = document.get("package", "")
    if not isinstance(package, str):
        raise ValueError("package is not a string")
    paths = []
    for key in MIXIN_CLASS_LISTS:
        names = document.get(key) or []
        if not isinstance(names, list) or not all(isinstance(name, str) for name in names):
            raise ValueError(f"{key} is not a list of class names")
        paths += [(f"{package}.{name}" if package else name).replace(".", "/") + ".class" for name in names]
    return paths


def multi_release(manifest: bytes) -> bool:
    """True when the manifest's main section declares `Multi-Release: true`, as the JVM requires."""
    attributes = []
    for line in manifest.decode("utf-8", "replace").splitlines():
        if not line:
            break  # the main section ends at the first blank line
        if line.startswith(" ") and attributes:
            attributes[-1] += line[1:]  # continuation line
        else:
            attributes.append(line)
    return any(name.strip().lower() == "multi-release" and value.strip().lower() == "true"
               for name, _, value in (attribute.partition(":") for attribute in attributes))


def java_release(data: bytes) -> int:
    """The oldest Java release that loads a class file (major version 61 is Java 17), or 0 if unknown."""
    return struct.unpack(">H", data[6:8])[0] - 44 if data[:4] == CLASS_MAGIC and len(data) >= 8 else 0


def check_jar(path: Path, loader: str, minecraft_version: str) -> list[str]:
    """Violations formatted as `<rule>: <detail>`, or an empty list when the jar is clean."""
    if loader not in LOADERS:
        raise ValueError(f"unknown loader {loader!r}; expected one of {', '.join(LOADERS)}")
    velocity = loader == "velocity"
    if not velocity:
        try:
            version = tuple(map(int, minecraft_version.split(".")))
        except ValueError:
            raise ValueError(f"not a Minecraft version: {minecraft_version!r}") from None

    with zipfile.ZipFile(path) as jar:
        # Directory entries carry no content, so the rules look at files only.
        entries = sorted(name for name in jar.namelist() if not name.endswith("/"))
        classes = {name: jar.read(name) for name in entries if name.endswith(".class")}
        manifest = jar.read(MANIFEST) if MANIFEST in entries else b""
        mixin_configs = {name: jar.read(name) for name in entries
                         if "/" not in name and name.endswith(MIXIN_CONFIG_SUFFIX)}
    present = set(entries)
    # Content rules judge each entry by its logical path, so a META-INF/versions/<n>/ copy is judged too.
    logical = {name: MULTI_RELEASE_PREFIX.sub("", name) for name in entries}

    # The JVM reads META-INF/versions/<n>/ only in a Multi-Release jar, and only on Java <n> or later. The jar's
    # own base classes set the oldest Java it runs on, so a reference resolves only against what that Java sees.
    is_multi_release = multi_release(manifest)
    versioned = {name: (int(match[1]), logical[name][:-len(".class")])
                 for name in classes if (match := MULTI_RELEASE_PREFIX.match(name))}
    base = {name[:-len(".class")] for name in classes if name not in versioned}
    oldest_java = max((java_release(data) for name, data in classes.items() if name not in versioned), default=0)
    visible = {}

    def provided_on(release: int) -> set[str]:
        if release not in visible:
            visible[release] = base | {class_name for java, class_name in versioned.values()
                                       if is_multi_release and java <= release}
        return visible[release]

    forbidden = [name for name in entries if logical[name].startswith("META-INF/maven/")]
    required = []
    if velocity:
        forbidden += [name for name in entries if logical[name].startswith(VELOCITY_FORBIDDEN_PREFIXES)
                      or logical[name] in VELOCITY_FORBIDDEN_ENTRIES]
        required.append("velocity-plugin.json")
    else:
        forbidden += [name for name in entries
                      if logical[name].startswith(PREFIX + "proxy/") or PROXY_ONLY_CLASS.match(logical[name])]
        required += BACKEND_REQUIRED_ENTRIES
        if version < CHAT_SPRITES_SINCE:
            forbidden += [name for name in entries if logical[name] in (CHAT_SPRITES, VANILLA_CHAT_SPRITES)]
        else:
            required.append(CHAT_SPRITES)

    violations = [f"forbidden-entry: {name}" for name in sorted(set(forbidden))]
    violations += [f"missing-entry: {name}" for name in required if name not in present]
    for config, data in mixin_configs.items():
        try:
            violations += [f"missing-mixin-class: {name} (named by {config})"
                           for name in mixin_classes(data) if name not in present]
        except ValueError as error:
            violations.append(f"unreadable-mixin-config: {config} ({error})")
    reported = set()
    for entry, data in classes.items():
        try:
            references = class_references(data)
        except ValueError as error:
            violations.append(f"unreadable-class: {entry} ({error})")
            continue
        if entry in versioned and not is_multi_release:
            continue  # never loaded
        release = max(versioned[entry][0], oldest_java) if entry in versioned else oldest_java
        for name in sorted(references - provided_on(release) - reported):
            reported.add(name)
            violations.append(f"missing-class: {name} (referenced by {entry})")
    return violations


def main(arguments: list[str]) -> int:
    if len(arguments) != 3:
        print("usage: check_release_jar.py <jar> <loader> <minecraft-version>", file=sys.stderr)
        return 2
    jar = Path(arguments[0])
    if not jar.is_file():
        print(f"check_release_jar.py: no such jar: {jar}", file=sys.stderr)
        return 2
    try:
        violations = check_jar(jar, arguments[1], arguments[2])
    except ValueError as error:
        print(f"check_release_jar.py: {error}", file=sys.stderr)
        return 2
    for violation in violations:
        print(f"{jar.name}: {violation}", file=sys.stderr)
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
