import dataclasses
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from metrics import summarize
from budgets import evaluate
from reporting import report_axes, combine_decisions, write_report
from models import CaseSpec, CaseResult, Decision, Status, RunRecord
from profiles import start_jfr

CONFIG = Path(__file__).resolve().parents[1] / "config/budgets.json"


class MeasurementsTest(unittest.TestCase):
    def result(self, feature="idle", repetitions=None):
        case = CaseSpec("case", "paper", feature, "shulker-books-100" if feature == "inventory-regression" else "empty", "medium", "audit", "idle", ("tick-processing",), ("backend-medium", "inventory-100") if feature == "inventory-regression" else ("backend-medium",), minimum_samples=1)
        rep = {"tickNs": [1_000_000], "workloadNs": [5_000_000], "allocationBytes": [2_097_152], "controlWorkloadNs": [0], "controlAllocationBytes": [0], "allocationScope": "server-thread", "tickMetric": "native-processing-duration", "warmupSeconds": 30, "measurementSeconds": 60}
        return CaseResult(case, Decision(Status.INCONCLUSIVE), observations={"repetitions": repetitions or [rep.copy() for _ in range(3)]})

    def test_p99_not_callback_spacing(self):
        result = self.result()
        for rep in result.observations["repetitions"]:
            rep["callbackSpacingNs"] = [100_000_000]
        self.assertEqual(Status.PASS, evaluate(result, CONFIG).status)
        result.observations["repetitions"][0]["tickNs"] = [40_000_000]
        self.assertEqual(Status.FAIL, evaluate(result, CONFIG).status)

    def test_bad_repeat_not_averaged_away(self):
        result = self.result()
        result.observations["repetitions"][2]["tickNs"] = [50_000_000]
        self.assertEqual(Status.FAIL, evaluate(result, CONFIG).status)

    def test_thread_allocations_not_process_allocations(self):
        result = self.result("inventory-regression")
        for rep in result.observations["repetitions"]:
            rep["allocationScope"] = "whole-process"
        self.assertEqual(Status.INCONCLUSIVE, evaluate(result, CONFIG).status)

    def test_profile_failure_retains_measured_failure(self):
        decision = combine_decisions((Decision(Status.FAIL, ("tick overrun",)), Decision(Status.BLOCKED, ("profiler missing",))))
        self.assertEqual(Status.FAIL, decision.status)
        self.assertIn("profiler missing", decision.reasons)

    def test_empty_or_wrong_unit_samples_inconclusive(self):
        with self.assertRaises(ValueError):
            summarize([], "ns")
        with self.assertRaises(ValueError):
            summarize([1], "milliseconds")
        result = self.result()
        result.observations["repetitions"][0]["tickMetric"] = "callback-spacing"
        self.assertEqual(Status.INCONCLUSIVE, evaluate(result, CONFIG).status)

    def test_complete_failing_audit_is_not_incomplete(self):
        axes = report_axes(("one",), {"one": Decision(Status.FAIL, ("known defect",))})
        self.assertTrue(axes["complete"])
        self.assertEqual("FAIL", axes["decision"].status.value)

    def test_inventory_inclusive_boundary(self):
        result = self.result("inventory-regression")
        self.assertEqual(Status.PASS, evaluate(result, CONFIG).status)
        result.observations["repetitions"][0]["workloadNs"] = [5_000_001]
        self.assertEqual(Status.FAIL, evaluate(result, CONFIG).status)

    def test_nearest_rank_and_nonfinite(self):
        self.assertEqual(95, summarize(list(range(1, 101)), "ns")["p95"])
        self.assertEqual(99, summarize(list(range(1, 101)), "ns")["p99"])
        with self.assertRaises(ValueError):
            summarize([float("nan")], "ns")

    def test_missing_profiler_is_blocked(self):
        self.assertEqual(Status.BLOCKED, start_jfr(None, "/does-not-exist/java", Path("/not-created/test.jfr")).status)

    def test_relative_pair_requires_matching_approved_baseline(self):
        from budgets import compare_relative
        self.assertEqual(Status.BLOCKED, compare_relative([2], [1], "ns", {}, {}, False, 0).status)
        self.assertEqual(Status.INCONCLUSIVE, compare_relative([2], [1], "ns", {"host": "a"}, {"host": "b"}, True, 0).status)
        self.assertEqual(Status.FAIL, compare_relative([2_000_000], [1_000_000], "ns", {}, {}, True, 500_000).status)
        self.assertEqual(Status.PASS, compare_relative([1_400_000], [1_000_000], "ns", {}, {}, True, 500_000).status)

    def test_successful_retry_cannot_erase_independent_failure(self):
        import json
        result = self.result()
        failed = dataclasses.replace(result, decision=Decision(Status.FAIL, ("measured defect",)))
        passed = dataclasses.replace(result, decision=Decision(Status.PASS))
        with tempfile.TemporaryDirectory() as directory:
            write_report(RunRecord("run", (failed, passed), (result.case.id,)), Path(directory))
            document = json.loads((Path(directory) / "report.json").read_text())
            self.assertEqual("FAIL", document["axes"]["decision"]["status"])

    def test_nonfinite_phase_window_cannot_pass(self):
        result = self.result()
        result.observations["repetitions"][0]["measurementSeconds"] = float("nan")
        self.assertEqual(Status.INCONCLUSIVE, evaluate(result, CONFIG).status)
