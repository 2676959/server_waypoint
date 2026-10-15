import json
from pathlib import Path
import shutil
import tempfile
import unittest

from persistence import verify_store


class PersistenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        shutil.copytree(Path(__file__).parent / "fixtures", self.root, dirs_exist_ok=True)

    def change_nether(self, update):
        path = self.root / "minecraft$the_nether.json"
        value = json.loads(path.read_text())
        update(value[0]["waypoints"])
        path.write_text(json.dumps(value))

    def test_recorded_live_waypoints_pass(self):
        verify_store(self.root, True)

    def test_wrong_dimension_edit_is_rejected(self):
        self.change_nether(lambda waypoints: waypoints[0]["pos"].__setitem__(1, 76))
        with self.assertRaisesRegex(AssertionError, "owning dimension"):
            verify_store(self.root, True)

    def test_metadata_loss_is_rejected(self):
        self.change_nether(lambda waypoints: waypoints[0].pop("icon"))
        with self.assertRaises((AssertionError, KeyError)):
            verify_store(self.root, True)

    def test_uncleared_metadata_is_rejected(self):
        self.change_nether(lambda waypoints: waypoints[1].__setitem__("icon", "minecraft:diamond"))
        with self.assertRaisesRegex(AssertionError, "clearing"):
            verify_store(self.root, True)

    def test_wrong_add_coordinates_are_rejected(self):
        self.change_nether(lambda waypoints: waypoints[2]["pos"].__setitem__(0, 18))
        with self.assertRaisesRegex(AssertionError, "Added waypoint"):
            verify_store(self.root, True)

    def test_duplicate_waypoints_are_rejected(self):
        self.change_nether(lambda waypoints: waypoints.append(dict(waypoints[0])))
        with self.assertRaisesRegex(AssertionError, "duplicated"):
            verify_store(self.root, True)

    def test_unexpected_waypoints_are_rejected(self):
        self.change_nether(lambda waypoints: waypoints.append({"name": "stale-target"}))
        with self.assertRaisesRegex(AssertionError, "unexpected"):
            verify_store(self.root, True)
