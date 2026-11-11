"""Native cross-dimension map identity and separate per-owner map viewers."""
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
from scenarios.navigation import switched_dimension, separate_map_viewers
from scenarios.navigation_behavior import same_region_viewers, viewer_from_player
from worlds import install_server, boot
from navigation_smoke import execute, snapshot


NETHER = {"dimension": "minecraft:the_nether", "centerX": 0, "centerZ": 0, "scale": 2}
BLANK = bytes(16384)


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
    properties = server / "server.properties"
    properties.write_text(properties.read_text().replace("allow-nether=false", "allow-nether=true"))
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    overworld = {"name": "overworld", "display_name": "Overworld", "initials": "O", "pos": [320, -61, 320],
                 "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": "Dimension fixture"}
    nearby = {"name": "overworld_b", "display_name": "Overworld B", "initials": "B", "pos": [360, -61, 360],
              "color": "#224466", "yaw": 0, "global": True, "keywords": [], "description": "Same-region retarget fixture"}
    nether = {"name": "nether", "display_name": "Nether", "initials": "N", "pos": [0, 80, 0],
              "color": "#332211", "yaw": 0, "global": True, "keywords": [], "description": "Unexplored nether fixture"}
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "dimension_fixture", "n": 7, "waypoints": [overworld, nearby]}], indent=4) + "\n")
    (directory / "minecraft$the_nether.json").write_text(json.dumps([{"list_name": "dimension_fixture", "n": 7, "waypoints": [nether]}], indent=4) + "\n")
    evidence = {"schemaVersion": 1, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"],
                "botSha256": runtime.bot["sha256"], "count": count, "scope": "cross-dimension-terrain-and-separate-viewers", "steps": []}
    paths = (Path(__file__).resolve(), tools / "bots/navigation_smoke.py", tools / "scenarios/navigation.py", tools / "bot_driver.py", tools / "worlds.py")
    atomic_write(root / "inputs.json", {"schemaVersion": 1, "files": {str(path.relative_to(tools)): sha256(path) for path in paths}})
    process = driver = None
    serial = 0
    try:
        process, probe = boot(runtime, server, False, artifact.sha256)
        probe.send("lifecycle.prepare-nether")
        evidence["netherFixture"] = probe.wait("lifecycle.nether.prepared")["data"]
        if evidence["netherFixture"].get("verified") is not True:
            raise ValueError("native nether fixture missing")
        execute(probe, "difficulty peaceful")
        probe.send("commands.tree")
        probe.wait("commands.tree")
        driver = BotDriver(runtime.bot["path"], create_root(root / "bots"), probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 150)
        value = snapshot(probe)
        if validate_ready(ready, value["players"], count, "vanilla").status != Status.PASS:
            raise ValueError("native dimension actor count/UUID mismatch")
        names = sorted(player["name"] for player in value["players"])
        identities = {player["name"]: player["uuid"] for player in value["players"]}
        for name in names:
            execute(probe, "op " + name)
            execute(probe, f"tp {name} 300 -61 300")

        def actions(command=None, slots=None, only=None):
            nonlocal serial
            selected = names if only is None else only
            for name in selected:
                serial += 1
                action = {"actionId": f"dimension-{serial}", "bot": name}
                action.update({"operation": "command", "command": command, "acknowledged": True} if command is not None else {"operation": "select", "slot": slots[name]})
                driver.send("action", action)
            return [driver.wait("action_result", 30) for _ in selected]

        def packets():
            driver.send("barrier")
            value = driver.wait("packet_summary", 30)
            rate = driver.wait("rate_sample", 30)
            if rate.get("pending") or rate.get("failed"):
                raise ValueError("native dimension actions incomplete")
            return value["effects"]

        def map_state(player):
            maps = [item for item in player["items"] if item.get("method") == "map"]
            return maps[0]["mapState"] if len(maps) == 1 and "mapState" in maps[0] else None

        actions("/wp navigate minecraft:overworld dimension_fixture overworld map")
        deadline = time.monotonic() + 20
        ids = {}
        while len(ids) != count:
            native = snapshot(probe)
            for player in native["players"]:
                state = map_state(player)
                if state and state.get("dimension") == "minecraft:overworld" and type(state.get("id")) is int:
                    ids[player["name"]] = state["id"]
            if time.monotonic() >= deadline:
                raise ValueError("native overworld map identity deadline")
            time.sleep(0.2)
        evidence["overworldMapIds"] = ids
        if count >= 2:
            before_native = snapshot(probe)
            before = tuple(viewer_from_player(player) for player in before_native["players"])
            if any(viewer is None for viewer in before):
                raise ValueError("same-region viewer map evidence missing")
            actions("/wp navigate minecraft:overworld dimension_fixture overworld_b map", only=names[:1])
            deadline = time.monotonic() + 20
            while True:
                after_native = snapshot(probe)
                after = tuple(viewer_from_player(player) for player in after_native["players"])
                decision = same_region_viewers(before, None if any(viewer is None for viewer in after) else after)
                if decision.status == Status.PASS:
                    evidence["sameRegionViewers"] = decision
                    break
                if time.monotonic() >= deadline:
                    evidence["sameRegionViewers"] = decision
                    raise ValueError("same-region viewers did not retarget independently: " + "; ".join(decision.reasons))
                time.sleep(0.2)
            viewers = tuple({"uuid": identities[name], "mapId": ids[name], "dimension": "minecraft:overworld"} for name in names)
            viewers_decision = separate_map_viewers(viewers)
            evidence["separateViewers"] = viewers_decision
            if viewers_decision.status != Status.PASS:
                raise ValueError("owners do not have separate navigation maps")
        value = snapshot(probe)
        actions(slots={player["name"]: next(item["slot"] for item in player["items"] if item["method"] == "map") for player in value["players"]})
        actions("/wp navigate minecraft:the_nether dimension_fixture nether map")
        deadline = time.monotonic() + 20
        step = {"region": NETHER}
        while True:
            native = {player["name"]: player for player in snapshot(probe)["players"]}
            client = packets()
            decisions = {}
            for name in names:
                state = map_state(native[name])
                if state is None or "colorsBase64" not in state:
                    continue
                received = next((item for item in client[name]["maps"] if item["id"] == state["id"]), {})
                decisions[name] = switched_dimension(
                    {"dimension": "minecraft:overworld"}, NETHER, ids[name], state["id"], BLANK, state, received)
            step.update(decisions=decisions, maps={name: map_state(native[name]) for name in names})
            if len(decisions) == count and all(value.status == Status.PASS for value in decisions.values()):
                evidence["steps"].append(step)
                evidence["completed"] = True
                atomic_write(root / "dimension.json", evidence)
                break
            if time.monotonic() >= deadline:
                evidence["steps"].append(step)
                atomic_write(root / "dimension.json", evidence)
                reasons = [f"{name}: {'; '.join(value.reasons)}" for name, value in decisions.items() if value.status != Status.PASS]
                raise ValueError("native dimension terrain deadline: " + " | ".join(reasons or ("map observation missing",)))
            time.sleep(0.3)
        print(f"native {count}-owner cross-dimension terrain verified")
    except BaseException as error:
        evidence["error"] = str(error)
        raise
    finally:
        try:
            if driver is not None:
                driver.abort()
        finally:
            try:
                if process is not None:
                    stop_owned(process, 30)
            finally:
                atomic_write(root / "dimension.json", evidence)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--count", type=int, choices=(1, 10), default=1)
    args = parser.parse_args()
    run(args.root, args.count)
