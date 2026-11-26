import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256
from contracts import atomic_write
from controller import create_root, preparation_transition
from models import RuntimeSpec, Status
from preparation import seal_evidence, input_hashes


class MinimalBindingTest(unittest.TestCase):
    def fixture(self, root):
        root = create_root(root)
        for name in ("server.jar", "java", "probe.jar", "bot", "candidate.jar"):
            (root / name).write_text(name)
        runtime = RuntimeSpec("paper-1.21.11", "candidate.jar", "paper", "1.21.11",
            java=str(root / "java"), server=str(root / "server.jar"),
            java_sha256=sha256(root / "java"), server_sha256=sha256(root / "server.jar"),
            probe={"path": str(root / "probe.jar"), "sha256": sha256(root / "probe.jar")},
            bot={"path": str(root / "bot"), "sha256": sha256(root / "bot")})
        atomic_write(root / "runtime-lock.json", {"schemaVersion": 1, "runtimes": [runtime]})
        atomic_write(root / "release.json", {"schemaVersion": 1, "tag": "development", "releaseTarget": "revision",
            "provenance": "candidate", "assets": [{"name": "candidate.jar", "digest": "sha256:" + sha256(root / "candidate.jar"),
                "size": (root / "candidate.jar").stat().st_size, "downloadUrl": ""}]})
        (root / "artifacts").mkdir()
        (root / "artifacts/candidate.jar").write_bytes((root / "candidate.jar").read_bytes())
        for name in ("budgets.json", "environment.json"):
            atomic_write(root / name, {"schemaVersion": 1})
        atomic_write(root / "selection.json", {"schemaVersion": 1, "suite": "minimal-critical"})
        atomic_write(root / "prepared.json", {"schemaVersion": 1, "worlds": {}, "inputs": input_hashes(), "evidence": seal_evidence(root, {})})
        for state in ("ARTIFACTS_VERIFIED", "FIXTURES_VALIDATED", "PREPARED"):
            preparation_transition(root, state)
        return runtime

    def test_binding_keeps_pinned_candidate_and_tool_identities(self):
        from scenarios.minimal import load_binding
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve() / "run"
            runtime = self.fixture(root)
            actual, artifact, release = load_binding(root)
            self.assertEqual(runtime, actual)
            self.assertEqual("candidate", artifact.provenance)
            self.assertEqual("revision", artifact.revision)
            self.assertEqual(root / "artifacts/candidate.jar", release)

    def test_changed_tool_bytes_are_rejected_instead_of_rehashed(self):
        from scenarios.minimal import load_binding
        for name in ("probe.jar", "bot", "java", "server.jar", "artifacts/candidate.jar"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary).resolve() / "run"
                self.fixture(root)
                (root / name).write_text("changed")
                with self.assertRaises(ValueError):
                    load_binding(root)

    def test_published_identity_cannot_be_resealed_as_candidate(self):
        from scenarios.minimal import load_binding
        from contracts import read_document
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve() / "run"
            self.fixture(root)
            release = read_document(root / "release.json")
            release["provenance"] = "published"
            atomic_write(root / "release.json", release)
            settings = read_document(root / "prepared.json")
            settings["evidence"] = seal_evidence(root, {})
            atomic_write(root / "prepared.json", settings)
            with self.assertRaisesRegex(ValueError, "candidate"):
                load_binding(root)


class FunctionalEvidenceTest(unittest.TestCase):
    def test_complete_flag_cannot_replace_native_checks(self):
        from scenarios.minimal import judge_evidence
        result = judge_evidence({"completed": True, "status": "PASS", "checks": []}, ("storage", "reopened"))
        self.assertEqual(Status.BLOCKED, result.status)

    def test_missing_check_does_not_erase_a_product_failure(self):
        from scenarios.minimal import judge_evidence
        result = judge_evidence({"completed": False, "checks": [
            {"name": "storage", "decision": {"status": "FAIL", "reasons": ["state changed after denied edit"]}}
        ]}, ("storage", "reopened"))
        self.assertEqual(Status.FAIL, result.status)
        self.assertTrue(any("reopened" in reason for reason in result.reasons))

    def test_all_native_checks_and_completion_are_required_for_pass(self):
        from scenarios.minimal import judge_evidence
        checks = [{"name": name, "decision": {"status": "PASS", "reasons": []}} for name in ("storage", "reopened")]
        self.assertEqual(Status.PASS, judge_evidence({"completed": True, "checks": checks}, ("storage", "reopened")).status)
        self.assertEqual(Status.BLOCKED, judge_evidence({"completed": False, "checks": checks}, ("storage", "reopened")).status)

    def test_duplicate_or_invalid_checks_cannot_prove_coverage(self):
        from scenarios.minimal import judge_evidence
        for checks in ([{"name": "storage", "decision": {"status": "NOT_APPLICABLE", "reasons": ["no"]}}],
                       [{"name": "storage", "decision": {"status": "PASS"}}] * 2):
            self.assertEqual(Status.BLOCKED, judge_evidence({"completed": True, "checks": checks}, ("storage",)).status)


class FunctionalRoutingTest(unittest.TestCase):
    def test_functional_dispatch_reports_missing_minimal_root_without_historical_fallback(self):
        from scenarios import run_case
        from test_minimal_selection import MinimalSelectionTest
        case = next(c for c in MinimalSelectionTest().select() if c.variant == "minimal-lifecycle")
        result = run_case(case, Path("/nonexistent/minimal-root"))
        self.assertEqual(Status.BLOCKED, result.decision.status)
        self.assertNotIn("adapter pending", " ".join(result.decision.reasons))

    def test_timing_requires_its_own_prepared_candidate_binding(self):
        from scenarios import run_case
        from test_minimal_selection import MinimalSelectionTest
        for case in MinimalSelectionTest().select():
            if case.variant in ("minimal-idle", "minimal-retarget"):
                result = run_case(case, Path("/nonexistent/minimal-root"))
                self.assertEqual(Status.BLOCKED, result.decision.status)
                self.assertNotIn("adapter pending", " ".join(result.decision.reasons))


class FunctionalExecutionTest(unittest.TestCase):
    fixture = MinimalBindingTest.fixture

    def test_failed_native_check_retains_identity_sources_and_shutdown_evidence(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        from contracts import read_document
        from scenarios.minimal import run_case
        from test_minimal_selection import MinimalSelectionTest
        case = next(c for c in MinimalSelectionTest().select() if c.variant == "minimal-lifecycle")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve() / "run"
            self.fixture(root)
            prepared = root / "fixtures/navigation-terrain-small"
            (prepared / "frozen").mkdir(parents=True)
            atomic_write(prepared / "baseline.json", {"schemaVersion": 1, "files": {}, "recipeSha256": "fixture"})
            settings = read_document(root / "prepared.json")
            settings["worlds"] = {"navigation-terrain-small": str(prepared)}
            settings["evidence"] = seal_evidence(root, settings["worlds"])
            atomic_write(root / "prepared.json", settings)

            def native(variant, directory, binding, baseline):
                # Only the external JVM/client operation is substituted.
                directory.mkdir()
                (directory / "server-one.log").write_text("All dimensions are saved")
                return {"completed": True, "checks": [
                    {"name": name, "decision": {"status": "FAIL" if name == "cleanup" else "PASS", "reasons": ["retained counter"] if name == "cleanup" else []}}
                    for name in ("methods", "retarget", "delete", "stop", "cleanup")]}

            with patch.dict(sys.modules, {"bots.minimal_functional": SimpleNamespace(run=native)}):
                result = run_case(case, root)
            self.assertEqual(Status.FAIL, result.decision.status)
            self.assertEqual("candidate", result.artifact["provenance"])
            self.assertEqual("verified saved shutdown", result.cleanup)
            self.assertFalse(result.assertions["cleanup"])
            evidence = Path(result.paths["functional"])
            self.assertTrue(evidence.exists())
            self.assertTrue((evidence.parent / "source/tools/server-performance/scenarios/minimal.py").is_file())


if __name__ == "__main__":
    unittest.main()
