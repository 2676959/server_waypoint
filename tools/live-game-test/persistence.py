"""Independent disk validation; a GUI PASS alone is insufficient."""
import json
from pathlib import Path


def verify_store(root, voxelmap):
    data = {}
    for dimension in ("overworld", "the_nether"):
        lists = json.loads(Path(root).joinpath("minecraft$" + dimension + ".json").read_text())
        bases = next(value for value in lists if value["list_name"] == "Bases")
        if bases["display_name"] != "Display Bases":
            raise AssertionError("List display name was not preserved")
        names = [waypoint["name"] for waypoint in bases["waypoints"]]
        expected = {"map-fixture", "voxel-fixture"}
        if dimension == "the_nether":
            expected.add("live-added")
        if len(names) != len(set(names)) or set(names) != expected:
            raise AssertionError("Persisted waypoint names are duplicated, missing or unexpected")
        data[dimension] = {waypoint["name"]: waypoint for waypoint in bases["waypoints"]}
        if "stale-target" in data[dimension]:
            raise AssertionError("Removed stale target persisted")
        for name in ("map-fixture", "voxel-fixture"):
            waypoint = data[dimension][name]
            if not (waypoint["initials"] == "FX" and waypoint["color"] == "#33AA55"
                    and waypoint["global"] is True and waypoint["pos"][::2] == [31, -42]):
                raise AssertionError("Unedited waypoint fields changed")
            if name == "voxel-fixture" and dimension == "the_nether" and voxelmap:
                if not (waypoint["yaw"] == 90 and waypoint["keywords"] == [] and waypoint["description"] == ""
                        and "icon" not in waypoint and "display_name" not in waypoint):
                    raise AssertionError("Explicit metadata clearing did not persist")
            elif not (waypoint["yaw"] == 135 and waypoint["keywords"] == ["alpha", "beta"]
                      and waypoint["description"] == "Preserve full metadata"
                      and waypoint["icon"] == "minecraft:diamond" and waypoint["display_name"] == "Fancy " + name):
                raise AssertionError("Full waypoint metadata was not preserved")
    if not (data["overworld"]["map-fixture"]["pos"][1] == 76 and data["the_nether"]["map-fixture"]["pos"][1] == 77):
        raise AssertionError("Saved wrong owning dimension")
    if any(data[dimension]["voxel-fixture"]["pos"][1] != 75 for dimension in data):
        raise AssertionError("Unedited VoxelMap position changed")
    if "live-added" in data["overworld"] or data["the_nether"]["live-added"]["pos"] != [17, 85, -19]:
        raise AssertionError("Added waypoint persisted in the wrong dimension or position")
