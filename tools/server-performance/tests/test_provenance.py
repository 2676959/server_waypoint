import dataclasses
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import stage_artifacts, validate_matrix
from preflight import check_runtime, required_capabilities
from contracts import load_release, load_runtimes
from models import ArtifactSpec, RuntimeSpec, Status

CONFIG = Path(__file__).resolve().parents[1] / "config"


class ProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.cache = self.root / "cache"
        self.cache.mkdir()
        self.asset = ArtifactSpec("release.jar", hashlib.sha256(b"release").hexdigest(), 7, "", "4.0.0", "a" * 40)

    def test_filename_cannot_override_digest(self):
        (self.cache / "release.jar").write_bytes(b"rebuilt")
        with self.assertRaises(ValueError):
            stage_artifacts((self.asset,), self.cache, self.root / "output")

    def test_cached_bytes_are_rehashed(self):
        path = self.cache / "release.jar"
        path.write_bytes(b"release")
        staged = stage_artifacts((self.asset,), self.cache, self.root / "one")
        self.assertEqual(b"release", staged["release.jar"].read_bytes())
        path.write_bytes(b"releasE")
        with self.assertRaises(ValueError):
            stage_artifacts((self.asset,), self.cache, self.root / "two")

    def test_partial_matrix_blocks(self):
        self.assertEqual(Status.BLOCKED, validate_matrix((self.asset,), ()).status)

    def test_unknown_bot_mapping_blocks(self):
        runtime = RuntimeSpec("paper", "release.jar", "paper", "1.21.11")
        self.assertEqual(Status.BLOCKED, check_runtime(runtime, self.root / "doctor", frozenset({"bots"})).status)

    def test_changed_bot_and_probe_bytes_block_readiness(self):
        java = self.root / "java"
        java.write_text("#!/bin/sh\necho openjdk >&2\nexit 0\n")
        java.chmod(0o755)
        server = self.root / "server.jar"
        bot = self.root / "bot"
        probe = self.root / "probe.jar"
        server.write_bytes(b"server")
        bot.write_bytes(b"locked-bot")
        probe.write_bytes(b"locked-probe")
        runtime = RuntimeSpec(
            "paper-1.21.11", "release.jar", "paper", "1.21.11",
            java=str(java), server=str(server),
            java_sha256=hashlib.sha256(java.read_bytes()).hexdigest(),
            server_sha256=hashlib.sha256(b"server").hexdigest(),
            capabilities=("fixture", "tick-processing", "profile", "bots"),
            bot={"verifiedNativeActions": True, "path": str(bot), "sha256": hashlib.sha256(b"locked-bot").hexdigest()},
            probe={"path": str(probe), "sha256": hashlib.sha256(b"locked-probe").hexdigest()},
        )
        bot.write_bytes(b"rebuilt-bot")
        probe.write_bytes(b"rebuilt-probe")
        decision = check_runtime(runtime, self.root / "doctor", frozenset({"bots", "fixture", "tick-processing", "profile"}))
        self.assertEqual(Status.BLOCKED, decision.status)
        self.assertTrue(any(reason.startswith("bots:") for reason in decision.reasons))
        self.assertTrue(any(reason.startswith("probe:") for reason in decision.reasons))
        record = json.loads((self.root / "doctor" / "paper-1.21.11.json").read_text())
        self.assertEqual("BLOCKED", record["decision"]["status"])
        self.assertNotIn("samples", record)
        self.assertNotIn("measurements", record)

    def test_zero_player_selection_does_not_require_bots(self):
        self.assertNotIn("bots", required_capabilities("zero-player"))
        self.assertIn("bots", required_capabilities("all"))

    def test_release_and_head_provenance_differ(self):
        release = load_release(CONFIG / "releases/4.0.0.json")
        self.assertEqual(44, len(release))
        self.assertEqual({"d9f532f8b5a14c9be4bc47531b05b44fd1dc5773"}, {a.revision for a in release})
        rebuilt = dataclasses.replace(release[0], provenance="development", revision="b" * 40)
        self.assertNotEqual(release[0], rebuilt)
        self.assertEqual(Status.PASS, validate_matrix(release, load_runtimes(CONFIG / "runtimes.json")).status)

    def test_symlink_cache_refused(self):
        original = self.root / "original.jar"
        original.write_bytes(b"release")
        (self.cache / "release.jar").symlink_to(original)
        with self.assertRaises(ValueError):
            stage_artifacts((self.asset,), self.cache, self.root / "output")
