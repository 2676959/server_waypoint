"""Resolve dedicated installed test clients and export offline HMCL launches."""
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

from prepare import command, properties
from runner import read_launch_script, isolated_launch, sha256


PROFILE = re.compile(r"([0-9]+(?:\.[0-9]+)*)-(fabric|forge|neoforge)", re.IGNORECASE)
TOOL = Path(__file__).resolve().parent


def default_clients():
    configured = os.environ.get("LIVE_GAME_TEST_CLIENTS")
    return Path(configured).expanduser() if configured else Path.home() / ".minecraft/versions"


def version_key(value):
    parts = tuple(map(int, value.split(".")))
    return parts + (0,) * (5 - len(parts))


def expand_versions(versions, requested):
    names = []
    for value in requested:
        if re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", value):
            matches = sorted(path.name for path in versions.iterdir() if path.is_dir()
                             and PROFILE.fullmatch(path.name)
                             and PROFILE.fullmatch(path.name).group(1) == value) if versions.is_dir() else []
            names.extend(matches or [value])
        else:
            names.append(value)
    return list(dict.fromkeys(name.lower() for name in names))


def select_client(repo, versions, name):
    match = PROFILE.fullmatch(name)
    if match is None:
        raise ValueError("Expected <minecraft-version>-<mod-loader>: " + name)
    matches = [path for path in versions.iterdir() if path.is_dir() and path.name.lower() == name.lower()]
    if len(matches) != 1:
        raise ValueError(("Ambiguous" if matches else "Missing") + " installed test client: " + name)
    client = matches[0].resolve()
    if client.parent != versions.resolve():
        raise ValueError("Test client must reside inside --clients: " + name)
    minecraft, loader = match.group(1), match.group(2).lower()
    target = minecraft + "-" + loader
    targets = repo / "mods/versions"
    if not (targets / target / "gradle.properties").is_file():
        compatible = []
        for path in targets.glob("*-" + loader + "/gradle.properties"):
            # The final build ranges used by copy_client_builds.sh overlap these builds.
            if path.parent.name in {"1.21.3-fabric", "1.21.3-neoforge"}:
                continue
            range_text = properties(path).get("mcVersionRange", "")
            low, _, high = range_text.partition("-")
            if low and version_key(low) <= version_key(minecraft) <= version_key(high or low):
                compatible.append(path.parent.name)
        if len(compatible) != 1:
            raise ValueError("No unique supported build for " + name)
        target = compatible[0]
    return {"name": client.name, "target": target, "minecraft": minecraft}


def java_major(home):
    text = (home / "release").read_text()
    match = re.search(r'^JAVA_VERSION="(?:1\.)?(\d+)', text, re.MULTILINE)
    if match is None:
        raise ValueError("Cannot identify Java version in " + str(home))
    return int(match.group(1))


def jdk_homes():
    candidates = [Path(os.environ[key]) for key in ("JAVA_HOME", "JDK_HOME") if os.environ.get(key)]
    for pattern in ("/Library/Java/JavaVirtualMachines/*/Contents/Home",
                    "/opt/homebrew/opt/openjdk*/libexec/openjdk.jdk/Contents/Home",
                    "/usr/lib/jvm/*", str(Path.home() / ".gradle/jdks/*")):
        import glob
        candidates.extend(map(Path, glob.glob(pattern)))
    return list(dict.fromkeys(path.resolve() for path in candidates if (path / "release").is_file()))


def choose_jdk(homes, minimum):
    compatible = [(java_major(home), home) for home in homes
                  if (home / "bin/java").is_file() and (home / "bin/javac").is_file()
                  and java_major(home) >= minimum]
    if not compatible:
        raise ValueError("Missing JDK " + str(minimum) + "+; provide --jdk or JAVA_HOME")
    return min(compatible, key=lambda entry: entry[0])[1]


def find_hmcl(explicit=None):
    configured = explicit or os.environ.get("LIVE_GAME_TEST_HMCL")
    if not configured:
        raise ValueError("Provide --hmcl /path/to/HMCL.jar, LIVE_GAME_TEST_HMCL, or --exports")
    return Path(configured).expanduser().resolve(strict=True)


def compile_exporter(hmcl, jdk, output):
    classes = output / "launcher-classes"
    classes.mkdir()
    dependencies = sorted((hmcl.parent / ".hmcl/dependencies").glob("*/openjfx/*.jar"))
    cp = os.pathsep.join(map(str, [hmcl, *dependencies]))
    command([str(jdk / "bin/javac"), "--release", "17", "-cp", cp, "-d", str(classes),
             str(TOOL / "java/launcher/ExportClient.java")], output / "compile-launcher.log")
    return os.pathsep.join([str(classes), cp])


def export_client(row, versions, jdk, classpath, output, homes):
    client = versions / row["name"]
    metadata = json.loads((client / (row["name"] + ".json")).read_text())
    minimum = metadata.get("javaVersion", {}).get("majorVersion")
    if minimum is None:
        minimum = 25 if version_key(row["minecraft"]) >= version_key("26") else (
            21 if version_key(row["minecraft"]) >= version_key("1.20.5") else 17)
    runtime = choose_jdk(homes, minimum)
    cache = client / ".live-game-test"
    cache.mkdir(exist_ok=True)
    script = cache / "launch.sh"
    # Use isolated launcher settings, never the user's HMCL accounts or preferences.
    state = output / "launcher-state"
    state.mkdir(exist_ok=True)
    command([str(jdk / "bin/java"), "-Djava.awt.headless=true", "-Duser.home=" + str(state),
             "-cp", classpath, "ExportClient", str(versions.parent), row["name"],
             str(runtime), str(script)], output / "export.log", cwd=state)
    script.chmod(0o600)
    launch, _ = read_launch_script(script)
    isolated_launch(launch, output / "game", 1024)
    row["launch_script"] = str(script)
    row["launcher_agents"] = []


def runtime_fabric_api(row, versions):
    if not row["target"].endswith("-fabric") or row["target"].rsplit("-", 1)[0] == row["minecraft"]:
        return
    matches = []
    for path in (versions / row["name"] / "mods").glob("*.jar"):
        with zipfile.ZipFile(path) as jar:
            if "fabric.mod.json" in jar.namelist() and json.loads(jar.read("fabric.mod.json")).get("id") == "fabric-api":
                matches.append(path)
    if len(matches) != 1:
        raise ValueError("Runtime differs from build target; install exactly one matching Fabric API in " + row["name"])
    row["fabric_api"] = {"path": str(matches[0].resolve()), "sha256": sha256(matches[0])}
