import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bots import world_marker_smoke as smoke


def configured_player():
    # Hand-derived: block centre (20.5, .5, .5), eye (.5, .5, .5), radius 8.5.
    return {
        "name": "owner",
        "target": {"position": [20, 0, 0]},
        "worldMarker": {
            "sampleEye": [0.5, 0.5, 0.5],
            "observedVelocity": [0, 0, 0],
            "displays": [
                {"part": "badge", "entityId": 101, "position": [9, 0.5, 0.5],
                 "translation": [0, 0.525, 0], "scale": [1.75, 1.75, 1.75],
                 "inWorld": False, "billboard": "CENTER", "teleportDuration": 1},
                {"part": "distance", "entityId": 102, "position": [9, 0.5, 0.5],
                 "translation": [0, 0, 0], "scale": [1.4, 1.4, 1.4],
                 "inWorld": False, "billboard": "CENTER", "teleportDuration": 1},
                {"part": "item", "entityId": 103, "position": [9, 0.5, 0.5],
                 "translation": [0, 1.4, 0], "scale": [0.7, 0.7, 0.7],
                 "inWorld": False, "billboard": "CENTER", "teleportDuration": 1},
            ],
        },
    }


class WorldMarkerConfigurationTest(unittest.TestCase):
    def test_accepts_fractional_size_and_radius_with_stable_part_ids(self):
        smoke.assert_marker_configuration(configured_player(), 1.75, 8.5,
            {"badge": 101, "distance": 102, "item": 103})

    def test_rejects_stale_size_or_wrong_offsets_on_each_row(self):
        for index in range(3):
            for field in ("scale", "translation"):
                with self.subTest(part=index, field=field):
                    player = configured_player()
                    player["worldMarker"]["displays"][index][field][1] += 0.2
                    with self.assertRaises(AssertionError):
                        smoke.assert_marker_configuration(player, 1.75, 8.5)

    def test_rejects_wrong_height_and_uncapped_far_anchor(self):
        for position in ([9, 2, 0.5], [20.5, 0.5, 0.5], [12.5, 0.5, 0.5]):
            with self.subTest(position=position):
                player = configured_player()
                for row in player["worldMarker"]["displays"]:
                    row["position"] = position
                with self.assertRaises(AssertionError):
                    smoke.assert_marker_configuration(player, 1.75, 8.5)

    def test_near_target_stays_at_actual_block_centre(self):
        player = configured_player()
        player["target"]["position"] = [2, 0, 0]
        for row in player["worldMarker"]["displays"]:
            row["position"] = [2.5, 0.5, 0.5]
        smoke.assert_marker_configuration(player, 1.75, 8.5)
        for row in player["worldMarker"]["displays"]:
            row["position"] = [9, 0.5, 0.5]
        with self.assertRaises(AssertionError):
            smoke.assert_marker_configuration(player, 1.75, 8.5)

    def test_rejects_missing_duplicate_or_unexpected_parts(self):
        for parts in ([0, 1], [0, 1, 1], [0, 1, 2, 2]):
            with self.subTest(parts=parts):
                player = configured_player()
                rows = player["worldMarker"]["displays"]
                player["worldMarker"]["displays"] = [copy.deepcopy(rows[i]) for i in parts]
                with self.assertRaises(AssertionError):
                    smoke.assert_marker_configuration(player, 1.75, 8.5)

    def test_rejects_respawned_or_duplicate_ids(self):
        for entity_id in (999, 101):
            with self.subTest(entity_id=entity_id):
                player = configured_player()
                player["worldMarker"]["displays"][2]["entityId"] = entity_id
                with self.assertRaises(AssertionError):
                    smoke.assert_marker_configuration(player, 1.75, 8.5,
                        {"badge": 101, "distance": 102, "item": 103})

    def test_rejects_nonfinite_geometry_and_incomplete_vectors(self):
        for field in ("position", "scale", "translation"):
            for invalid in (float("nan"), float("inf"), float("-inf")):
                with self.subTest(field=field, invalid=invalid):
                    player = configured_player()
                    player["worldMarker"]["displays"][0][field][0] = invalid
                    with self.assertRaises(AssertionError):
                        smoke.assert_marker_configuration(player, 1.75, 8.5)
        player = configured_player()
        player["worldMarker"]["sampleEye"] = [0.5, 0.5]
        with self.assertRaises(AssertionError):
            smoke.assert_marker_configuration(player, 1.75, 8.5)

    def test_rejects_wrong_visibility_interpolation_and_moving_samples(self):
        for key, value in (("inWorld", True), ("inWorld", None),
                ("billboard", "FIXED"), ("teleportDuration", 0)):
            with self.subTest(key=key, value=value):
                player = configured_player()
                player["worldMarker"]["displays"][0][key] = value
                with self.assertRaises(AssertionError):
                    smoke.assert_marker_configuration(player, 1.75, 8.5)
        player = configured_player()
        player["worldMarker"]["observedVelocity"] = [0.1, 0, 0]
        with self.assertRaises(AssertionError):
            smoke.assert_marker_configuration(player, 1.75, 8.5)


class WorldMarkerReadbackTest(unittest.TestCase):
    def test_accepts_fresh_fractional_readback(self):
        messages = [{"sequence": 11, "text": "World marker size ×1.75"},
                    {"sequence": 12, "text": "World marker distance: 8.5 blocks"}]
        self.assertEqual(messages, smoke.assert_setting_feedback(messages, 10, 1.75, 8.5))

    def test_rejects_stale_partial_or_incorrect_readback(self):
        for messages in (
                [{"sequence": 9, "text": "World marker size ×1.75"},
                 {"sequence": 10, "text": "World marker distance: 8.5 blocks"}],
                [{"sequence": 11, "text": "World marker size ×1.75"}],
                [{"sequence": 11, "text": "World marker size ×1.0"},
                 {"sequence": 12, "text": "World marker distance: 8.5 blocks"}],
                [{"sequence": 11, "text": "World marker size ×1.75"},
                 {"sequence": 12, "text": "World marker distance: 12.0 blocks"}]):
            with self.subTest(messages=messages), self.assertRaises(AssertionError):
                smoke.assert_setting_feedback(messages, 10, 1.75, 8.5)


if __name__ == "__main__":
    unittest.main()
