#!/usr/bin/env python3
"""Negative release-gate tests using synthetic ZIPs, never runtime/release evidence."""
import re
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r"^mod_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M)[1]

sys.path.insert(0, str(ROOT / "tools"))
import check_release_jar  # noqa: E402


def minimal_class(name, references=(), descriptors=()):
    """A Java 17 class file with no members: its constant pool names the class, its references and descriptors."""
    pool = []

    def utf8(text):
        encoded = text.encode()
        pool.append(struct.pack(">BH", 1, len(encoded)) + encoded)
        return len(pool)

    def class_entry(class_name):
        pool.append(struct.pack(">BH", 7, utf8(class_name)))
        return len(pool)

    this_class = class_entry(name)
    super_class = class_entry("java/lang/Object")
    for reference in references:
        class_entry(reference)
    for descriptor in descriptors:
        utf8(descriptor)
    return (struct.pack(">IHHH", 0xCAFEBABE, 0, 61, len(pool) + 1) + b"".join(pool)
            + struct.pack(">7H", 0x21, this_class, super_class, 0, 0, 0, 0))


def write_jar(path, entries):
    with zipfile.ZipFile(path, "w") as output:
        for entry, data in entries.items():
            output.writestr(entry, data)


NOISE = {"_959/server_waypoint/internal/noisekk/protocol/HandshakeState.class":
         minimal_class("_959/server_waypoint/internal/noisekk/protocol/HandshakeState"),
         "META-INF/LICENSE-noise-java": b"fixture"}
CHAT_SPRITES = "assets/server_waypoint/chat-sprites.json"


class ReleaseArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="waypoint-release-gate-")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.names = [f"server_waypoint-{VERSION}-velocity.jar"]
        self.contents = {self.names[0]: {**NOISE, "velocity-plugin.json": b"{}"}}
        for branch in ("mods", "paper"):
            for properties in (ROOT / branch / "versions").glob("*/gradle.properties"):
                target = properties.parent.name
                if target in ("1.21.3-fabric", "1.21.3-neoforge"):
                    continue
                version_range = re.search(r"^mcVersionRange\s*=\s*(.+)$", properties.read_text(), re.M)[1]
                name = f"server_waypoint-{VERSION}-{target.rsplit('-', 1)[1]}-mc{version_range}.jar"
                self.names.append(name)
                self.contents[name] = {**NOISE, "lang/en_us.json": b"{}", "SERVER_WAYPOINT_CREDITS.txt": b"credits"}
                if tuple(map(int, target.rsplit("-", 1)[0].split("."))) >= (1, 21, 9):
                    self.contents[name][CHAT_SPRITES] = b"{}"
        for name in self.names:
            self.jar(name, self.contents[name])

    def jar(self, name, entries):
        write_jar(self.directory / name, entries)

    def velocity_with(self, entry, data):
        self.jar(self.names[0], {**self.contents[self.names[0]], entry: data})

    def verify(self, success, reason=""):
        result = subprocess.run(["bash", str(ROOT / "tools/verify-release-artifacts.sh"),
                                 str(self.directory)], capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        self.assertIn(reason, result.stdout + result.stderr)

    def test_collector_ignores_intermediates_and_preserves_build_outputs(self):
        import shutil
        root = self.directory / "collector"
        root.mkdir()
        shutil.copy2(ROOT / "move_builds.sh", root / "move_builds.sh")
        (root / "gradle.properties").write_text(f"mod_version={VERSION}\n")
        expected = []
        for branch, target in (("mods", "1.20.1-forge"), ("paper", "1.21-paper")):
            version = target.rsplit("-", 1)[0]
            folder = root / branch / "versions" / target
            (folder / "build/libs").mkdir(parents=True)
            (folder / "gradle.properties").write_text(f"mcVersionRange={version}\n")
            name = f"server_waypoint-{VERSION}-{target.rsplit('-', 1)[1]}-mc{version}.jar"
            (folder / "build/libs" / name).write_bytes(b"release")
            (folder / "build/libs" / name.replace(".jar", "-jarjar-input.jar")).write_bytes(b"intermediate")
            expected.append(name)
        velocity = root / "velocity/build/libs"
        velocity.mkdir(parents=True)
        name = f"server_waypoint-{VERSION}-velocity.jar"
        (velocity / name).write_bytes(b"release")
        (velocity / name.replace(".jar", "-unshaded.jar")).write_bytes(b"intermediate")
        expected.append(name)
        result = subprocess.run(["bash", str(root / "move_builds.sh")], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(sorted(expected), sorted(p.name for p in (root / "builds").iterdir()))
        self.assertTrue((velocity / name).exists())

    def test_complete_set(self):
        self.verify(True, "Verified 43")

    def test_missing_velocity(self):
        (self.directory / self.names[0]).unlink()
        self.verify(False, "Expected 43")

    def test_wrong_target_cannot_fill_missing_version(self):
        name = next(name for name in self.names if "fabric-mc" in name)
        (self.directory / name).rename(self.directory / f"server_waypoint-{VERSION}-fabric-mc99.jar")
        self.verify(False, "supported target")

    def test_unshaded_velocity_is_not_a_release(self):
        (self.directory / self.names[0]).rename(self.directory / f"server_waypoint-{VERSION}-velocity-unshaded.jar")
        self.verify(False, "supported target")

    def test_missing_noise_notice(self):
        self.jar(self.names[0], {entry: data for entry, data in self.contents[self.names[0]].items()
                                 if entry != "META-INF/LICENSE-noise-java"})
        self.verify(False, "Missing META-INF/LICENSE-noise-java")

    def test_unrelocated_crypto(self):
        self.velocity_with("com/southernstorm/noise/protocol/HandshakeState.class",
                           minimal_class("com/southernstorm/noise/protocol/HandshakeState"))
        self.verify(False, "Unrelocated Noise")

    def test_development_probes(self):
        for probe in ("CrossServerGuiProbe", "TeleportAudit", "org/junit/Test"):
            with self.subTest(probe=probe):
                self.velocity_with(f"{probe}.class", minimal_class(probe))
                self.verify(False, "Development or test content")

    def test_content_rule_fails_gate(self):
        self.velocity_with("lang/en_us.json", b"{}")
        self.verify(False, "forbidden-entry: lang/en_us.json")


BACKEND = {"lang/en_us.json": b"{}", "SERVER_WAYPOINT_CREDITS.txt": b"credits", CHAT_SPRITES: b"{}"}
VELOCITY = {"velocity-plugin.json": b"{}"}
A_REFERENCING_B = {"_959/server_waypoint/A.class": minimal_class("_959/server_waypoint/A", ["_959/server_waypoint/B"]),
                   "_959/server_waypoint/B.class": minimal_class("_959/server_waypoint/B")}
TRANSPORT = "_959/server_waypoint/crossserver/transport/"
MULTI_RELEASE = {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n"}


class CheckReleaseJarTest(unittest.TestCase):
    def violations(self, entries, loader, version):
        with tempfile.TemporaryDirectory(prefix="waypoint-release-jar-") as directory:
            path = Path(directory) / "fixture.jar"
            write_jar(path, entries)
            return check_release_jar.check_jar(path, loader, version)

    def test_clean_jars_pass(self):
        self.assertEqual([], self.violations({**BACKEND, **A_REFERENCING_B}, "fabric", "1.21.11"))
        self.assertEqual([], self.violations({**VELOCITY, **A_REFERENCING_B}, "velocity", "-"))

    def test_missing_internal_class(self):
        entries = {**BACKEND, "_959/server_waypoint/A.class": minimal_class(
            "_959/server_waypoint/A", [TRANSPORT + "TcpChannel"])}
        result = self.violations(entries, "fabric", "1.21.11")
        self.assertEqual(1, len(result), result)
        self.assertTrue(result[0].startswith("missing-class: " + TRANSPORT + "TcpChannel"), result)

    def test_descriptor_and_annotation_references(self):
        entries = {**VELOCITY, "_959/server_waypoint/A.class": minimal_class("_959/server_waypoint/A", descriptors=[
            "(L_959/server_waypoint/core/waypoint/WaypointPos;)V",
            "L_959/server_waypoint/config/NavigationMethodSetJsonAdapter;"])}
        result = self.violations(entries, "velocity", "-")
        self.assertEqual(["missing-class: _959/server_waypoint/config/NavigationMethodSetJsonAdapter",
                          "missing-class: _959/server_waypoint/core/waypoint/WaypointPos"],
                         sorted(violation.split(" (")[0] for violation in result))

    def test_multi_release_entry_provides_class(self):
        entries = {**VELOCITY, **MULTI_RELEASE,
                   "_959/server_waypoint/A.class": A_REFERENCING_B["_959/server_waypoint/A.class"],
                   "META-INF/versions/17/_959/server_waypoint/B.class": minimal_class("_959/server_waypoint/B")}
        self.assertEqual([], self.violations(entries, "velocity", "-"))

    def test_versioned_classes_need_a_multi_release_manifest(self):
        # Without `Multi-Release: true` the JVM never looks under META-INF/versions/.
        entries = {**VELOCITY,
                   "_959/server_waypoint/A.class": A_REFERENCING_B["_959/server_waypoint/A.class"],
                   "META-INF/versions/17/_959/server_waypoint/B.class": minimal_class("_959/server_waypoint/B")}
        self.assertEqual(["missing-class: _959/server_waypoint/B"],
                         [violation.split(" (")[0] for violation in self.violations(entries, "velocity", "-")])

    def test_versioned_classes_newer_than_the_jar_do_not_provide(self):
        # A is a Java 17 class, so the jar runs on Java 17, which does not see META-INF/versions/21/.
        entries = {**VELOCITY, **MULTI_RELEASE,
                   "_959/server_waypoint/A.class": A_REFERENCING_B["_959/server_waypoint/A.class"],
                   "META-INF/versions/21/_959/server_waypoint/B.class": minimal_class("_959/server_waypoint/B")}
        self.assertEqual(["missing-class: _959/server_waypoint/B"],
                         [violation.split(" (")[0] for violation in self.violations(entries, "velocity", "-")])

    def test_versioned_entries_follow_the_content_rules(self):
        velocity_entry = "META-INF/versions/17/_959/server_waypoint/config/Config.class"
        self.assertIn(f"forbidden-entry: {velocity_entry}", self.violations(
            {**VELOCITY, **MULTI_RELEASE, velocity_entry: minimal_class("_959/server_waypoint/config/Config")},
            "velocity", "-"))
        backend_entry = "META-INF/versions/17/" + TRANSPORT + "TcpCoordinator.class"
        self.assertIn(f"forbidden-entry: {backend_entry}", self.violations(
            {**BACKEND, **MULTI_RELEASE, backend_entry: minimal_class(TRANSPORT + "TcpCoordinator")},
            "fabric", "1.21.11"))

    def test_unreadable_class(self):
        result = self.violations({**VELOCITY, "_959/server_waypoint/Broken.class": b"fixture"}, "velocity", "-")
        self.assertEqual(1, len(result), result)
        self.assertTrue(result[0].startswith("unreadable-class: _959/server_waypoint/Broken.class"), result)

    def test_malformed_class_files_raise(self):
        valid = A_REFERENCING_B["_959/server_waypoint/A.class"]
        references = {"_959/server_waypoint/A", "_959/server_waypoint/B"}
        self.assertEqual(references, check_release_jar.class_references(valid))
        # The last 14 bytes are the class header and the empty interface, field, method and attribute tables.
        header = valid[:-14]

        def with_members(field_attribute_length=2):
            return (header
                    + struct.pack(">5H", 0x21, 2, 4, 1, 4)  # flags, this, super, one interface
                    + struct.pack(">5H", 1, 0, 1, 1, 1)  # one field with one attribute holding two bytes
                    + struct.pack(">HI", 1, field_attribute_length) + b"\0\1"
                    + struct.pack(">5H", 1, 0, 1, 1, 0)  # one method without attributes
                    + struct.pack(">HHI", 1, 1, 4) + b"\0\0\0\0")  # one class attribute

        self.assertEqual(references, check_release_jar.class_references(with_members()))
        # Byte 10 is the first constant's tag; 2 is unassigned. The first Utf8 entry ends at byte 35.
        for label, data in (("bad magic", b"\0" + valid[1:]), ("unknown tag", valid[:10] + b"\2" + valid[11:]),
                            ("truncated pool", valid[:20]), ("truncated after the pool", header),
                            ("attribute overruns the file", valid[:-2] + struct.pack(">HHI", 1, 1, 100)),
                            ("field attribute overruns the file", with_members(field_attribute_length=1000)),
                            ("class attribute table missing", with_members()[:-12]),
                            ("trailing bytes", valid + b"\0")):
            with self.subTest(label), self.assertRaises(ValueError):
                check_release_jar.class_references(data)

    def test_velocity_rejects_backend_content(self):
        entries = {name: b"fixture" for name in ("lang/en_us.json", CHAT_SPRITES, "SERVER_WAYPOINT_CREDITS.txt",
                                                  "META-INF/maven/org.signal.forks/noise-java/pom.xml")}
        for name in ("command/CoreWaypointCommand", "config/Config", "navigation/NavigationService", "text/chat/Chat",
                     "translation/AdventureTranslator", "core/WaypointServerCore"):
            entries[f"_959/server_waypoint/{name}.class"] = minimal_class(f"_959/server_waypoint/{name}")
        for entry, data in entries.items():
            with self.subTest(entry=entry):
                self.assertIn(f"forbidden-entry: {entry}", self.violations({**VELOCITY, entry: data}, "velocity", "-"))

    def test_velocity_requires_plugin_descriptor(self):
        self.assertIn("missing-entry: velocity-plugin.json", self.violations({}, "velocity", "-"))

    def test_backends_reject_proxy_classes(self):
        for loader in ("fabric", "forge", "neoforge", "paper"):
            for name in ("_959/server_waypoint/proxy/ProxyPlayerRouter", TRANSPORT + "TcpCoordinator",
                         TRANSPORT + "TcpCoordinator$1", TRANSPORT + "CoordinatorTransport"):
                with self.subTest(loader=loader, name=name):
                    self.assertIn(f"forbidden-entry: {name}.class",
                                  self.violations({**BACKEND, f"{name}.class": minimal_class(name)}, loader, "1.21.11"))
            with self.subTest(loader=loader, name="control"):
                control = TRANSPORT + "TcpChannel"
                self.assertEqual([], self.violations({**BACKEND, f"{control}.class": minimal_class(control)},
                                                     loader, "1.21.11"))

    def test_backends_require_translations_and_credits(self):
        for removed in ("lang/en_us.json", "SERVER_WAYPOINT_CREDITS.txt"):
            with self.subTest(removed=removed):
                entries = {entry: data for entry, data in BACKEND.items() if entry != removed}
                self.assertIn(f"missing-entry: {removed}", self.violations(entries, "paper", "1.21.11"))

    def test_chat_sprites_follow_version(self):
        sprites_class = "_959/server_waypoint/text/chat/VanillaChatSprites"
        without_table = {entry: data for entry, data in BACKEND.items() if entry != CHAT_SPRITES}
        self.assertIn(f"forbidden-entry: {CHAT_SPRITES}", self.violations(BACKEND, "paper", "1.21"))
        self.assertIn(f"forbidden-entry: {sprites_class}.class", self.violations(
            {**without_table, f"{sprites_class}.class": minimal_class(sprites_class)}, "fabric", "1.20.1"))
        self.assertIn(f"missing-entry: {CHAT_SPRITES}", self.violations(without_table, "neoforge", "1.21.9"))
        self.assertIn(f"missing-entry: {CHAT_SPRITES}", self.violations(without_table, "forge", "26.1.2"))
        self.assertEqual([], self.violations(without_table, "fabric", "1.21.6"))

    def test_maven_metadata_rejected(self):
        self.assertIn("forbidden-entry: META-INF/maven/x/pom.xml",
                      self.violations({**BACKEND, "META-INF/maven/x/pom.xml": b"<project/>"}, "fabric", "1.21.11"))

    def test_command_line_contract(self):
        with tempfile.TemporaryDirectory(prefix="waypoint-release-jar-") as directory:
            clean = Path(directory) / "clean.jar"
            dirty = Path(directory) / "dirty.jar"
            write_jar(clean, VELOCITY)
            write_jar(dirty, {**VELOCITY, "lang/en_us.json": b"{}"})

            def run(*arguments):
                result = subprocess.run([sys.executable, str(ROOT / "tools/check_release_jar.py"), *arguments],
                                        capture_output=True, text=True)
                return result.returncode, result.stdout, result.stderr

            self.assertEqual((0, "", ""), run(str(clean), "velocity", "-"))
            self.assertEqual((1, "", "dirty.jar: forbidden-entry: lang/en_us.json\n"), run(str(dirty), "velocity", "-"))
            self.assertEqual(2, run(str(clean), "velocity")[0])
            self.assertEqual(2, run(str(clean), "bukkit", "1.21.11")[0])
            self.assertEqual(2, run(str(Path(directory) / "absent.jar"), "velocity", "-")[0])


if __name__ == "__main__":
    unittest.main()
