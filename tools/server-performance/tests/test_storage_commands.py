import sys
import unittest
import tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scenarios.storage_commands import command_coverage, denied_mutation, restore_once, stale_edit, persisted_state, workload_schedule
from models import Status


class StorageCommandsTest(unittest.TestCase):
    def test_process_allocation_requires_same_jvm_and_monotonic_supported_counters(self):
        from scenarios.storage_commands import process_allocation
        before = {"pid":12,"startedMs":3,"allocatedBytes":100,"scope":"whole-process"}
        self.assertEqual(25, process_allocation(before, {**before,"allocatedBytes":125}))
        for after in ({**before,"allocatedBytes":-1}, {**before,"pid":13}, {**before,"allocatedBytes":99}):
            with self.assertRaises(ValueError):
                process_allocation(before, after)

    def test_failed_window_retains_allocation_diagnostics_without_hiding_original_error(self):
        from unittest.mock import patch
        from scenarios.command_workload import collect_commands
        from contracts import read_document
        before={"pid":12,"startedMs":3,"allocatedBytes":100,"scope":"whole-process"}
        after={**before,"allocatedBytes":150}
        class Probe:
            def __init__(self): self.values=iter((before,after))
            def send(self,operation):
                if operation != "process.snapshot": raise AssertionError(operation)
            def wait(self,kind): return {"data":next(self.values)}
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"window.json"
            with patch("scenarios.command_workload._collect_commands",side_effect=ValueError("original overload")):
                with self.assertRaisesRegex(ValueError,"original overload"):
                    collect_commands(Probe(),None,"small",["alice"],60,"MEASURING","p",evidence_path=path)
            raw=read_document(path)
            self.assertFalse(raw["complete"])
            self.assertEqual([before,after],raw["processAllocation"])

    def test_unfulfilled_load_is_inconclusive_and_preserves_prior_correctness_failure(self):
        from scenarios.storage_runner import incomplete_result
        from contracts import atomic_write
        from models import CaseSpec,CaseResult,Decision
        case=CaseSpec("c","paper-1.21.11","storage","concurrent","medium","audit","navigation-terrain",(),())
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"partial.json"
            self.assertEqual(Status.INCONCLUSIVE,incomplete_result(path,case,ValueError("demand unfulfilled")).decision.status)
            atomic_write(path,CaseResult(case,Decision(Status.FAIL,("known correctness failure",))))
            result=incomplete_result(path,case,ValueError("demand unfulfilled"))
            self.assertEqual(Status.FAIL,result.decision.status)
            self.assertIn("known correctness failure",result.decision.reasons)

    def test_native_watchdog_failure_survives_command_timeout(self):
        from unittest.mock import patch
        from scenarios.storage_runner import run_case, record_health
        from contracts import atomic_write
        from models import CaseSpec, CaseResult, Decision
        case = CaseSpec("c", "paper-1.21.11", "storage", "concurrent", "medium", "audit", "navigation-terrain", (), ())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "partials").mkdir()
            log = root / "server.log"
            log.write_text("\x1b[m> \r  \r\x1b[31;1m[00:00:00 ERROR]: The server has not responded for 10 seconds!\n")
            def interrupted(case, root, partial):
                assertions, outcomes = {}, []
                record_health(log, root, assertions, outcomes, "native-log-healthy")
                atomic_write(partial, CaseResult(case, outcomes[0], assertions=assertions))
                raise ValueError("native command acknowledgement deadline")
            with patch("scenarios.storage_runner.execute_case", side_effect=interrupted):
                result = run_case(case, root)
            self.assertEqual(Status.FAIL, result.decision.status)
            self.assertFalse(result.assertions["native-log-healthy"])
            self.assertTrue(any("watchdog" in reason for reason in result.decision.reasons))
            self.assertTrue((root / "server-health.json").exists())

    def test_semantic_rows_require_exact_order_not_just_feedback_presence(self):
        from scenarios.storage_commands import verify_rows
        self.assertEqual(Status.PASS, verify_rows("[P] bravo · (1, 2, 3)\n[P] alpha · (4, 5, 6)", ("bravo","alpha")).status)
        self.assertEqual(Status.FAIL, verify_rows("[P] alpha · (4, 5, 6)\n[P] bravo · (1, 2, 3)", ("bravo","alpha")).status)
        self.assertEqual(Status.FAIL, verify_rows("unrelated feedback", ("bravo",)).status)

    def test_skewed_traffic_preserves_tier_rates_and_pins_existing_mutation_target(self):
        from scenarios.storage_commands import planned_actions
        actions = planned_actions("stress", ["alice"], 1, "s", read_commands=("/wp help", "/wp details list minecraft:overworld perf_00"))
        self.assertEqual(110, len(actions))
        self.assertEqual(10, sum(" set color " in action["command"] for _, action in actions))

    def test_leaf_ownership_cannot_silently_discard_future_command_families(self):
        from scenarios.storage_commands import leaf_ownership
        tree = {"origin":"native-minecraft-command-dispatcher","nodes":[
            {"path":"wp help","executable":True}, {"path":"wp tp","executable":True},
            {"path":"wp navigate","executable":True}, {"path":"wp upload","executable":True},
            {"path":"wp remote list","executable":True}, {"path":"sw-cross-server-keygen","executable":True}]}
        owners = leaf_ownership(tree)
        self.assertEqual({"wp help","wp tp"}, set(owners["task10"]))
        self.assertEqual(["wp navigate"], owners["task11"])
        self.assertEqual(["wp upload"], owners["task12"])
        self.assertEqual(["sw-cross-server-keygen","wp remote list"], owners["task15"])
        tree["nodes"].append({"path":"wp future","executable":True})
        with self.assertRaises(ValueError):
            leaf_ownership(tree)

    def test_behavior_receipt_must_match_actor_input_and_expected_leaf(self):
        from scenarios.storage_commands import match_receipt
        event = {"path":"wp help","actor":"alice","input":"wp help","serverObserved":True,"allocatedBytes":0,"durationNs":1}
        self.assertEqual(event, match_receipt({"callbacks":[event],"droppedCallbacks":0}, "wp help", "alice", "wp help"))
        for key, value in (("actor","bob"),("input","wp list"),("path","wp list"),("serverObserved",False),("exception","error")):
            with self.assertRaises(ValueError):
                match_receipt({"callbacks":[{**event,key:value}],"droppedCallbacks":0}, "wp help", "alice", "wp help")

    def test_permission_matrix_tests_fallback_grant_and_operator_override(self):
        from scenarios.command_behavior import permission_matrix
        matrix = permission_matrix()
        states = {(row["operator"], row["override"], row["allowed"]) for row in matrix}
        self.assertEqual({(False,None,False),(True,None,True),(False,True,True),(True,False,False)}, states)
        self.assertEqual({"add","edit","remove","restore","tp","reload","navigate","upload","upload.delete","remote.list","remote.tp"}, {row["branch"] for row in matrix})

    def test_semantic_fixture_has_distinct_sort_search_visibility_and_tie_inputs(self):
        from scenarios.command_behavior import semantic_fixture
        value = semantic_fixture()
        self.assertEqual(["charlie","alpha","bravo","delta"], [p["name"] for p in value["waypoints"]])
        self.assertEqual(2, sum("needle" in p["keywords"] for p in value["waypoints"]))
        self.assertEqual([True,True,True,False], [p["global"] for p in value["waypoints"]])

    def test_suggestion_rate_requires_distinct_correlated_provider_completions(self):
        from scenarios.command_workload import validate_suggestion_window
        reports = [{"requestId":"p-0","input":"wp details ","droppedProviderCallbacks":0,
                    "providerCallbacks":[{"input":"wp details ","providerId":7,"serverObserved":True,"durationNs":10}]}]
        validate_suggestion_window(reports,1,"p","wp details ",7)
        for invalid in ([],reports+reports,[{**reports[0],"requestId":"other"}],[{**reports[0],"providerCallbacks":[]} ]):
            with self.assertRaises(ValueError):
                validate_suggestion_window(invalid,1,"p","wp details ",7)

    def test_known_feedback_failure_cannot_hide_an_independent_unexecuted_leaf(self):
        from contracts import atomic_write
        from scenarios.command_behavior import local_leaf_report
        tree={"origin":"native-minecraft-command-dispatcher","nodes":[{"path":p,"executable":True} for p in ("wp help","wp tp","wp add")]}
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            atomic_write(root/"reads.json",{"schemaVersion":1,"feedback":[{"path":"wp help","serverObserved":True,"feedbackObserved":True}]})
            atomic_write(root/"mutations.json",{"schemaVersion":1,"observations":[]})
            atomic_write(root/"semantics.json",{"schemaVersion":1,"failures":["missing tp feedback"],"playerCommands":[{"feedback":[],"dispatch":{"callbacks":[{"path":"wp tp","serverObserved":True}]}}]})
            atomic_write(root/"boundaries.json",{"schemaVersion":1,"observations":[]})
            with self.assertRaises(ValueError):
                local_leaf_report(tree,root/"reads.json",root/"mutations.json",root/"semantics.json",root/"coverage.json",root/"boundaries.json")

    def test_each_command_leaf_has_execution_evidence(self):
        required = ("wp help", "wp list <dimension>")
        self.assertEqual(Status.BLOCKED, command_coverage(required, [{"path": "wp help", "serverObserved": True}], ()).status)
        events = [{"path": path, "serverObserved": True, "feedbackObserved": True} for path in required]
        self.assertEqual(Status.PASS, command_coverage(required, events, ()).status)
        self.assertEqual(Status.BLOCKED, command_coverage(required, events, ("wp list <dimension>",)).status)

    def test_explicit_missing_delivery_is_a_failure_not_a_missing_capture(self):
        event={"path":"wp tp","serverObserved":True,"feedbackObserved":False}
        self.assertEqual(Status.FAIL,command_coverage(("wp tp",),[event],()).status)

    def test_denied_mutation_preserves_revision(self):
        before = {"revision": 7, "names": ["origin"]}
        self.assertEqual(Status.PASS, denied_mutation(before, dict(before), False).status)
        self.assertEqual(Status.FAIL, denied_mutation(before, {"revision": 8, "names": []}, False).status)

    def test_restore_is_single_use(self):
        events = [{"token": "token", "success": True}, {"token": "token", "success": False}]
        self.assertEqual(Status.PASS, restore_once(events).status)
        self.assertEqual(Status.FAIL, restore_once([events[0], events[0]]).status)

    def test_native_restore_replay_requires_consumed_token_and_explicit_rejection(self):
        from scenarios.storage_commands import restore_rejection
        event={"serverObserved":True,"path":"wp restore <token>","input":"wp restore t","actor":"CONSOLE","feedbackObserved":True,"feedback":["✘ That restore action expired or isn't available anymore."]}
        self.assertEqual(Status.PASS,restore_rejection("t",[],event).status)
        self.assertEqual(Status.FAIL,restore_rejection("t",["t"],event).status)
        self.assertEqual(Status.FAIL,restore_rejection("t",[],{**event,"feedback":["Waypoint already exists"]}).status)

    def test_stale_edit_rejected(self):
        self.assertEqual(Status.PASS, stale_edit(6, {"revision": 7, "name": "origin"}, {"revision": 7, "name": "origin"}, False).status)
        self.assertEqual(Status.FAIL, stale_edit(6, {"revision": 7, "name": "origin"}, {"revision": 8, "name": "renamed"}, True).status)

    def test_persisted_state_matches_committed_operations(self):
        expected = [{"list_name": "perf", "n": 9, "waypoints": [{"name": "renamed", "pos": [1, 64, -16]}]}]
        self.assertEqual(Status.PASS, persisted_state(expected, expected).status)
        self.assertEqual(Status.FAIL, persisted_state(expected, []).status)

    def test_declared_read_mutation_rates_and_window_bounds(self):
        small = workload_schedule("small", 1)
        medium = workload_schedule("medium", 1)
        self.assertEqual(10, sum(operation == "read" for _, operation in small))
        self.assertEqual(1, sum(operation == "mutate" for _, operation in small))
        self.assertEqual(50, sum(operation == "read" for _, operation in medium))
        self.assertEqual(5, sum(operation == "mutate" for _, operation in medium))
        self.assertTrue(all(0 <= at < 1 for at, _ in medium))

    def test_operation_demand_is_read_from_the_manifest_and_conflicts_are_rejected(self):
        from contracts import atomic_write
        from scenarios.storage_commands import declared_rates
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"scenarios.json"
            rows=[{"feature":feature,"rates":{"small":7},"mutationRates":{"small":2},"suggestionRates":{"small":3}} for feature in ("storage","commands")]
            atomic_write(path,{"schemaVersion":1,"scenarios":rows})
            self.assertEqual((7,2,3),declared_rates("small",path))
            rows[1]["mutationRates"]["small"]=4
            atomic_write(path,{"schemaVersion":1,"scenarios":rows})
            with self.assertRaises(ValueError):
                declared_rates("small",path)

    def test_fixture_counts_revisions_and_dimension_distribution(self):
        from scenarios.storage_commands import make_dataset
        dataset = make_dataset("small")
        self.assertEqual({"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}, set(dataset))
        self.assertEqual(10, sum(len(lists) for lists in dataset.values()))
        self.assertEqual([333, 333, 334], sorted(sum(len(value["waypoints"]) for value in lists) for lists in dataset.values()))
        self.assertTrue(all(value["n"] == 7 for lists in dataset.values() for value in lists))
        skewed = make_dataset("small", skewed=True)
        self.assertEqual(900, max(len(value["waypoints"]) for lists in skewed.values() for value in lists))

    def test_saved_map_order_does_not_hide_missing_or_changed_fields(self):
        from scenarios.storage_commands import canonical_saved
        expected = {"minecraft:overworld": [{"list_name": "b", "n": 7, "waypoints": [{"name": "second", "color": "#112233"}, {"name": "first", "color": "#112233"}]}, {"list_name": "a", "n": 3, "waypoints": []}]}
        reordered = {"minecraft:overworld": [expected["minecraft:overworld"][1], {"list_name": "b", "n": 7, "waypoints": list(reversed(expected["minecraft:overworld"][0]["waypoints"]))}]}
        self.assertEqual(canonical_saved(expected), canonical_saved(reordered))
        reordered["minecraft:overworld"][1]["n"] = 8
        self.assertNotEqual(canonical_saved(expected), canonical_saved(reordered))

    def test_reopen_compares_keyed_identity_and_preserves_revision(self):
        from scenarios.storage_commands import canonical_snapshot
        first = [{"dimension": "minecraft:overworld", "name": "b", "revision": 7, "waypoints": ["second", "first"]}, {"dimension": "minecraft:overworld", "name": "a", "revision": 5, "waypoints": []}]
        second = [{"dimension": "minecraft:overworld", "name": "a", "revision": 5, "waypoints": []}, {"dimension": "minecraft:overworld", "name": "b", "revision": 7, "waypoints": ["first", "second"]}]
        self.assertEqual(canonical_snapshot(first), canonical_snapshot(second))
        second[0]["revision"] = 6
        self.assertNotEqual(canonical_snapshot(first), canonical_snapshot(second))

    def test_player_traffic_uses_declared_rates_and_unique_action_ids(self):
        from scenarios.storage_commands import planned_actions
        actions = planned_actions("medium", [f"actor-{index}" for index in range(10)], 1, "phase")
        self.assertEqual(55, len(actions))
        self.assertEqual(55, len({action["actionId"] for _, action in actions}))
        writes = [action for _, action in actions if " set color " in action["command"]]
        self.assertEqual(5, len(writes))
        self.assertEqual({"actor-0"}, {action["bot"] for action in writes})
        self.assertEqual("/wp edit waypoint minecraft:overworld perf_00 wp_00000 set color 100005", writes[-1]["command"])
        self.assertEqual(10, len({action["bot"] for _, action in actions}))

    def test_mixed_command_timing_includes_distinct_mutation_paths_and_console_sources(self):
        from scenarios.storage_commands import planned_actions
        actions=planned_actions("small",["alice"],6,"mixed",mutation_mode="mixed",console_reads=True)
        self.assertEqual(66,len(actions))
        writes=[action for _,action in actions if action["kind"]=="mutate"]
        self.assertEqual(["/wp edit waypoint minecraft:overworld perf_00 wp_00000 set color 100001",
                          "/wp tp minecraft:overworld semantic alpha","/wp reload",
                          "/wp edit waypoint minecraft:overworld perf_00 wp_00000 set color 100004",
                          "/wp tp minecraft:overworld semantic charlie","/wp reload"], [action["command"] for action in writes])
        console=[action for _,action in actions if action.get("transport")=="console"]
        self.assertEqual(6,len(console))
        self.assertEqual(3,sum(action["command"].startswith("/execute as alice at alice run wp ") for action in console))
        self.assertEqual(3,sum(action["command"].startswith("/wp ") for action in console))

    def test_async_action_log_costs_require_exact_successful_committed_completions(self):
        from scenarios.command_workload import action_log_completions
        actions=[{"command":"/wp reload"},{"command":"/wp tp minecraft:overworld semantic alpha"}]
        lines="[x] [Folia Async Scheduler Thread #1/INFO]: [server_waypoint.actions] action=reload player=alice outcome=completed\n[x] [Server thread/INFO]: [server_waypoint.actions] action=tp player=alice outcome=success"
        self.assertEqual(2,len(action_log_completions(lines,actions)))
        self.assertIsNone(action_log_completions(lines.splitlines()[0],actions))
        with self.assertRaises(ValueError):
            action_log_completions(lines.replace("outcome=success","outcome=failed"),actions)

    def test_overdue_batch_drains_real_ledger_completions_while_offering(self):
        import time
        from bot_driver import BotStream
        from scenarios.command_workload import offer_due
        stream=BotStream("run","attempt")
        actions=[(0,{"actionId":str(i)}) for i in range(400)]
        sequence=0
        def poll():
            nonlocal sequence
            if stream.pending:
                identifier=next(iter(stream.pending));sequence+=1
                stream.parse({"schemaVersion":1,"runId":"run","attemptId":"attempt","sequence":sequence,"type":"action_result","data":{"actionId":identifier}})
        index=offer_due(actions,0,6,lambda action:stream.offer(action["actionId"]),poll,lambda action:len(stream.pending)<256,time.monotonic()+1)
        self.assertEqual(400,index)
        self.assertEqual(400,len(stream.pending)+len(stream.completed))
        self.assertLessEqual(len(stream.pending),1)

    def test_warmup_drain_allows_startup_lag_but_measurement_still_rejects_it(self):
        from scenarios.command_workload import summarize_window,WorkloadIncomplete
        raw={"phase":"WARMUP","seconds":30,"expectedPlayers":1,"samples":[{"durationNs":1,"onlinePlayers":1,"tickIndex":1}],"actions":[{}],"latencies":[1],"callbacks":[{"allocatedBytes":0}],"completedWithinWindow":1,"droppedCallbacks":0,"rates":{"offered":1,"completed":1,"pending":0,"elapsedSeconds":30,"maxLagSeconds":6}}
        summarize_window(raw,0)
        raw["phase"]="MEASURING"
        with self.assertRaises(WorkloadIncomplete): summarize_window(raw,30)

    def test_latency_requires_exact_native_ack_for_every_action(self):
        from scenarios.storage_commands import acknowledged_latencies
        action = {"actionId":"phase-1","bot":"alice","command":"/wp help"}
        event = {"actionId":"phase-1","bot":"alice","clock":"client-monotonic","completionScope":"client-issued","clientElapsedNs":100,"nativeAck":None}
        with self.assertRaises(ValueError):
            acknowledged_latencies([action], [event])
        event.update(completionScope="client-roundtrip", nativeAck={"actionId":"phase-1","command":"wp help","actor":"alice","serverObserved":True,"stage":"command-completed"})
        self.assertEqual([100], acknowledged_latencies([action], [event]))
        event["nativeAck"]["actor"] = "bob"
        with self.assertRaises(ValueError):
            acknowledged_latencies([action], [event])
        with self.assertRaises(ValueError):
            acknowledged_latencies([action], [])

    def test_command_window_does_not_hide_missing_ticks_or_dropped_callbacks(self):
        from scenarios.command_workload import summarize_window
        raw = {"seconds":60,"expectedPlayers":1,"samples":[{"durationNs":1000,"onlinePlayers":1,"tickIndex":1}],"actions":[{}],"latencies":[100],"callbacks":[{"allocatedBytes":5}],"completedWithinWindow":1,"droppedCallbacks":0,"rates":{"offered":1,"completed":1,"pending":0,"elapsedSeconds":60,"maxLagSeconds":0}}
        summary = summarize_window(raw, 30)
        self.assertEqual([1000], summary["tickNs"])
        self.assertEqual(1, summary["completedActions"])
        raw["droppedCallbacks"] = 1
        with self.assertRaises(ValueError):
            summarize_window(raw, 30)
        raw["droppedCallbacks"] = 0
        raw["samples"] = []
        with self.assertRaises(ValueError):
            summarize_window(raw, 30)

    def test_concurrent_storage_case_declares_real_tier_actor_count(self):
        from registry import expand_cases
        config = Path(__file__).resolve().parents[1] / "config"
        cases = expand_cases(config / "features.json", config / "scenarios.json", (), "audit", ("small","medium","stress"))
        self.assertEqual({"small":1,"medium":10,"stress":50}, {case.tier:case.clients for case in cases if case.feature == "storage" and case.variant == "concurrent"})

    def test_skewed_storage_uses_the_same_real_tier_actor_counts(self):
        from registry import expand_cases
        config = Path(__file__).resolve().parents[1] / "config"
        cases = expand_cases(config / "features.json", config / "scenarios.json", (), "audit", ("small","medium","stress"))
        self.assertEqual({"small":1,"medium":10,"stress":50}, {case.tier:case.clients for case in cases if case.feature == "storage" and case.variant == "skewed-list"})

    def test_command_rate_tiers_have_enough_independent_native_actors(self):
        from registry import expand_cases
        config=Path(__file__).resolve().parents[1]/"config"
        cases=expand_cases(config/"features.json",config/"scenarios.json",(),"audit",("small","medium","stress"))
        for variant in {case.variant for case in cases if case.feature == "commands"}:
            self.assertEqual({"small":1,"medium":10,"stress":50},{case.tier:case.clients for case in cases if case.feature == "commands" and case.variant == variant})

    def test_join_settle_requires_healthy_native_ticks(self):
        from scenarios.command_behavior import wait_for_recovered_dispatch
        class Probe:
            def __init__(self, durations):
                self.durations = list(durations)
                self.sent = []
                self.offset = 0
            def send(self, operation, payload=None):
                self.sent.append(operation)
            def wait(self, kind, timeout=60):
                return {"data": {}}
            def poll(self):
                if self.offset >= len(self.durations):
                    return []
                duration = self.durations[self.offset]
                self.offset += 1
                return [{"type":"sample","data":{"durationNs":duration,"phase":"SETTLE"}}]
        recovered = Probe([2_000_000_000, 1_000_000, 2_000_000_000] + [1_000_000] * 20)
        wait_for_recovered_dispatch(recovered, timeout=3, required=20)
        self.assertEqual(["scenario.start", "scenario.stop"], [value for value in recovered.sent if value.startswith("scenario.")])
        with self.assertRaisesRegex(TimeoutError, "native command dispatch did not recover"):
            wait_for_recovered_dispatch(Probe([2_000_000_000]), timeout=0.2)

    def test_join_settle_retries_deadline_until_native_ack(self):
        from scenarios.command_behavior import wait_for_recovered_dispatch
        class Probe:
            def send(self, operation, payload=None):
                pass
            def wait(self, kind, timeout=60):
                return {"data": {}}
            def poll(self):
                return [{"type":"sample","data":{"durationNs":1_000_000,"phase":"SETTLE"}}]
        class Driver:
            def __init__(self):
                self.sent = []
                self.replies = [
                    {"actionId":"a","error":"native command acknowledgement deadline","success":False,"nativeAck":None},
                    {"actionId":"b","error":"native command acknowledgement deadline","success":False,"nativeAck":None},
                    {"actionId":"c","success":True,"nativeAck":{"serverObserved":True,"stage":"command-completed","command":"wp help"}},
                ]
            def send(self, kind, payload=None):
                self.sent.append((kind, payload))
            def wait(self, kind, timeout=30):
                return self.replies.pop(0)
        driver = Driver()
        recovered = wait_for_recovered_dispatch(Probe(), driver, "swp_reader_00", timeout=3, required=1)
        self.assertTrue(recovered["nativeAck"]["serverObserved"])
        self.assertEqual(3, len(driver.sent))
        self.assertTrue(all(payload["acknowledged"] and payload["command"] == "/wp help" for _, payload in driver.sent))

    def test_join_settle_rejects_unexpected_acknowledgement_error(self):
        from scenarios.command_behavior import wait_for_recovered_dispatch
        class Probe:
            def send(self, operation, payload=None):
                pass
            def wait(self, kind, timeout=60):
                return {"data": {}}
            def poll(self):
                return [{"type":"sample","data":{"durationNs":1_000_000,"phase":"SETTLE"}}]
        class Driver:
            def send(self, kind, payload=None):
                pass
            def wait(self, kind, timeout=30):
                return {"error":"unknown actor/action acknowledgement","nativeAck":None}
        with self.assertRaisesRegex(ValueError, "unknown actor/action acknowledgement"):
            wait_for_recovered_dispatch(Probe(), Driver(), "swp_reader_00", timeout=3, required=1)

    def test_remaining_operators_are_granted_one_at_a_time(self):
        from scenarios.command_behavior import prepare_command_operator, prepare_remaining_operators
        class Probe:
            def __init__(self):
                self.commands = []
            def send(self, operation, payload=None):
                if operation == "commands.execute":
                    self.commands.extend(payload["commands"])
            def wait(self, kind, timeout=60):
                return {"data": {"callbacks": []}}
            def poll(self):
                return [{"type":"sample","data":{"durationNs":1_000_000,"phase":"SETTLE"}}]
        class Driver:
            def send(self, kind, payload=None):
                pass
            def wait(self, kind, timeout=30):
                return {"success":True,"nativeAck":{"serverObserved":True,"stage":"command-completed","command":"wp help"}}
        probe = Probe()
        prepare_command_operator(probe, Driver(), "swp_reader_00", timeout=3, required=1)
        self.assertEqual(["op swp_reader_00"], probe.commands)
        prepare_remaining_operators(probe, Driver(), ["swp_reader_00","swp_reader_01","swp_reader_02"], timeout=3, required=1)
        self.assertEqual(["op swp_reader_00","op swp_reader_01","op swp_reader_02"], probe.commands)

    def test_command_samples_require_declared_actor_count_and_contiguous_ticks(self):
        from scenarios.command_workload import validate_native_samples
        samples = [{"durationNs":100,"onlinePlayers":10,"tickIndex":5},{"durationNs":200,"onlinePlayers":10,"tickIndex":6}]
        validate_native_samples(samples,10)
        samples[1]["onlinePlayers"] = 9
        with self.assertRaises(ValueError):
            validate_native_samples(samples,10)
        samples[1]["onlinePlayers"] = 10
        samples[1]["tickIndex"] = 7
        with self.assertRaises(ValueError):
            validate_native_samples(samples,10)

    def test_missing_command_allocation_cannot_pass(self):
        from scenarios.command_workload import validate_command_allocations
        validate_command_allocations([{"allocatedBytes":0},{"allocatedBytes":125}])
        for callbacks in ([],[{}],[{"allocatedBytes":-1}]):
            with self.assertRaises(ValueError):
                validate_command_allocations(callbacks)
