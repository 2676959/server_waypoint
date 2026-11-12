import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from budgets import evaluate
from models import CaseResult, CaseSpec, Decision, Status

BUDGETS = Path(__file__).resolve().parents[1] / "config/budgets.json"


def case(feature="navigation", variant="methods", tier="medium", clients=10, runtime="paper-1.21.11", repeats=3):
    return CaseSpec(
        f"{runtime}:{feature}:{variant}:{tier}", runtime, feature, variant, tier, "audit",
        "navigation-terrain", ("action-latency", "completed-actions"), ("backend-medium", "correctness-bounds"),
        clients=clients, offered_rate={"small": 10, "medium": 50, "stress": 100}[tier], repeats=repeats,
        minimum_samples=100)


class NavigationRunnerTest(unittest.TestCase):
    def test_routes_three_families_to_the_navigation_runner(self):
        from scenarios import run_case
        with patch("scenarios.navigation_runner.run_case", return_value="routed") as routed:
            for feature, variant in (("navigation", "methods"), ("map-terrain", "warm-cold"), ("protection", "drop-pickup-spawn-move")):
                self.assertEqual("routed", run_case(case(feature, variant), Path("/tmp/unused-navigation-route")))
            self.assertEqual(3, routed.call_count)

    def test_unknown_variant_and_wrong_runtime_are_blocked_before_boot(self):
        from scenarios.navigation_runner import run_case
        unknown = run_case(case(variant="not-a-variant"), Path("/tmp/unused-navigation-route"))
        self.assertEqual(Status.BLOCKED, unknown.decision.status)
        wrong = run_case(case(runtime="fabric-1.21.11"), Path("/tmp/unused-navigation-route"))
        self.assertEqual(Status.BLOCKED, wrong.decision.status)
        self.assertTrue(any("Paper 1.21.11" in reason for reason in wrong.decision.reasons))

    def test_stale_bot_probe_or_artifact_blocks_before_a_sample(self):
        from scenarios.navigation_workload import reject_stale_bytes
        self.assertEqual(Status.BLOCKED, reject_stale_bytes({"bot": False, "probe": True, "artifact": True}).status)
        self.assertEqual(Status.BLOCKED, reject_stale_bytes({"bot": True, "probe": False, "artifact": True}).status)
        self.assertEqual(Status.BLOCKED, reject_stale_bytes({"bot": True, "probe": True, "artifact": False}).status)
        self.assertEqual(Status.PASS, reject_stale_bytes({"bot": True, "probe": True, "artifact": True}).status)

    def test_schedule_uses_tier_rate_and_excludes_fixture_setup(self):
        from scenarios.navigation_behavior import VARIANT_CATALOG
        from scenarios.navigation_workload import action_schedule
        selected = case()
        actions = action_schedule(selected, 1)
        self.assertEqual(selected.offered_rate, len(actions))
        operation = VARIANT_CATALOG[(selected.feature, selected.variant)]["action"]["operation"]
        self.assertTrue(all(action["operation"] == operation for action in actions))
        self.assertTrue(all(not str(action.get("command", "")).startswith("/give") for action in actions))
        self.assertTrue(all(not str(action.get("command", "")).startswith("op ") for action in actions))
        assigned = action_schedule(selected, 1, ("swp_reader_00", "swp_reader_01"))
        self.assertEqual({"swp_reader_00", "swp_reader_01"}, {action["bot"] for action in assigned})
        self.assertNotIn("owner", {action["bot"] for action in assigned})
        warm = {action["actionId"] for action in action_schedule(selected, 1, phase="WARMUP")}
        measured = {action["actionId"] for action in action_schedule(selected, 1, phase="MEASURING")}
        self.assertTrue(warm.isdisjoint(measured))

    def test_waypoint_commands_request_native_completion_before_counting_latency(self):
        from scenarios.navigation_workload import action_schedule, native_command_latency
        action = action_schedule(case(), 1, ("swp_reader_00",))[0]
        self.assertIs(True, action.get("acknowledged"))
        issued = {"actionId": action["actionId"], "clock": "client-monotonic",
            "completionScope": "client-issued", "clientElapsedNs": 50_000_000, "nativeAck": None}
        self.assertIsNone(native_command_latency(action, issued))
        ack = {"actionId": action["actionId"], "command": action["command"].lstrip("/"),
            "actor": action["bot"], "serverObserved": True, "stage": "command-completed"}
        receipt = {**issued, "completionScope": "client-roundtrip", "nativeAck": ack}
        self.assertEqual(50_000_000, native_command_latency(action, receipt))
        for key, value in (("stage", "marker-registered"), ("actor", "other-owner"),
                ("command", "wp help"), ("actionId", "unrelated"), ("serverObserved", False),
                ("exception", "command failed")):
            self.assertIsNone(native_command_latency(action, {**receipt, "nativeAck": {**ack, key: value}}), key)
        self.assertIsNone(native_command_latency(action, {**receipt, "success": False}))
        self.assertIsNone(native_command_latency(action, {**receipt, "error": "callback timed out"}))
        self.assertIsNone(native_command_latency(action, {**receipt, "clock": "server-monotonic"}))
        self.assertIsNone(native_command_latency({**action, "operation": "walk"}, receipt))

    def test_client_issued_receipt_does_not_count_as_completed_roundtrip_in_window(self):
        from types import SimpleNamespace
        from dataclasses import replace
        from scenarios.navigation_workload import collect_window
        class Clock:
            elapsed = 0
            def monotonic(self):
                return self.elapsed
            def sleep(self, seconds):
                self.elapsed += seconds
        clock = Clock()
        pending = set()
        issued = []
        class Driver:
            stream = SimpleNamespace(pending=pending)
            def send(self, kind, data):
                pending.add(data["actionId"])
                issued.append(data)
            def poll(self):
                if not issued:
                    return []
                action = issued.pop(0)
                pending.remove(action["actionId"])
                return [{"type": "action_result", "data": {"actionId": action["actionId"],
                    "clock": "client-monotonic", "completionScope": "client-issued", "clientElapsedNs": 1_000}}]
        class Probe:
            def send(self, *args):
                pass
            def wait(self, *args):
                return {"data": {"phaseElapsedSeconds": 1}}
            def poll(self):
                return []
        selected = replace(case(clients=1), measurement_seconds=1, offered_rate=1)
        with tempfile.TemporaryDirectory() as directory, patch("scenarios.navigation_workload.time", clock):
            window = collect_window(selected, Probe(), Driver(), "MEASURING", Path(directory), ("swp_reader_00",))
        self.assertEqual(1, window["offeredActions"])
        self.assertEqual(0, window["completedActions"])
        self.assertEqual([], window["actionLatencyNs"])
        self.assertEqual(1, window["unacknowledgedActions"])

    def test_short_or_incomplete_windows_cannot_pass(self):
        from scenarios.navigation_workload import repetition_record
        selected = case(repeats=1)
        ticks = [1_000_000] * selected.minimum_samples
        latencies = [1_000] * selected.offered_rate
        short = repetition_record(selected, 29, 59, ticks, selected.offered_rate, selected.offered_rate, latencies, "client-monotonic")
        missing = repetition_record(selected, 30, 60, ticks[:10], selected.offered_rate, selected.offered_rate, latencies, "client-monotonic")
        unacked = repetition_record(selected, 30, 60, ticks, selected.offered_rate, selected.offered_rate - 1, latencies[:-1], "client-monotonic")
        wrong_clock = repetition_record(selected, 30, 60, ticks, selected.offered_rate, selected.offered_rate, latencies, "wall")
        for repetition in (short, missing, unacked, wrong_clock):
            result = CaseResult(selected, Decision(Status.INCONCLUSIVE), observations={"repetitions": [repetition]})
            self.assertNotEqual(Status.PASS, evaluate(result, BUDGETS).status)

    def test_deficient_bot_is_not_labeled_server_saturation(self):
        from scenarios.navigation_workload import delivery_status
        self.assertEqual(Status.INCONCLUSIVE, delivery_status(50, 20, 30, 1_000_000).status)
        self.assertTrue(any("deficient bot" in reason for reason in delivery_status(50, 20, 30, 1_000_000).reasons))
        saturated = delivery_status(50, 20, 0, 40_000_000)
        self.assertEqual(Status.FAIL, saturated.status)
        self.assertTrue(any("saturation" in reason for reason in saturated.reasons))
        slow_generator = delivery_status(50, 20, 30, 40_000_000)
        self.assertEqual(Status.INCONCLUSIVE, slow_generator.status)
        self.assertTrue(any("deficient bot" in reason for reason in slow_generator.reasons))

    def test_failed_cleanup_and_watchdog_survive_fast_ticks(self):
        from reporting import combine_decisions
        timing = Decision(Status.PASS, ())
        cleanup = Decision(Status.FAIL, ("native deletion/cleanup failed",))
        watchdog = Decision(Status.FAIL, ("native watchdog nonresponse reports: 1",))
        combined = combine_decisions((timing, cleanup, watchdog))
        self.assertEqual(Status.FAIL, combined.status)
        self.assertIn("native deletion/cleanup failed", combined.reasons)
        self.assertIn("native watchdog nonresponse reports: 1", combined.reasons)
        missing_timing = combine_decisions((cleanup, Decision(Status.INCONCLUSIVE, ("shortened measurement (smoke only)",))))
        self.assertEqual(Status.FAIL, missing_timing.status)
        self.assertIn("native deletion/cleanup failed", missing_timing.reasons)

    def test_ten_owner_readiness_uses_independent_repetition_roots(self):
        from scenarios.navigation_runner import prepare_repetition
        roots = [prepare_repetition(case(), Path(f"/tmp/navigation-root-{index}"), index) for index in range(3)]
        self.assertEqual([10, 10, 10], [value["clients"] for value in roots])
        self.assertEqual(3, len({value["world"] for value in roots}))
        other = prepare_repetition(case(variant="lifecycle"), Path("/tmp/navigation-root-0"), 0)
        self.assertNotEqual(roots[0]["world"], other["world"])

    def test_known_failure_does_not_skip_the_next_variant(self):
        from scenarios.navigation_runner import continue_after_failure
        first = CaseResult(case(variant="methods"), Decision(Status.FAIL, ("cleanup failed",)))
        second = continue_after_failure(first, case(variant="lifecycle"))
        self.assertEqual("lifecycle", second.variant)
        self.assertEqual(Status.FAIL, first.decision.status)

    def test_interruption_retains_the_failure_and_stops_owned_processes(self):
        from scenarios.navigation_runner import interrupted_result
        stopped = []
        result = interrupted_result(
            case(), Decision(Status.FAIL, ("deletion failed",)), lambda: stopped.append("stopped"))
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertIn("deletion failed", result.decision.reasons)
        self.assertEqual(["stopped"], stopped)
        from scenarios.navigation_runner import retained_after_error
        from scenarios.navigation_workload import tick_p95_ns
        from metrics import summarize
        kept = retained_after_error((Decision(Status.FAIL, ("deletion failed",)),), RuntimeError("later crash"))
        self.assertEqual(Status.FAIL, kept.status)
        self.assertIn("deletion failed", kept.reasons)
        samples = list(range(100))
        self.assertEqual(summarize(samples, "ns")["p95"], tick_p95_ns(samples))
        self.assertEqual(0, tick_p95_ns([]))
        from scenarios.navigation_workload import drain_allowance
        self.assertEqual(5.0, drain_allowance(0))
        self.assertEqual(30.0, drain_allowance(30))
        self.assertEqual(120.0, drain_allowance(256))
        from scenarios.navigation_workload import inflight_limit
        self.assertEqual(1, inflight_limit(()))
        self.assertEqual(10, inflight_limit(tuple(f"swp_reader_{index:02}" for index in range(10))))

    def test_container_timing_targets_the_placed_chest_and_records_cleanup(self):
        from scenarios.inventory_container import chest_position, removal_decision
        from scenarios.navigation_workload import action_schedule
        selected = case(feature="protection", variant="inventory-click-drag-open-close", tier="small", clients=1)
        actions = action_schedule(selected, 1, ("swp_reader_00", "swp_reader_01"), "MEASURING")
        self.assertEqual(selected.offered_rate, len(actions))
        self.assertEqual(chest_position(0), {key: actions[0][key] for key in ("x", "y", "z")})
        self.assertEqual(chest_position(1), {key: actions[1][key] for key in ("x", "y", "z")})
        self.assertTrue(all(action.get("hold") is False and action["operation"] == "open-container" for action in actions))
        self.assertEqual(Status.PASS, removal_decision({"present": False, "player": "swp_reader_00"}).status)
        self.assertEqual(Status.BLOCKED, removal_decision({}).status)
        self.assertEqual(Status.FAIL, removal_decision({"present": True, "player": "swp_reader_00"}).status)

    def test_passing_container_behavior_is_recorded_before_timing(self):
        result, events = self._run_container_fixture()
        self.assertTrue(result.assertions["repeat-1-behavior"])
        self.assertEqual(1, events.count("removed"))
        self.assertNotIn("assertions", " ".join(result.decision.reasons))

    def test_profiled_runner_keeps_jfr_and_native_quiescence_failure(self):
        result, events = self._run_container_fixture(profile=True)
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertIn("retained tick counter", result.decision.reasons)
        self.assertEqual(1, events.count("profile-start"))
        self.assertEqual(1, events.count("profile-stop"))
        self.assertLess(events.index("profile-start"), events.index("window"))
        self.assertLess(events.index("profile-stop"), events.index("server-stop"))
        self.assertLess(events.index("quiescence"), events.index("server-stop"))
        self.assertFalse(result.assertions["repeat-1-quiescence"])
        self.assertEqual(1, len(result.paths["profiles"]))
        self.assertEqual(1, len(result.paths["quiescence"]))
        self.assertEqual(1, events.count("bot-close"))
        self.assertNotIn("bot-abort", events)

    def test_profiled_runner_stops_capture_and_processes_after_window_error(self):
        result, events = self._run_container_fixture(profile=True, window_error=RuntimeError("window crashed"))
        self.assertEqual(Status.BLOCKED, result.decision.status)
        self.assertIn("window crashed", result.decision.reasons)
        self.assertEqual(1, events.count("profile-stop"))
        self.assertLess(events.index("profile-stop"), events.index("server-stop"))
        self.assertIn("bot-abort", events)

    def test_interrupted_profiled_runner_keeps_observed_defect_in_partial_result(self):
        result, events = self._run_container_fixture(profile=True, window_error=KeyboardInterrupt(), behavior_failure=True)
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertIn("observed protection defect", result.decision.reasons)
        self.assertIn("profile-stop", events)
        self.assertIn("server-stop", events)

    def test_completed_slow_measurement_survives_interrupted_disconnect(self):
        result, _ = self._run_container_fixture(tick_ns=50_000_000, disconnect_error=KeyboardInterrupt(), tier="medium")
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertIn("repeat 1: native tick budget exceeded", result.decision.reasons)

    def test_completed_slow_measurement_survives_failed_disconnect(self):
        result, _ = self._run_container_fixture(tick_ns=50_000_000, disconnect_error=RuntimeError("disconnect failed"), tier="medium")
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertIn("repeat 1: native tick budget exceeded", result.decision.reasons)
        self.assertIn("disconnect failed", result.decision.reasons)

    def test_profile_dumped_only_on_server_exit_is_in_result_paths(self):
        result, _ = self._run_container_fixture(profile=True, dump_on_exit=True)
        self.assertEqual(1, len(result.paths["profiles"]))

    def _run_container_fixture(self, profile=False, window_error=None, behavior_failure=False,
            tick_ns=1_000_000, disconnect_error=None, tier="small", dump_on_exit=False):
        import tempfile
        from contextlib import ExitStack
        from types import SimpleNamespace
        from artifacts import sha256
        from contracts import atomic_write
        from controller import create_root
        from scenarios.navigation_runner import run_case

        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory).resolve()
            root = create_root(base / "run")
            prepared = base / "prepared"
            (prepared / "frozen").mkdir(parents=True)
            atomic_write(prepared / "baseline.json", {"schemaVersion": 1, "recipeSha256": "a" * 64})
            atomic_write(root / "prepared.json", {"schemaVersion": 1, "profile": profile,
                "worlds": {f"navigation-terrain-{tier}": str(prepared)}})
            atomic_write(root / "budgets.json", {"schemaVersion": 1, "budgets": {"backend-medium": {"p95MsExclusive": 40, "p99MsExclusive": 50}}})
            binary = base / "bot"
            binary.write_bytes(b"locked bot")
            runtime = SimpleNamespace(id="paper-1.21.11", artifact="release.jar", bot={"path": str(binary), "sha256": sha256(binary)},
                probe={"path": "unused", "sha256": "b" * 64}, server_sha256="c" * 64, java_sha256="d" * 64, java="/java")
            artifact = SimpleNamespace(name="release.jar", sha256="e" * 64, revision="original", provenance="published")
            cleaned = []

            class Probe:
                def send(self, operation, payload=None):
                    pass
                def wait(self, kind, timeout=60):
                    if kind == "players.snapshot":
                        return {"data": {"players": [{"name": "swp_reader_00"}]}}
                    return {"data": {}}

            class Driver:
                def __init__(self, *args):
                    self.closed = False
                def send(self, *args):
                    pass
                def wait(self, kind, timeout=60):
                    return {"bots": [{"name": "swp_reader_00"}]}
                def abort(self):
                    if self.closed:
                        raise ValueError("refusing to stop an unowned process")
                    cleaned.append("bot-abort")
                    self.closed = True
                def close(self):
                    if self.closed:
                        raise ValueError("refusing to stop an unowned process")
                    cleaned.append("bot-close")
                    self.closed = True

            def boot(runtime, server, *args):
                log = Path(server) / "native.log"
                log.write_text("[00:00:00 INFO]: native server ready\n")
                return SimpleNamespace(log=log), Probe()

            def exercise(*args):
                decision = Decision(Status.FAIL, ("observed protection defect",)) if behavior_failure else Decision(Status.PASS)
                return {"decisions": (decision,), "paths": {}, "chestsArmed": True}

            def window(*args, **kwargs):
                cleaned.append("window")
                if window_error:
                    raise window_error
                return {"seconds": 60, "tickNs": [tick_ns] * 100, "offeredActions": 10, "unacknowledgedActions": 0,
                    "completedActions": 10, "driverPending": 0, "actionLatencyNs": [1_000] * 10}

            def stop_server(*args):
                cleaned.append("server-stop")
                if dump_on_exit:
                    output = next((root / "attempts").glob("*/profiles")) / "server.jfr"
                    output.write_bytes(b"dump on exit")

            replacements = {
                "load_runtimes": lambda *args: [runtime], "load_release": lambda *args: [artifact],
                "verify_evidence": lambda *args: None, "verify_artifact": lambda *args: None,
                "_matches": lambda *args: True, "install_server": lambda *args: 25565,
                "clone_baseline": lambda *args: None, "boot": boot, "BotDriver": Driver,
                "validate_ready": lambda *args: Decision(Status.PASS), "stop_owned": stop_server,
                "exercise_inventory_container": exercise, "finish_container_cleanup": lambda *args: cleaned.append("removed") or (Decision(Status.PASS),),
                "collect_window": window,
            }
            selected = case(feature="protection", variant="inventory-click-drag-open-close", tier=tier, clients=1, repeats=1)
            with ExitStack() as stack:
                for name, replacement in replacements.items():
                    stack.enter_context(patch("scenarios.navigation_runner." + name, replacement))
                if profile:
                    def start(*args):
                        cleaned.append("profile-start")
                        return Decision(Status.PASS)
                    def stop(process, java, output):
                        cleaned.append("profile-stop")
                        output.parent.mkdir(parents=True, exist_ok=True)
                        if dump_on_exit:
                            return Decision(Status.BLOCKED, ("profile stop timeout",))
                        output.write_bytes(b"native profile")
                        return Decision(Status.PASS)
                    stack.enter_context(patch("scenarios.navigation_diagnostics.start_jfr", start))
                    stack.enter_context(patch("scenarios.navigation_diagnostics.stop_jfr", stop))
                def disconnect(probe, close, output):
                    cleaned.append("quiescence")
                    if disconnect_error:
                        raise disconnect_error
                    close()
                    return {"decisions": (Decision(Status.FAIL, ("retained tick counter",)),),
                        "paths": {"quiescence": str(base / "quiescence.json")}}
                stack.enter_context(patch("scenarios.navigation_runner.disconnect_and_observe", disconnect, create=True))
                if isinstance(window_error, KeyboardInterrupt) or isinstance(disconnect_error, KeyboardInterrupt):
                    from contracts import load_result
                    with self.assertRaises(KeyboardInterrupt):
                        run_case(selected, root)
                    result = load_result(next((root / "results").glob("interrupted-*.json")))
                else:
                    result = run_case(selected, root)
            return result, cleaned

    def test_navigation_families_require_prepared_worlds(self):
        from preparation import requires_world
        for feature in ("navigation", "map-terrain", "protection"):
            self.assertTrue(requires_world(case(feature)))
        idle = CaseSpec("idle", "paper-1.21.11", "idle", "empty", "small", "audit", "idle", (), (), clients=0)
        self.assertTrue(requires_world(idle))
