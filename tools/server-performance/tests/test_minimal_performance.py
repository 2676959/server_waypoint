import copy
import json
from dataclasses import replace
from pathlib import Path
import sys
import unittest
import tempfile
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from models import Status
from test_minimal_selection import MinimalSelectionTest


def case():
    return next(c for c in MinimalSelectionTest().select() if c.variant == "minimal-retarget")


class MinimalPerformanceTest(unittest.TestCase):
    def test_schedule_alternates_once_per_owner_per_second(self):
        from scenarios.minimal_performance import schedule
        owners = tuple(f"owner-{i}" for i in range(10))
        actions = schedule(case(), 3, owners, "MEASURING")
        self.assertEqual(30, len(actions))
        for owner in owners:
            selected = [a for a in actions if a["bot"] == owner]
            self.assertEqual(["b", "a", "b"], [a["command"].split()[-1] for a in selected])
            self.assertTrue(all(a["acknowledged"] is True for a in selected))
        self.assertEqual(30, len({a["actionId"] for a in actions}))
        with self.assertRaises(ValueError):
            schedule(case(), 3, owners[:1], "MEASURING")

    def test_receipts_require_one_successful_native_completion_per_offered_action(self):
        from scenarios.minimal_performance import judge_delivery
        action = {"actionId":"x", "bot":"owner", "operation":"command", "command":"/wp navigate minecraft:overworld nav_fixture b", "acknowledged":True}
        receipt = {"actionId":"x", "bot":"owner", "operation":"command", "success":True,
            "clock":"client-monotonic", "completionScope":"client-roundtrip", "clientElapsedNs":4,
            "nativeAck":{"actionId":"x", "actor":"owner", "command":"wp navigate minecraft:overworld nav_fixture b",
                "stage":"command-completed", "serverObserved":True, "returned":1}}
        self.assertEqual(Status.PASS, judge_delivery([action], [receipt], 1, 0).status)
        for receipts, issued, pending in (([],1,0), ([receipt,receipt],1,0), ([receipt],0,0), ([receipt],1,1)):
            self.assertEqual(Status.BLOCKED, judge_delivery([action], receipts, issued, pending).status)
        for field, value in (("actor","other"), ("command","wp help"), ("returned",0), ("exception","broken")):
            bad = copy.deepcopy(receipt)
            bad["nativeAck"][field] = value
            self.assertNotEqual(Status.PASS, judge_delivery([action], [bad], 1, 0).status)

    def test_native_state_checks_exact_target_items_and_conservation(self):
        from scenarios.minimal_performance import judge_state
        target = {"dimension":"minecraft:overworld", "list":"nav_fixture", "waypoint":"b", "displayName":"Target B", "position":[-320,-61,-320], "rgb":4478310}
        owner = {"name":"owner", "uuid":"id", "methods":["compass","map","bossbar","actionbar","text_display","world_marker"],
            "ordinary":{"stone":64}, "target":target, "bossbar":"Target B", "text":"Target B", "dead":False,
            "items":[{"method":"compass","amount":1,"caption":"Target B","compassTarget":{"dimension":"minecraft:overworld","position":[-320,-61,-320]}},
                {"method":"map","amount":1,"mapState":{"centerX":-512,"centerZ":-512,"scale":2,"cursorX":96,"cursorZ":96,"caption":"Target B"}}]}
        snapshot = {"players":[owner]}
        args = ({"owner":"id"}, {"owner":{"stone":64}}, "b")
        self.assertEqual(Status.PASS, judge_state(snapshot, *args).status)
        for field, value in (("items",[]), ("ordinary",{}), ("methods",["map"]), ("target",{})):
            bad = copy.deepcopy(snapshot)
            bad["players"][0][field] = value
            self.assertEqual(Status.FAIL, judge_state(bad, *args).status)
        self.assertEqual(Status.BLOCKED, judge_state({"players":[]}, *args).status)

    def test_tick_observation_gaps_block_even_when_budget_is_fast(self):
        from scenarios.minimal_performance import judge_window
        window = {"seconds":60.1, "tickNs":[1000]*1200, "sessionCounts":[10]*1200,
            "offeredActions":600, "completedActions":600, "delivery":{"status":"PASS","reasons":[]},
            "maxOfferLagSeconds":0.02, "clock":"server-monotonic", "scope":"server-thread"}
        self.assertEqual(Status.PASS, judge_window(case(), window, 60).status)
        for key, value in (("sessionCounts",[10]*1199), ("sessionCounts",[1]*1200), ("tickNs",[]),
                           ("seconds",59), ("maxOfferLagSeconds",1.1), ("completedActions",599),
                           ("delivery",{"status":"BLOCKED","reasons":["missing ack"]})):
            bad = {**window, key:value}
            self.assertNotEqual(Status.PASS, judge_window(case(), bad, 60).status)
        slow = {**window, "tickNs":[50_000_000]*1200, "sessionCounts":[]}
        self.assertEqual(Status.FAIL, judge_window(case(), slow, 60).status)

    def test_partial_slow_ticks_survive_a_later_probe_error(self):
        from scenarios.minimal_performance import collect_window
        class Probe:
            def send(self, *args):
                pass
            def wait(self, *args):
                return {}
            def poll(self):
                return [{"type":"sample", "data":{"phase":"MEASURING", "durationNs":50_000_000,
                    "navigationSessions":0,"tickCount":1,"clock":"server-monotonic","scope":"server-thread"}},
                    {"type":"error", "data":{"reason":"stop unavailable"}}]
        with tempfile.TemporaryDirectory() as temporary:
            try:
                collect_window(replace(case(), variant="minimal-idle", clients=0, offered_rate=0), Probe(), None, "MEASURING", temporary, ())
            except ValueError:
                pass
            evidence = json.loads((Path(temporary) / "measuring.json").read_text())
            self.assertIn("decision", evidence)
            self.assertEqual("FAIL", evidence["decision"]["status"])
            self.assertIn("native tick budget exceeded", evidence["decision"]["reasons"])

    def test_interrupted_repetition_is_never_pass_and_stops_server(self):
        from scenarios.minimal_performance import run_repetition
        native = {"players":[], "resources":{"sessions":0}}
        stopped = []
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary).resolve() / "repeat"
            log = Path(temporary) / "server.log"
            log.write_text("All dimensions are saved")
            runtime = SimpleNamespace(probe={"path":"probe"})
            process = SimpleNamespace(pid=123,log=log)
            with patch("scenarios.minimal_performance.install_server", return_value=1234), patch("scenarios.minimal_performance.clone_baseline"), \
                 patch("scenarios.minimal_performance.boot", return_value=(process, object())), \
                 patch("scenarios.minimal_performance.snapshot", return_value=native), \
                 patch("scenarios.minimal_performance.collect_window", side_effect=KeyboardInterrupt()), \
                 patch("scenarios.minimal_performance.stop_owned", side_effect=lambda *a: stopped.append(True)):
                with self.assertRaises(KeyboardInterrupt):
                    run_repetition(replace(case(), variant="minimal-idle", clients=0, offered_rate=0), directory,
                        (runtime, SimpleNamespace(sha256="candidate"), Path("candidate.jar")), Path("baseline"))
            self.assertEqual([True], stopped)
            evidence = json.loads((directory / "repetition.json").read_text())
            self.assertEqual("BLOCKED", evidence["decision"]["status"])
            self.assertTrue(evidence["errors"])

    def test_interrupted_current_repetition_keeps_its_failure_in_root_results(self):
        from scenarios.minimal_performance import run_case
        from test_minimal_runner import MinimalBindingTest
        from contracts import atomic_write, read_document
        from preparation import seal_evidence
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve() / "run"
            MinimalBindingTest().fixture(root)
            prepared = root / "fixtures/navigation-terrain-medium"
            (prepared / "frozen").mkdir(parents=True)
            atomic_write(prepared / "baseline.json", {"schemaVersion":1,"files":{},"recipeSha256":"fixture"})
            settings = read_document(root / "prepared.json")
            settings["worlds"] = {"navigation-terrain-medium":str(prepared)}
            settings["evidence"] = seal_evidence(root, settings["worlds"])
            atomic_write(root / "prepared.json", settings)
            def interrupted(selected, directory, *args):
                directory.mkdir(parents=True)
                atomic_write(directory / "repetition.json", {"schemaVersion":1,"decision":{"status":"FAIL","reasons":["native tick budget exceeded"]},"windows":{},"shutdown":True})
                raise KeyboardInterrupt()
            with patch("scenarios.minimal_performance.run_repetition", side_effect=interrupted):
                with self.assertRaises(KeyboardInterrupt):
                    run_case(case(), root)
            results = list((root / "results").glob("interrupted-*.json"))
            self.assertEqual(1, len(results))
            result = read_document(results[0])
            self.assertEqual("FAIL", result["decision"]["status"])
            self.assertIn("native tick budget exceeded", result["decision"]["reasons"])

    def test_failed_window_survives_shutdown_failure(self):
        from scenarios.minimal_performance import run_repetition
        from contracts import atomic_write
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary).resolve() / "repeat"
            log = Path(temporary) / "server.log"
            log.write_text("")
            def failed_window(selected, probe, driver, phase, output, owners):
                value = {"decision":{"status":"FAIL","reasons":["native tick budget exceeded"]}}
                atomic_write(output / "measuring.json", {"schemaVersion":1, **value})
                raise ValueError("observation unavailable")
            with patch("scenarios.minimal_performance.install_server", return_value=1234), patch("scenarios.minimal_performance.clone_baseline"), \
                 patch("scenarios.minimal_performance.boot", return_value=(SimpleNamespace(pid=123,log=log), object())), \
                 patch("scenarios.minimal_performance.snapshot", return_value={"players":[],"resources":{"sessions":0}}), \
                 patch("scenarios.minimal_performance.collect_window", side_effect=failed_window), \
                 patch("scenarios.minimal_performance.stop_owned", side_effect=RuntimeError("shutdown unavailable")):
                try:
                    run_repetition(replace(case(), variant="minimal-idle", clients=0, offered_rate=0), directory,
                        (SimpleNamespace(probe={"path":"probe"}), SimpleNamespace(sha256="candidate"), Path("candidate.jar")), Path("baseline"))
                except RuntimeError:
                    pass
            evidence = json.loads((directory / "repetition.json").read_text())
            self.assertEqual("FAIL", evidence["decision"]["status"])
            self.assertIn("native tick budget exceeded", evidence["decision"]["reasons"])
            self.assertTrue(any("shutdown unavailable" in r for r in evidence["decision"]["reasons"]))


if __name__ == "__main__":
    unittest.main()
