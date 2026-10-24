import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scenarios.inventory import validate_physical_pair, validate_zero_players, run_independent, synthetic_count
from models import Decision, Status


class InventoryScenariosTest(unittest.TestCase):
    def sample(self, physical=100, players=0):
        return {"onlinePlayers": players, "physicalTransfersObserved": physical, "forceLoadedHoppers": 64, "expectedHoppers": 64, "topLevelItemCount": 16, "tickCount": 1200, "paused": False, "elapsedSeconds": 60}

    def test_zero_clients_required(self):
        self.assertEqual(Status.FAIL, validate_zero_players([self.sample(players=1)]).status)
        self.assertEqual(Status.PASS, validate_zero_players([self.sample()]).status)

    def test_inactive_control_world_invalid(self):
        self.assertEqual(Status.INCONCLUSIVE, validate_physical_pair(self.sample(0), self.sample(0), 16).status)

    def test_slow_candidate_throughput_is_failure(self):
        candidate = self.sample(20)
        candidate["tickCount"] = 300
        decision = validate_physical_pair(self.sample(100), candidate, 16)
        self.assertEqual(Status.FAIL, decision.status)

    def test_synthetic_count_not_physical_count(self):
        self.assertEqual(120000, synthetic_count(100, 1200))
        sample = self.sample(0)
        sample["syntheticEvents"] = 120000
        self.assertEqual(Status.INCONCLUSIVE, validate_physical_pair(sample, sample, 16).status)

    def test_known_bad_result_kept_while_next_case_runs(self):
        seen = []
        def run(case):
            seen.append(case)
            return Decision(Status.FAIL if case == "known-bad" else Status.PASS)
        results = run_independent(("known-bad", "idle"), run)
        self.assertEqual(["known-bad", "idle"], seen)
        self.assertEqual([Status.FAIL, Status.PASS], [r.status for r in results])

    def test_conservation_and_force_loading_fail_independently(self):
        candidate = self.sample()
        candidate["topLevelItemCount"] = 15
        self.assertEqual(Status.FAIL, validate_physical_pair(self.sample(), candidate, 16).status)
        candidate = self.sample()
        candidate["forceLoadedHoppers"] = 0
        self.assertEqual(Status.INCONCLUSIVE, validate_physical_pair(self.sample(), candidate, 16).status)

    def test_later_setup_error_retains_already_measured_failure(self):
        import tempfile
        from contracts import atomic_write
        from models import CaseSpec, CaseResult
        from scenarios.inventory import retain_partial
        case = CaseSpec("case", "paper", "inventory-regression", "shulker-books-100", "medium", "audit", "idle", (), ())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "partial.json"
            atomic_write(path, CaseResult(case, Decision(Status.FAIL, ("allocation exceeded",))))
            result = retain_partial(path, case, RuntimeError("later profiler failed"))
            self.assertEqual(Status.FAIL, result.decision.status)
            self.assertIn("allocation exceeded", result.decision.reasons)
            self.assertIn("later profiler failed", result.decision.reasons)

    def test_native_measurement_crash_is_failure(self):
        from controller import NativeFailure
        from scenarios.inventory import retain_partial
        from models import CaseSpec
        import tempfile
        case = CaseSpec("case", "paper", "idle", "empty", "medium", "audit", "idle", (), ())
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(Status.FAIL, retain_partial(Path(directory) / "absent.json", case, NativeFailure("server crashed during measurement")).decision.status)

    def test_nonfinite_physical_counter_cannot_pass(self):
        candidate = self.sample()
        candidate["physicalTransfersObserved"] = float("nan")
        self.assertEqual(Status.INCONCLUSIVE, validate_physical_pair(self.sample(), candidate, 16).status)

    def test_activity_must_continue_through_the_window(self):
        from scenarios.inventory import validate_activity
        samples = [{"physicalTransfersObserved": index if index < 50 else 50, "topLevelItemCount": 16, "forceLoadedHoppers": 64, "expectedHoppers": 64, "paused": False, "pauseWhenEmptySeconds": 0} for index in range(120)]
        self.assertEqual(Status.INCONCLUSIVE, validate_activity(samples, True, 16).status)
        self.assertEqual(Status.FAIL, validate_activity(samples, False, 16).status)
