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

    def test_navigation_families_require_prepared_worlds(self):
        from preparation import requires_world
        for feature in ("navigation", "map-terrain", "protection"):
            self.assertTrue(requires_world(case(feature)))
        idle = CaseSpec("idle", "paper-1.21.11", "idle", "empty", "small", "audit", "idle", (), (), clients=0)
        self.assertTrue(requires_world(idle))
