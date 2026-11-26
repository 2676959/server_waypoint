import copy
import sys
import tempfile
import subprocess
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from models import Decision, Status
from reporting import combine_decisions


def snapshot(tick, connected=False, **resources):
    return {
        "tick": tick,
        "players": [{"name": "owner"}] if connected else [],
        "resources": {"sessions": 0, "tasks": 0, "entities": 0, "bossbars": 0,
            "items": 0, "mapTargets": 0, "tickCounters": 0, "targetIndexEntries": 0,
            "mapCacheEntries": 1, **resources},
    }


class NavigationDiagnosticsTest(unittest.TestCase):
    def test_profile_is_captured_and_stopped_when_workload_raises(self):
        from scenarios.navigation_diagnostics import capture_profile
        calls, outcomes = [], []
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "server.jfr"
            def start(*args):
                calls.append("start")
                return Decision(Status.PASS)
            def stop(*args):
                calls.append("stop")
                path.write_bytes(b"retained recording")
                return Decision(Status.PASS)
            with patch("scenarios.navigation_diagnostics.start_jfr", start), patch("scenarios.navigation_diagnostics.stop_jfr", stop):
                with self.assertRaisesRegex(RuntimeError, "workload failed"):
                    with capture_profile(object(), "/java", path, True, outcomes):
                        calls.append("workload")
                        raise RuntimeError("workload failed")
            self.assertEqual(["start", "workload", "stop"], calls)
            self.assertEqual([Status.PASS, Status.PASS], [outcome.status for outcome in outcomes])
            self.assertTrue(path.is_file())

    def test_failed_profiler_does_not_erase_a_product_failure_or_skip_work(self):
        from scenarios.navigation_diagnostics import capture_profile
        outcomes = [Decision(Status.FAIL, ("cleanup defect",))]
        calls = []
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "server.jfr"
            with patch("scenarios.navigation_diagnostics.start_jfr", side_effect=subprocess.TimeoutExpired("jcmd", 30)), patch("scenarios.navigation_diagnostics.stop_jfr") as stop:
                with capture_profile(object(), "/java", path, True, outcomes):
                    calls.append("workload")
            self.assertEqual(["workload"], calls)
            stop.assert_not_called()
            self.assertEqual(Status.FAIL, combine_decisions(outcomes).status)
            self.assertTrue(any("timed out" in reason for outcome in outcomes for reason in outcome.reasons))
            self.assertTrue(path.with_suffix(".error.json").is_file())

    def test_unprofiled_window_never_attaches_and_stop_failure_is_retained(self):
        from scenarios.navigation_diagnostics import capture_profile
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "server.jfr"
            outcomes = []
            with patch("scenarios.navigation_diagnostics.start_jfr", return_value=Decision(Status.PASS)) as start, patch("scenarios.navigation_diagnostics.stop_jfr", side_effect=RuntimeError("stop failed")) as stop:
                with capture_profile(object(), "/java", path, False, outcomes):
                    pass
                start.assert_not_called()
                stop.assert_not_called()
                with capture_profile(object(), "/java", path, True, outcomes):
                    pass
            self.assertEqual(Status.BLOCKED, combine_decisions(outcomes).status)
            self.assertTrue(any("stop failed" in reason for outcome in outcomes for reason in outcome.reasons))

    def test_quiescence_waits_for_disconnect_then_ten_native_ticks(self):
        from scenarios.navigation_diagnostics import disconnect_and_observe
        snapshots = iter((snapshot(100, True, sessions=1), snapshot(105, True), snapshot(106), snapshot(115), snapshot(116)))
        sent, closed = [], []
        class Probe:
            def send(self, kind):
                sent.append(kind)
            def wait(self, kind):
                return {"data": next(snapshots)}
        driver = SimpleNamespace(close=lambda: closed.append(True))
        with tempfile.TemporaryDirectory() as directory, patch("scenarios.navigation_diagnostics.time.sleep"):
            result = disconnect_and_observe(Probe(), driver.close, Path(directory))
            self.assertEqual([True], closed)
            self.assertEqual(Status.PASS, result["decisions"][0].status)
            self.assertEqual(116, result["after"]["tick"])
            self.assertTrue(Path(result["paths"]["quiescence"]).is_file())
            self.assertNotIn("commands.execute", sent)

    def test_zero_online_items_does_not_prove_disconnected_inventory_cleanup(self):
        from scenarios.navigation_diagnostics import disconnect_and_observe
        snapshots = iter((snapshot(100, True, items=2), snapshot(101), snapshot(111)))
        class Probe:
            def send(self, kind):
                pass
            def wait(self, kind):
                return {"data": next(snapshots)}
        with tempfile.TemporaryDirectory() as directory, patch("scenarios.navigation_diagnostics.time.sleep"):
            result = disconnect_and_observe(Probe(), lambda: None, Path(directory))
        self.assertEqual(Status.BLOCKED, combine_decisions(result["decisions"]).status)
        self.assertTrue(any("disconnected-owner inventories" in reason for d in result["decisions"] for reason in d.reasons))

    def test_missing_or_stalled_quiescence_never_passes(self):
        from scenarios.navigation_diagnostics import disconnect_and_observe, quiescence_decision
        before = snapshot(100, True, sessions=1)
        self.assertEqual(Status.BLOCKED, quiescence_decision(before, snapshot(109), 100).status)
        missing = snapshot(110)
        del missing["resources"]["tasks"]
        self.assertEqual(Status.BLOCKED, quiescence_decision(before, missing, 100).status)
        self.assertEqual(Status.BLOCKED, quiescence_decision({}, snapshot(110), 100).status)
        self.assertEqual(Status.BLOCKED, quiescence_decision({"resources": None}, snapshot(110), 100).status)
        class Probe:
            def send(self, kind):
                pass
            def wait(self, kind):
                return {"data": copy.deepcopy(before)}
        with tempfile.TemporaryDirectory() as directory, patch("scenarios.navigation_diagnostics.time.monotonic", side_effect=(0, 0, 31)), patch("scenarios.navigation_diagnostics.time.sleep"):
            result = disconnect_and_observe(Probe(), lambda: None, Path(directory))
        self.assertEqual(Status.BLOCKED, combine_decisions(result["decisions"]).status)

    def test_quiescent_leaks_fail_and_cache_retention_has_an_explicit_contract(self):
        from scenarios.navigation_diagnostics import quiescence_decision
        before = snapshot(100, True, sessions=1)
        self.assertEqual(Status.PASS, quiescence_decision(before, snapshot(120), 110).status)
        for key in ("sessions", "tasks", "entities", "bossbars", "items", "mapTargets", "tickCounters", "targetIndexEntries"):
            decision = quiescence_decision(before, snapshot(120, **{key: 1}), 110)
            self.assertEqual(Status.FAIL, decision.status, key)
        self.assertEqual(Status.FAIL, quiescence_decision(before, snapshot(120, mapCacheEntries=2), 110).status)


if __name__ == "__main__":
    unittest.main()
