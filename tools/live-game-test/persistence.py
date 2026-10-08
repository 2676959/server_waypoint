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


def verify_default_set(game):
    """Validate the default-set scenario against config, native Xaero and server files."""
    game = Path(game)
    config = json.loads((game / "config/server_waypoint/client-config.json").read_text())
    if config.get("xaeroDefaultListDirectSync") is not True:
        raise AssertionError("Saved direct-sync setting is missing or not enabled")
    native = game / "xaero/minimap/live-editor-verification/dim%0/waypoints.txt"
    lines = native.read_text().splitlines()
    sets = next((line.split(":")[1:] for line in lines if line.startswith("sets:")), [])
    if "gui.xaero_default" not in sets:
        raise AssertionError("Native default set was deleted")
    if "sw␟gui.xaero_default" in sets:
        raise AssertionError("Stale owned default copy persisted")
    waypoints = [line.split(":") for line in lines if line.startswith("waypoint:")]
    if any(len(waypoint) < 10 for waypoint in waypoints):
        raise AssertionError("Malformed native Xaero waypoint record")
    defaults = [waypoint[1] for waypoint in waypoints if waypoint[9] == "gui.xaero_default"]
    if defaults != ["personal-after"]:
        raise AssertionError("Saved personal default waypoint was lost, duplicated or mixed with server markers")
    default_record = next(waypoint for waypoint in waypoints if waypoint[9] == "gui.xaero_default")
    if default_record[3:6] != ["12", "64", "13"]:
        raise AssertionError("Saved personal default coordinates changed")
    other = [waypoint[1] for waypoint in waypoints if waypoint[9] == "Default-test-personal"]
    if "Default-test-personal" not in sets or other != ["other-personal"]:
        raise AssertionError("Other personal set was modified")
    other_record = next(waypoint for waypoint in waypoints if waypoint[9] == "Default-test-personal")
    if other_record[3:6] != ["9", "64", "10"]:
        raise AssertionError("Saved other personal coordinates changed")
    server = game / "saves/live-editor-verification/server_waypoint/waypoints/minecraft$overworld.json"
    lists = json.loads(server.read_text())
    if any(value["list_name"] == "gui.xaero_default" for value in lists):
        raise AssertionError("Removed server default list persisted")
