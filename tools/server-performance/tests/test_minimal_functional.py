import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "bots"))
import minimal_functional as minimal
from models import Status

class MinimalFunctionalTests(unittest.TestCase):
    def test_missing_required_observation_blocks_completion(self):
        result = minimal.summarize("navigation", [{"name":"methods", "status":"PASS", "reasons":[]}])
        self.assertEqual(result["status"], "BLOCKED")
        self.assertFalse(result["completed"])

    def test_complete_observed_failure_retains_fail(self):
        checks = [{"name": name, "status":"FAIL" if name == "cleanup" else "PASS", "reasons":["leak"] if name == "cleanup" else []} for name in minimal.REQUIRED["navigation"]]
        result = minimal.summarize("navigation", checks)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(result["completed"])

    def test_binding_does_not_rehash_tools_or_load_historical_inputs(self):
        runtime, artifact, release = object(), object(), Path("candidate.jar")
        self.assertEqual(minimal.bound_inputs((runtime, artifact, release)), (runtime, artifact, release))

    def test_all_methods_selected_once(self):
        self.assertEqual(minimal.selections(["map", "compass" ]), [("compass", "map")])

    def test_unknown_variant_rejected_before_native_work(self):
        with self.assertRaises(ValueError):
            minimal.run("unknown", Path("unused"), None, None)

    def test_duplicate_names_cannot_complete_coverage(self):
        checks = [{"name":name, "status":"PASS", "reasons":[]} for name in minimal.REQUIRED["storage"]]
        result = minimal.summarize("storage", checks + [dict(checks[0])])
        self.assertEqual(result["status"], "BLOCKED")
        self.assertFalse(result["completed"])

    def test_typed_observed_failure_is_distinct_from_setup_error(self):
        import tempfile
        from contracts import atomic_write
        from models import Decision, Status
        for error, wanted in ((RuntimeError("startup crashed"), "BLOCKED"),
                              (minimal.NativeAssertion("regions", Decision(Status.FAIL, ("pixels differ",))), "FAIL")):
            with self.subTest(wanted=wanted), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                @minimal.minimal_adapter("terrain", "terrain.json")
                def adapter(root, minimal=False):
                    atomic_write(root / "terrain.json", {"schemaVersion": 1, "checks": []})
                    raise error
                result = adapter(root, minimal=True)
                self.assertEqual(wanted, result["status"])

    def test_world_marker_requires_both_native_and_client_parts(self):
        from scenarios.navigation import world_marker_effect
        native = {"worldMarker": {"displays": [
            {"part": "badge", "entityId": 10, "text": "A", "inWorld": False},
            {"part": "distance", "entityId": 11, "text": "100m", "inWorld": False}]}}
        client = {"textDisplays": {"10": {"text": "A"}, "11": {"text": "100m"}}}
        self.assertEqual(Status.PASS, world_marker_effect(native, client).status)
        client["textDisplays"]["10"]["text"] = "B"
        self.assertEqual(Status.FAIL, world_marker_effect(native, client).status)
        del client["textDisplays"]["10"]
        self.assertEqual(Status.BLOCKED, world_marker_effect(native, client).status)

    def test_minimal_mutations_ignore_unselected_new_command_leaves(self):
        from minimal_storage import select_mutations
        paths = ["wp add <dimension> <list identifier> <position> <waypoint identifier> <initials> <color> <yaw> <global>",
                 "wp edit waypoint <dimension> <list identifier> <waypoint identifier> set color <color>",
                 "wp remove <dimension> <list identifier> <waypoint identifier>", "wp restore <token>",
                 "wp add <unmapped new argument>"]
        cases = select_mutations({"origin": "native-minecraft-command-dispatcher", "nodes": [{"path": p, "executable": True} for p in paths]})
        self.assertEqual(4, len(cases))
        self.assertEqual("wp add minecraft:overworld mutation_00 12 -61 -16 origin P 112233 30 true", cases[0]["command"])

    def test_saved_state_mismatch_is_a_native_failure_not_setup_error(self):
        from minimal_storage import check_exact_state
        expected = {"minecraft:overworld": [{"list_name": "fixture", "n": 1, "waypoints": []}]}
        actual = {"minecraft:overworld": [{"list_name": "fixture", "n": 2, "waypoints": []}]}
        with self.assertRaises(minimal.NativeAssertion) as raised:
            check_exact_state("save", expected, actual)
        self.assertEqual(Status.FAIL, raised.exception.decision.status)
        self.assertEqual("save", raised.exception.name)
        check_exact_state("reopen", expected, expected)

    def test_observed_mutation_timeout_retains_typed_mismatch(self):
        from scenarios.command_behavior import eventually, NativeStateMismatch
        with self.assertRaises(NativeStateMismatch):
            eventually(lambda: False, "native fields differ", timeout=0, timeout_error=NativeStateMismatch)
        with self.assertRaises(KeyError):
            eventually(lambda: {}["missing-observation"], "missing", timeout=0, timeout_error=NativeStateMismatch)

    def test_observation_deadline_preserves_missing_receipt_and_observed_mismatch(self):
        from models import Decision
        self.assertEqual(Status.BLOCKED, minimal.observation_deadline([Decision(Status.BLOCKED, ("client receipt missing",))], 1, 1).status)
        self.assertEqual(Status.FAIL, minimal.observation_deadline([Decision(Status.FAIL, ("target differs",)), Decision(Status.BLOCKED, ("client receipt missing",))], 1, 1).status)
        self.assertEqual(Status.BLOCKED, minimal.observation_deadline([], 0, 1).status)
