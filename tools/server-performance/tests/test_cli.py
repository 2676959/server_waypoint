import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cli import main
from contracts import atomic_write
from controller import create_root, preparation_transition
from models import CaseSpec, CaseResult, Decision, Status
from selection import select_cases

CONFIG = Path(__file__).resolve().parents[1] / "config"


class CliTest(unittest.TestCase):
    def test_report_cannot_pass_with_missing_expected_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = create_root(Path(temporary).resolve() / "run")
            for state in ("ARTIFACTS_VERIFIED", "FIXTURES_VALIDATED", "PREPARED"):
                preparation_transition(root, state)
            cases = [CaseSpec(name, "paper", "idle", "empty", "medium", "audit", "idle", (), ()) for name in ("one", "two")]
            atomic_write(root / "selection.json", {"schemaVersion": 1, "partial": True, "cases": cases})
            atomic_write(root / "results/one.json", CaseResult(cases[0], Decision(Status.PASS)))
            self.assertEqual(2, main(["report", "--root", str(root)]))

    def test_zero_player_filter_retains_all_four_physical_variants(self):
        cases = select_cases(CONFIG / "features.json", CONFIG / "scenarios.json", (), "zero-player", ("small",))
        self.assertEqual(4, len([c for c in cases if c.feature == "world-container"]))
        self.assertTrue(all(c.clients == 0 for c in cases))
        self.assertFalse(any(c.variant == "navigation-100" for c in cases))

    def test_unknown_selection_filter_rejected(self):
        with self.assertRaises(ValueError):
            select_cases(CONFIG / "features.json", CONFIG / "scenarios.json", (), "all", ("small",), families=("typo",))
