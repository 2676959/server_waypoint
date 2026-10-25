"""Native cold/warm region, exact terrain persistence and active-session restoration smoke."""
import argparse
import base64
from dataclasses import replace
import json
from pathlib import Path
import sys
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready
from contracts import atomic_write, load_release, load_runtimes
from controller import create_root
from models import Status
from processes import stop_owned
from scenarios.navigation import METHODS, terrain_roundtrip, cleanup_counts, restored_session
from worlds import install_server, boot
from navigation_smoke import execute, snapshot


REGIONS = {"a": {"dimension": "minecraft:overworld", "centerX": 512, "centerZ": 512, "scale": 2},
           "b": {"dimension": "minecraft:overworld", "centerX": -512, "centerZ": -512, "scale": 2}}


def run(root, count):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])},
                      bot={**runtime.bot, "sha256": sha256(runtime.bot["path"])})
    artifact = next(a for a in load_release(tools / "config/releases/4.0.0.json") if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-m0-audit/artifacts") / artifact.name
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    points = [{"name": name, "display_name": "Terrain " + name.upper(), "initials": name.upper(), "pos": pos,
               "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": "Terrain fixture"}
              for name, pos in (("a", [320, -61, 320]), ("b", [-320, -61, -320]))]
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "terrain_fixture", "n": 7, "waypoints": points}], indent=4) + "\n")
    evidence = {"schemaVersion": 1, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"],
                "botSha256": runtime.bot["sha256"], "count": count, "scope": "same-dimension-terrain-and-session-restoration", "steps": []}
    atomic_write(root / "inputs.json", {"schemaVersion": 1, "files": {str(path.relative_to(tools)): sha256(path) for path in
                 (Path(__file__).resolve(), tools / "bots/navigation_smoke.py", tools / "scenarios/navigation.py", tools / "bot_driver.py", tools / "worlds.py")}})
    process = driver = probe = None
    serial = 0
    names = []
    ids = {}
    identities = {}
    selection = {"map"}

    def record(label, value):
        evidence["steps"].append({"label": label, **value})
        atomic_write(root / "terrain.json", evidence)

    def connect(reopened=False):
        nonlocal driver, names
        probe.send("commands.tree")
        probe.wait("commands.tree")
        bot_root = create_root(root / ("reopened-bots" if reopened else "initial-bots"))
        driver = BotDriver(runtime.bot["path"], bot_root, probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 150)
        value = snapshot(probe)
        if validate_ready(ready, value["players"], count, "vanilla").status != Status.PASS:
            raise ValueError("native terrain actor count/UUID mismatch")
        names = sorted(player["name"] for player in value["players"])
        for player in value["players"]:
            if player["name"] in identities and identities[player["name"]] != player["uuid"]:
                raise ValueError("restored native owner identity changed")
            identities[player["name"]] = player["uuid"]
        for name in names:
            execute(probe, "op " + name)
        actions("/wp help")
        return value

    def actions(command=None, slots=None):
        nonlocal serial
        for name in names:
            serial += 1
            value = {"actionId": f"terrain-{serial}", "bot": name}
            value.update({"operation": "command", "command": command, "acknowledged": True} if command is not None else {"operation": "select", "slot": slots[name]})
            driver.send("action", value)
        return [driver.wait("action_result", 30) for _ in names]

    def packets():
        driver.send("barrier")
        value = driver.wait("packet_summary", 30)
        rate = driver.wait("rate_sample", 30)
        if rate.get("pending") or rate.get("failed"):
            raise ValueError("native terrain actions incomplete")
        return value["effects"]

    def observe(region, expected=None, warm=False, stable=False):
        deadline = time.monotonic() + 20
        previous = None
        unchanged = 0
        while True:
            native = snapshot(probe)
            client = packets()
            colors = {}
            decisions = {}
            for player in native["players"]:
                name = player["name"]
                maps = [item for item in player["items"] if item["method"] == "map"]
                if set(player["methods"]) != selection or len(maps) != 1 or maps[0]["amount"] != 1:
                    continue
                state = maps[0]["mapState"]
                if name in ids and state["id"] != ids[name]:
                    raise ValueError("owner map identity changed across regions/restart")
                if "colorsBase64" not in state:
                    raise ValueError("native terrain byte observation missing")
                actual = base64.b64decode(state["colorsBase64"], validate=True)
                if warm and not any(actual):
                    continue
                received = next((value for value in client[name]["maps"] if value["id"] == state["id"]), {})
                decision = terrain_roundtrip(REGIONS[region], actual if expected is None else expected[name], state, received)
                if decision.status == Status.PASS and selection == METHODS:
                    point = next(point for point in points if point["name"] == region)
                    expected_target = {"dimension": "minecraft:overworld", "list": "terrain_fixture", "waypoint": region,
                                       "displayName": point["display_name"], "position": point["pos"], "rgb": 0x112233,
                                       "expectedMap": {**{key: REGIONS[region][key] for key in ("centerX", "centerZ", "scale")},
                                                       "cursorX": -96 if region == "a" else 96, "cursorZ": -96 if region == "a" else 96}}
                    decision = restored_session({"uuid": identities[name], "mapId": ids[name], "methods": selection, "target": expected_target}, player, client[name])
                if decision.status == Status.PASS:
                    decisions[name] = decision
                    colors[name] = actual
                    ids[name] = state["id"]
            if len(decisions) == count:
                unchanged = unchanged + 1 if colors == previous else 0
                previous = colors
                if not stable or unchanged >= 3:
                    return {"native": native, "client": client, "decisions": decisions}, colors
            if time.monotonic() >= deadline:
                atomic_write(root / "terrain-mismatch.json", {"schemaVersion": 1, "region": region, "native": native, "client": client, "matched": list(decisions)})
                raise ValueError("native exact terrain/client observation deadline")
            time.sleep(0.3)

    def saved_region(region, expected):
        for name in names:
            files = list((server / "world/server_waypoint/navigation-map-terrain" / str(ids[name])).glob("*.json"))
            matches = [json.loads(path.read_text()) for path in files if json.loads(path.read_text())["region"] == REGIONS[region]]
            if len(matches) != 1 or base64.b64decode(matches[0]["colors"], validate=True) != expected[name]:
                raise ValueError("saved independent region metadata/exact terrain differs")

    def disconnect(label):
        nonlocal driver
        driver.close()
        driver = None
        deadline = time.monotonic() + 10
        while True:
            value = snapshot(probe)
            if not value["players"]:
                break
            if time.monotonic() >= deadline:
                raise TimeoutError("native active-session disconnect deadline")
            time.sleep(0.05)
        tick = value["tick"] + 10
        while value["tick"] < tick:
            time.sleep(0.05)
            value = snapshot(probe)
        expected = {"sessions": 0, "tasks": 0, "entities": 0, "bossbars": 0, "items": 0, "mapTargets": 0,
                    "mapCacheEntries": count, "tickCounters": 0, "targetIndexEntries": 0}
        decision = cleanup_counts(expected, value["resources"])
        record(label, {"native": value, "decision": decision})
        if decision.status != Status.PASS:
            raise ValueError("active-session native disconnect cleanup differs")

    try:
        process, probe = boot(runtime, server, False, artifact.sha256)
        execute(probe, "difficulty peaceful")
        connect()
        for name in names:
            execute(probe, f"tp {name} 300 -61 300")
        actions("/wp navigate minecraft:overworld terrain_fixture a map")
        value = snapshot(probe)
        actions(slots={player["name"]: next(item["slot"] for item in player["items"] if item["method"] == "map") for player in value["players"]})
        value, a = observe("a", warm=True, stable=True)
        record("initial-warm-a", value)
        actions("/wp navigate minecraft:overworld terrain_fixture b")
        value, _ = observe("b", expected={name: bytes(16384) for name in names})
        saved_region("a", a)
        record("cold-b-clears-client-and-saves-a", value)
        actions("/wp navigate minecraft:overworld terrain_fixture a")
        value, _ = observe("a", expected=a)
        record("warm-a-restores-exact-pixels", value)
        for name in names:
            execute(probe, f"tp {name} -300 -61 -300")
        actions("/wp navigate minecraft:overworld terrain_fixture b")
        value, b = observe("b", warm=True, stable=True)
        record("explored-b", value)
        for name in names:
            execute(probe, f"tp {name} 300 -61 300")
        actions("/wp navigate minecraft:overworld terrain_fixture a")
        value, _ = observe("a", expected=a)
        saved_region("b", b)
        record("a-before-active-quit", value)
        actions("/wp navigate minecraft:overworld terrain_fixture a all")
        selection = METHODS
        value, _ = observe("a", expected=a)
        record("all-methods-before-active-quit", value)
        disconnect("active-quit")
        stop_owned(process, 30)
        process = None
        files = server / "world/server_waypoint/navigation-map-terrain"
        evidence["savedTerrainFiles"] = {str(path.relative_to(server)): sha256(path) for path in sorted(files.rglob("*.json"))}
        saved_region("a", a)
        saved_region("b", b)
        process, probe = boot(runtime, server, False, artifact.sha256)
        reopened = connect(True)
        # Restored sessions must select the real persisted target and methods,
        # not merely reconstruct a map from a controller-issued new command.
        if any(player.get("target", {}).get("waypoint") != "a" or set(player["methods"]) != METHODS for player in reopened["players"]):
            raise ValueError("native saved navigation session did not restore exact target/methods")
        actions(slots={player["name"]: next(item["slot"] for item in player["items"] if item["method"] == "map") for player in reopened["players"]})
        value, _ = observe("a", expected=a)
        record("restart-native-session-and-a-pixels", value)
        actions("/wp navigate minecraft:overworld terrain_fixture b")
        value, _ = observe("b", expected=b)
        record("restart-b-persistent-terrain", value)
        disconnect("reopened-active-quit")
        evidence["completed"] = True
    except BaseException as error:
        evidence["error"] = str(error)
        raise
    finally:
        if driver is not None:
            driver.abort()
        if process is not None:
            stop_owned(process, 30)
        atomic_write(root / "terrain.json", evidence)
    print(f"native {count}-owner cold/warm terrain bytes, active-session restart and client patches verified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--count", type=int, choices=(1, 10), default=1)
    args = parser.parse_args()
    run(args.root, args.count)
