import base64
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from models import Status


class PreparedTerrainTest(unittest.TestCase):
    def test_patterns_are_independent_and_distinguish_owner_and_region(self):
        from bots.minimal_terrain import expected_pixels
        patterns = [expected_pixels(owner, region) for owner in (0, 1) for region in ("a", "b")]
        self.assertTrue(all(len(p) == 16384 for p in patterns))
        self.assertEqual(4, len(set(patterns)))
        self.assertEqual(bytes([5, 6, 7, 8]), patterns[0][:4])
        self.assertEqual(bytes([9, 10, 11, 12]), patterns[1][:4])
        self.assertEqual(bytes([13, 14, 15, 16]), patterns[2][:4])

    def test_wrong_native_pixels_fail_even_when_client_agrees(self):
        from bots.minimal_terrain import judge_owner, REGIONS, expected_pixels
        expected = expected_pixels(0, "a")
        wrong = bytes([1]) * 16384
        state = {"id": 7, **REGIONS["a"], "colorsBase64": base64.b64encode(wrong).decode()}
        player = {"uuid": "owner", "methods": ["map"], "target": {"dimension": "minecraft:overworld", "list": "terrain_fixture", "waypoint": "a"},
                  "items": [{"method": "map", "amount": 1, "mapState": state}]}
        import zlib
        client = {"maps": [{"id": 7, "scale": 2, "colorsBase64": base64.b64encode(wrong).decode(), "colorsCrc32": zlib.crc32(wrong), "pixelCount": 16384}]}
        self.assertEqual(Status.FAIL, judge_owner("owner", 7, "a", expected, player, client).status)

    def test_missing_owner_or_client_map_is_blocked(self):
        from bots.minimal_terrain import judge_owner, expected_pixels
        self.assertEqual(Status.BLOCKED, judge_owner("owner", 7, "a", expected_pixels(0, "a"), None, {}).status)
