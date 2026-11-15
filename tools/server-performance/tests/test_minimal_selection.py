import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cli import main
from contracts import load_runtimes
from selection import select_cases

CONFIG = Path(__file__).resolve().parents[1] / "config"


class MinimalSelectionTest(unittest.TestCase):
    def select(self, **overrides):
        options = dict(features=CONFIG / "features.json", scenarios=CONFIG / "scenarios.json",
                       runtimes=load_runtimes(CONFIG / "runtimes.json"), suite="minimal-critical",
                       tiers=("small", "medium", "stress"), mode="candidate")
        options.update(overrides)
        return select_cases(**options)

    def test_minimal_selection_never_expands_historical_matrix(self):
        cases = self.select()
        self.assertEqual(7, len(cases))
        self.assertEqual({"paper-1.21.11"}, {c.runtime for c in cases})
        self.assertTrue(all(":minimal-critical:" in c.id for c in cases))
        self.assertEqual({"storage", "commands", "navigation", "map-terrain", "protection", "idle"}, {c.feature for c in cases})
        timed = [c for c in cases if c.measurement_seconds]
        self.assertEqual(2, len(timed))
        self.assertEqual(6, sum(c.repeats for c in timed))
        self.assertEqual({(0, 0), (10, 10)}, {(c.clients, c.offered_rate) for c in timed})
        self.assertTrue(all(c.tier == "medium" and c.warmup_seconds == 30 and c.measurement_seconds == 60 for c in timed))
        tree = next(c for c in cases if c.feature == "commands")
        self.assertEqual(3, tree.repeats)
        self.assertEqual((), tree.budgets)
        self.assertTrue(all(c.repeats == 1 for c in cases if not c.measurement_seconds and c.feature != "commands"))

    def test_minimal_selection_rejects_audit_and_wrong_native_target(self):
        for options in ({"mode": "audit"}, {"targets": ("fabric-1.21.11",)}, {"runtimes": ()}):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self.select(**options)

    def test_filters_do_not_silently_replace_minimal_cases(self):
        for options in ({"tiers": ("stress",)}, {"families": ("world-container",)}, {"variants": ("empty",)}):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self.select(**options)
        cases = self.select(families=("navigation",), tiers=("medium",))
        self.assertEqual(1, len(cases))
        self.assertEqual(10, cases[0].offered_rate)

    def test_cli_can_inventory_minimal_scope_without_launching_runtime(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.assertEqual(0, main(["inventory", "--suite", "minimal-critical", "--mode", "candidate", "--output", temporary]))
            document = json.loads((Path(temporary) / "inventory.json").read_text())
            self.assertEqual(7, document["cardinality"])
            self.assertTrue(document["partial"])
            self.assertEqual([], document["artifacts"])


class CandidateIdentityTest(unittest.TestCase):
    def test_candidate_manifest_preserves_source_provenance(self):
        from contracts import atomic_write, load_release
        with tempfile.TemporaryDirectory() as temporary:
            manifest = Path(temporary) / "candidate.json"
            atomic_write(manifest, {"schemaVersion": 1, "tag": "development", "releaseTarget": "abc123",
                "provenance": "candidate", "assets": [{"name": "candidate.jar", "digest": "sha256:" + "a" * 64,
                "size": 10, "downloadUrl": ""}]})
            self.assertEqual("candidate", load_release(manifest)[0].provenance)

    def test_invalid_provenance_is_rejected(self):
        from contracts import atomic_write, load_release
        with tempfile.TemporaryDirectory() as temporary:
            manifest = Path(temporary) / "candidate.json"
            atomic_write(manifest, {"schemaVersion": 1, "tag": "development", "releaseTarget": "abc123",
                "provenance": "typo", "assets": [{"name": "candidate.jar", "digest": "sha256:" + "a" * 64,
                "size": 10, "downloadUrl": ""}]})
            with self.assertRaises(ValueError):
                load_release(manifest)

    def test_unimplemented_minimal_case_cannot_run_historical_adapter(self):
        from scenarios import run_case
        cases = MinimalSelectionTest().select()
        for case in cases:
            with self.subTest(case=case.id):
                result = run_case(case, Path("/nonexistent/minimal-root"))
                self.assertEqual("BLOCKED", result.decision.status.value)
                self.assertIn("minimal", " ".join(result.decision.reasons))


class MinimalPreparationTest(unittest.TestCase):
    def test_prepare_rejects_published_bytes_before_creating_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "run"
            with self.assertRaisesRegex(ValueError, "candidate"):
                main(["prepare", "--suite", "minimal-critical", "--mode", "candidate", "--output", str(root)])
            self.assertFalse(root.exists())

    def test_minimal_preflight_requires_native_bots_but_not_custom_wire_protocol(self):
        from preflight import required_capabilities
        capabilities = required_capabilities("minimal-critical")
        self.assertIn("bots", capabilities)
        self.assertIn("tick-processing", capabilities)
        self.assertNotIn("wire-protocol", capabilities)


    def test_run_keeps_minimal_candidate_mode_and_per_case_repetitions(self):
        from contracts import atomic_write
        from controller import create_root, preparation_transition
        from preparation import input_hashes, seal_evidence
        cases = MinimalSelectionTest().select()
        with tempfile.TemporaryDirectory() as temporary:
            root = create_root(Path(temporary).resolve() / "run")
            for name in ("runtime-lock.json", "release.json", "budgets.json", "environment.json"):
                atomic_write(root / name, {"schemaVersion": 1})
            atomic_write(root / "selection.json", {"schemaVersion": 1, "suite": "minimal-critical", "partial": True, "cases": cases})
            atomic_write(root / "prepared.json", {"schemaVersion": 1, "inputs": input_hashes(), "evidence": seal_evidence(root, {})})
            for state in ("ARTIFACTS_VERIFIED", "FIXTURES_VALIDATED", "PREPARED"):
                preparation_transition(root, state)
            self.assertEqual(2, main(["run", "--root", str(root)]))
            results = [json.loads(p.read_text()) for p in (root / "results").glob("*.json")]
            self.assertEqual(7, len(results))
            self.assertTrue(all(r["case"]["mode"] == "candidate" for r in results))
            self.assertEqual({1, 3}, {r["case"]["repeats"] for r in results})
            self.assertTrue(all(r["decision"]["status"] == "BLOCKED" for r in results))


    def test_run_cannot_relabel_a_historical_root_as_minimal(self):
        from contracts import atomic_write
        from controller import create_root, preparation_transition
        from preparation import input_hashes, seal_evidence
        from dataclasses import replace
        case = replace(MinimalSelectionTest().select()[0], id="historical", variant="empty", feature="idle", clients=0, mode="audit")
        with tempfile.TemporaryDirectory() as temporary:
            root = create_root(Path(temporary).resolve() / "run")
            for name in ("runtime-lock.json", "release.json", "budgets.json", "environment.json"):
                atomic_write(root / name, {"schemaVersion": 1})
            atomic_write(root / "selection.json", {"schemaVersion": 1, "suite": "all", "partial": True, "cases": [case]})
            atomic_write(root / "prepared.json", {"schemaVersion": 1, "inputs": input_hashes(), "evidence": seal_evidence(root, {})})
            for state in ("ARTIFACTS_VERIFIED", "FIXTURES_VALIDATED", "PREPARED"):
                preparation_transition(root, state)
            with self.assertRaisesRegex(ValueError, "prepared"):
                main(["run", "--root", str(root), "--suite", "minimal-critical"])
            with self.assertRaisesRegex(ValueError, "mode"):
                main(["run", "--root", str(root), "--mode", "candidate"])
            self.assertFalse(list((root / "results").glob("*.json")))

    def test_minimal_doctor_only_checks_paper_anchor(self):
        from unittest.mock import patch
        from models import Decision, Status
        with tempfile.TemporaryDirectory() as temporary, patch("preflight.check_runtime", return_value=Decision(Status.PASS)) as check:
            self.assertEqual(0, main(["doctor", "--suite", "minimal-critical", "--output", temporary]))
            self.assertEqual(["paper-1.21.11"], [call.args[0].id for call in check.call_args_list])
