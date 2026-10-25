import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scenarios.storage_commands import command_coverage, denied_mutation, restore_once, stale_edit, persisted_state, workload_schedule
from models import Status


class StorageCommandsTest(unittest.TestCase):
    def test_each_command_leaf_has_execution_evidence(self):
        required = ("wp help", "wp list <dimension>")
        self.assertEqual(Status.BLOCKED, command_coverage(required, [{"path": "wp help", "serverObserved": True}], ()).status)
        events = [{"path": path, "serverObserved": True, "feedbackObserved": True} for path in required]
        self.assertEqual(Status.PASS, command_coverage(required, events, ()).status)
        self.assertEqual(Status.BLOCKED, command_coverage(required, events, ("wp list <dimension>",)).status)

    def test_denied_mutation_preserves_revision(self):
        before = {"revision": 7, "names": ["origin"]}
        self.assertEqual(Status.PASS, denied_mutation(before, dict(before), False).status)
        self.assertEqual(Status.FAIL, denied_mutation(before, {"revision": 8, "names": []}, False).status)

    def test_restore_is_single_use(self):
        events = [{"token": "token", "success": True}, {"token": "token", "success": False}]
        self.assertEqual(Status.PASS, restore_once(events).status)
        self.assertEqual(Status.FAIL, restore_once([events[0], events[0]]).status)

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

    def test_fixture_counts_revisions_and_dimension_distribution(self):
        from scenarios.storage_commands import make_dataset
        dataset = make_dataset("small")
        self.assertEqual({"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}, set(dataset))
        self.assertEqual(10, sum(len(lists) for lists in dataset.values()))
        self.assertEqual([333, 333, 334], sorted(sum(len(value["waypoints"]) for value in lists) for lists in dataset.values()))
        self.assertTrue(all(value["n"] == 7 for lists in dataset.values() for value in lists))
        skewed = make_dataset("small", skewed=True)
        self.assertEqual(900, max(len(value["waypoints"]) for lists in skewed.values() for value in lists))
