import json
from pathlib import Path
import tempfile
import unittest

import persistence


class DefaultSetPersistenceTests(unittest.TestCase):
    def setUp(self):
        self.verify = persistence.verify_default_set
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.game = Path(self.temp.name)
        self.config = self.game / "config/server_waypoint/client-config.json"
        self.config.parent.mkdir(parents=True)
        self.config.write_text(json.dumps({"xaeroDefaultListDirectSync": True}))
        self.native = self.game / "xaero/minimap/live-editor-verification/dim%0/waypoints.txt"
        self.native.parent.mkdir(parents=True)
        self.native.write_text(
            "sets:gui.xaero_default:sw␟Bases:Default-test-personal\n"
            "#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination\n"
            "waypoint:personal-after:P:12:64:13:12:false:0:gui.xaero_default:false:0:0:false\n"
            "waypoint:other-personal:O:9:64:10:12:false:0:Default-test-personal:false:0:0:false\n")
        self.server = self.game / "saves/live-editor-verification/server_waypoint/waypoints/minecraft$overworld.json"
        self.server.parent.mkdir(parents=True)
        self.server.write_text(json.dumps([{"list_name": "Bases", "waypoints": []}]))

    def test_saved_default_set_and_other_personal_set_pass(self):
        self.verify(self.game)

    def test_unsaved_direct_sync_option_is_rejected(self):
        self.config.write_text(json.dumps({"xaeroDefaultListDirectSync": False}))
        with self.assertRaisesRegex(AssertionError, "direct-sync setting"):
            self.verify(self.game)

    def test_string_true_is_not_a_persisted_boolean(self):
        self.config.write_text(json.dumps({"xaeroDefaultListDirectSync": "true"}))
        with self.assertRaisesRegex(AssertionError, "direct-sync setting"):
            self.verify(self.game)

    def test_deleted_default_set_is_rejected(self):
        self.native.write_text(self.native.read_text().replace("sets:gui.xaero_default:", "sets:"))
        with self.assertRaisesRegex(AssertionError, "default set"):
            self.verify(self.game)

    def test_stale_owned_default_copy_is_rejected(self):
        self.native.write_text(self.native.read_text().replace("sets:gui.xaero_default:", "sets:sw␟gui.xaero_default:gui.xaero_default:"))
        with self.assertRaisesRegex(AssertionError, "owned default copy"):
            self.verify(self.game)

    def test_lost_personal_default_waypoint_is_rejected(self):
        self.native.write_text("\n".join(line for line in self.native.read_text().splitlines()
                                         if not line.startswith("waypoint:personal-after:")))
        with self.assertRaisesRegex(AssertionError, "personal default waypoint"):
            self.verify(self.game)

    def test_server_marker_left_in_default_set_is_rejected(self):
        with self.native.open("a") as stream:
            stream.write("waypoint:sw␟default-live:FX:31:75:-42:8:false:0:gui.xaero_default:true:135:1:false\n")
        with self.assertRaisesRegex(AssertionError, "personal default waypoint"):
            self.verify(self.game)

    def test_duplicate_personal_default_waypoint_is_rejected(self):
        with self.native.open("a") as stream:
            stream.write("waypoint:personal-after:P:12:64:13:12:false:0:gui.xaero_default:false:0:0:false\n")
        with self.assertRaisesRegex(AssertionError, "personal default waypoint"):
            self.verify(self.game)

    def test_other_personal_set_waypoint_loss_is_rejected(self):
        self.native.write_text(self.native.read_text().replace(":Default-test-personal:false", ":gui.xaero_default:false"))
        with self.assertRaises(AssertionError):
            self.verify(self.game)

    def test_server_default_list_left_on_disk_is_rejected(self):
        self.server.write_text(json.dumps([{"list_name": "gui.xaero_default", "waypoints": []}]))
        with self.assertRaisesRegex(AssertionError, "server default list"):
            self.verify(self.game)

    def test_personal_default_coordinates_are_preserved(self):
        self.native.write_text(self.native.read_text().replace("personal-after:P:12:64:13", "personal-after:P:31:75:-42"))
        with self.assertRaisesRegex(AssertionError, "personal default coordinates"):
            self.verify(self.game)

    def test_other_personal_coordinates_are_preserved(self):
        self.native.write_text(self.native.read_text().replace("other-personal:O:9:64:10", "other-personal:O:31:75:-42"))
        with self.assertRaisesRegex(AssertionError, "other personal coordinates"):
            self.verify(self.game)
