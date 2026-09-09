#!/usr/bin/env python3
"""Negative release-gate tests using synthetic ZIPs, never runtime/release evidence."""
import re
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r"^mod_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M)[1]
REQUIRED = ["_959/server_waypoint/internal/noisekk/protocol/HandshakeState.class",
            "META-INF/LICENSE-noise-java"]


class ReleaseArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="waypoint-release-gate-")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.names = [f"server_waypoint-{VERSION}-velocity.jar"]
        for branch in ("mods", "paper"):
            for properties in (ROOT / branch / "versions").glob("*/gradle.properties"):
                target = properties.parent.name
                if target in ("1.21.3-fabric", "1.21.3-neoforge"):
                    continue
                version_range = re.search(r"^mcVersionRange\s*=\s*(.+)$", properties.read_text(), re.M)[1]
                self.names.append(f"server_waypoint-{VERSION}-{target.rsplit('-', 1)[1]}-mc{version_range}.jar")
        for name in self.names:
            self.jar(name, REQUIRED)

    def jar(self, name, entries):
        with zipfile.ZipFile(self.directory / name, "w") as output:
            for entry in entries:
                output.writestr(entry, b"fixture")

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
        self.verify(True, "Verified 39")

    def test_missing_velocity(self):
        (self.directory / self.names[0]).unlink()
        self.verify(False, "Expected 39")

    def test_wrong_target_cannot_fill_missing_version(self):
        name = next(name for name in self.names if "fabric-mc" in name)
        (self.directory / name).rename(self.directory / f"server_waypoint-{VERSION}-fabric-mc99.jar")
        self.verify(False, "supported target")

    def test_unshaded_velocity_is_not_a_release(self):
        (self.directory / self.names[0]).rename(self.directory / f"server_waypoint-{VERSION}-velocity-unshaded.jar")
        self.verify(False, "supported target")

    def test_missing_noise_notice(self):
        self.jar(self.names[0], REQUIRED[:1])
        self.verify(False, "Missing META-INF/LICENSE-noise-java")

    def test_unrelocated_crypto(self):
        self.jar(self.names[0], REQUIRED + ["com/southernstorm/noise/protocol/HandshakeState.class"])
        self.verify(False, "Unrelocated Noise")

    def test_development_probes(self):
        for probe in ("CrossServerGuiProbe.class", "TeleportAudit.class", "org/junit/Test.class"):
            with self.subTest(probe=probe):
                self.jar(self.names[0], REQUIRED + [probe])
                self.verify(False, "Development or test content")


if __name__ == "__main__":
    unittest.main()
