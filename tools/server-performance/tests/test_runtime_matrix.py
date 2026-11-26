import hashlib
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from contracts import load_release, load_runtimes
from models import ArtifactSpec, RuntimeSpec, Status
from runtime_matrix import anchor_decision, probe_class_decision, published_mod_anchors

CONFIG = Path(__file__).resolve().parents[1] / "config"
VERIFIED_REVISION = "675e56fd9d9da084b8f923fdab6feda3ebd19568"
DOCUMENTED_UNVERIFIED_REVISION = "0d38e0dcf47eb258c41b1b09ebc2bcde7253e185"


class RuntimeMatrixTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.output = self.root / "doctor"
        self.java = self._file("java")
        self.server = self._file("server")
        self.probe = self._file("probe")
        self.bot = self._file("bot")

    def test_all_published_mod_artifacts_have_native_anchors(self):
        artifacts = load_release(CONFIG / "releases/4.0.0.json")
        runtimes = load_runtimes(CONFIG / "runtimes.json")
        mod_artifacts = [artifact for artifact in artifacts if self._platform(artifact.name)]
        self.assertEqual({"fabric", "forge", "neoforge"}, {self._platform(artifact.name) for artifact in mod_artifacts})
        self.assertGreaterEqual(len(mod_artifacts), 30)
        decision = published_mod_anchors(artifacts, runtimes, self.output)
        self.assertNotEqual(Status.NOT_APPLICABLE, decision.status)
        self.assertFalse(any(reason.startswith("no native runtime anchor:") for reason in decision.reasons))
        self.assertFalse(any("NOT_APPLICABLE" in reason for reason in decision.reasons))

        names = tuple(artifact.name for artifact in mod_artifacts[:3])
        complete = published_mod_anchors(
            tuple(self._artifact(name) for name in names),
            tuple(self._runtime(self._platform(name), name) for name in names),
            self.output / "complete",
        )
        self.assertEqual(Status.PASS, complete.status)
        self.assertEqual((), complete.reasons)
        missing = published_mod_anchors(
            (self._artifact("server_waypoint-4.0.0-forge-missing.jar"),),
            (),
            self.output / "missing",
        )
        self.assertEqual(Status.BLOCKED, missing.status)
        self.assertIn("no native runtime anchor: server_waypoint-4.0.0-forge-missing.jar", missing.reasons)

    def test_channel_envelope_capability_required(self):
        forge = self._runtime("forge", "server_waypoint-4.0.0-forge-mc1.21.11.jar", channel=False)
        blocked = anchor_decision(forge, self.output / "forge-channel")
        self.assertEqual(Status.BLOCKED, blocked.status)
        self.assertNotEqual(Status.NOT_APPLICABLE, blocked.status)
        self.assertTrue(any("channel-envelope" in reason for reason in blocked.reasons))

        accepted = anchor_decision(
            self._runtime("neoforge", "server_waypoint-4.0.0-neoforge-mc1.21.11.jar", channel=True),
            self.output / "neoforge-channel",
        )
        self.assertEqual(Status.PASS, accepted.status)
        fabric = anchor_decision(
            self._runtime("fabric", "server_waypoint-4.0.0-fabric-mc1.21.11.jar", channel=False),
            self.output / "fabric-channel",
        )
        self.assertEqual(Status.PASS, fabric.status)

    def test_unverified_azalea_version_blocks(self):
        declared = anchor_decision(
            self._runtime("fabric", "server_waypoint-4.0.0-fabric-mc1.21.11.jar", verified=False),
            self.output / "declared",
        )
        self.assertEqual(Status.BLOCKED, declared.status)
        self.assertNotEqual(Status.NOT_APPLICABLE, declared.status)
        self.assertTrue(any("unverified azalea" in reason for reason in declared.reasons))

        documented = anchor_decision(
            self._runtime(
                "fabric",
                "server_waypoint-4.0.0-fabric-mc26.2.jar",
                minecraft="26.2",
                candidate="0d38e0dcf47eb258c41b1b09ebc2bcde7253e185",
                revision=DOCUMENTED_UNVERIFIED_REVISION,
            ),
            self.output / "documented",
        )
        self.assertEqual(Status.BLOCKED, documented.status)
        self.assertTrue(any("unverified azalea" in reason for reason in documented.reasons))

        verified = anchor_decision(
            self._runtime("fabric", "server_waypoint-4.0.0-fabric-mc1.21.11.jar"),
            self.output / "verified",
        )
        self.assertEqual(Status.PASS, verified.status)

    def test_probe_classes_absent_from_production_jars(self):
        production = self._jar("production.jar", ("_959/server_waypoint/fabric/ServerWaypointFabricServer.class",))
        probe = self._jar("probe.jar", ("_959/server_waypoint/perf/mods/ModPerformanceProbe.class",))
        leaked = self._jar(
            "leaked.jar",
            (
                "_959/server_waypoint/fabric/ServerWaypointFabricServer.class",
                "_959/server_waypoint/perf/mods/ModTickProbe.class",
            ),
        )
        self.assertEqual(Status.PASS, probe_class_decision(production, probe).status)
        leaked_decision = probe_class_decision(leaked, probe)
        self.assertEqual(Status.FAIL, leaked_decision.status)
        self.assertTrue(any("probe classes" in reason for reason in leaked_decision.reasons))
        absent = probe_class_decision(production, self.root / "missing-probe.jar")
        self.assertEqual(Status.BLOCKED, absent.status)
        self.assertNotEqual(Status.NOT_APPLICABLE, absent.status)

    def _file(self, name):
        path = self.root / name
        path.write_bytes(name.encode())
        return path

    def _digest(self, path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def _artifact(self, name):
        return ArtifactSpec(name, "a" * 64, 1, "https://example.invalid/artifact.jar", "4.0.0", "d" * 40)

    def _runtime(self, platform, artifact, channel=None, verified=True, minecraft="1.21.11", candidate="0.15.1+mc1.21.11", revision=VERIFIED_REVISION):
        if channel is None:
            channel = platform in {"forge", "neoforge"}
        capabilities = ["fixture", "tick-processing", "zero-player-ticking", "bots"]
        if channel:
            capabilities.append("channel-envelope")
        return RuntimeSpec(
            f"{platform}-test",
            artifact,
            platform,
            minecraft,
            java=str(self.java),
            server=str(self.server),
            java_sha256=self._digest(self.java),
            server_sha256=self._digest(self.server),
            capabilities=tuple(capabilities),
            bot={
                "candidate": candidate,
                "revision": revision,
                "verifiedNativeActions": verified,
                "path": str(self.bot),
                "sha256": self._digest(self.bot),
            },
            probe={"path": str(self.probe), "sha256": self._digest(self.probe)},
            evidence=("unit fixture",),
        )

    def _jar(self, name, entries):
        path = self.root / name
        with zipfile.ZipFile(path, "w") as archive:
            for entry in entries:
                archive.writestr(entry, b"class")
        return path

    @staticmethod
    def _platform(name):
        for platform in ("neoforge", "forge", "fabric"):
            if f"-{platform}-" in name:
                return platform
        return None


if __name__ == "__main__":
    unittest.main()
