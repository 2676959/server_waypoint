"""Actual death/automatic respawn and cross-world navigation lifecycle observations."""
import argparse
from collections import Counter
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
from scenarios.navigation import METHODS, restored_session, death_protection
from worlds import install_server, boot
from navigation_smoke import execute, snapshot


def run(root, count, fixture_only=False):
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
    point = {"name": "target", "display_name": "Lifecycle", "initials": "L", "pos": [320, -61, 320],
             "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": "Lifecycle fixture"}
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "lifecycle_fixture", "n": 7, "waypoints": [point]}], indent=4) + "\n")
    evidence = {"schemaVersion": 1, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"],
                "botSha256": runtime.bot["sha256"], "count": count, "steps": [], "scope": "native-death-respawn-and-world-change"}
    source_paths = (Path(__file__).resolve(), tools / "bots/navigation_smoke.py", tools / "scenarios/navigation.py", tools / "bot_driver.py", tools / "worlds.py")
    atomic_write(root / "inputs.json", {"schemaVersion": 1, "files": {str(path.relative_to(tools)): sha256(path) for path in source_paths}})
    for path in source_paths:
        target = root / "controller-inputs" / path.relative_to(tools)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
    process, probe = boot(runtime, server, False, artifact.sha256)
    driver = None
    try:
        probe.send("lifecycle.arm")
        probe.wait("lifecycle.armed")
        probe.send("lifecycle.prepare-nether")
        evidence["netherFixture"] = probe.wait("lifecycle.nether.prepared")["data"]
        if evidence["netherFixture"].get("verified") is not True or evidence["netherFixture"].get("padBlocks") != 625:
            raise ValueError("native Nether landing fixture verification missing")
        if fixture_only:
            return
        probe.send("commands.tree")
        probe.wait("commands.tree")
        execute(probe, "difficulty peaceful")
        execute(probe, "gamerule keep_inventory false")
        driver = BotDriver(runtime.bot["path"], create_root(root / "bots"), probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 150)
        value = snapshot(probe)
        if validate_ready(ready, value["players"], count, "vanilla").status != Status.PASS:
            raise ValueError("native lifecycle actor count/identity mismatch")
        names = sorted(player["name"] for player in value["players"])
        identities = {player["name"]: player["uuid"] for player in value["players"]}
        serial = 0

        def actions(command=None, slots=None):
            nonlocal serial
            for name in names:
                serial += 1
                action = {"actionId": f"lifecycle-{serial}", "bot": name}
                action.update({"operation": "command", "command": command, "acknowledged": True} if command is not None else {"operation": "select", "slot": slots[name]})
                driver.send("action", action)
            return [driver.wait("action_result", 30) for _ in names]

        def packets():
            driver.send("barrier")
            value = driver.wait("packet_summary", 30)
            rate = driver.wait("rate_sample", 30)
            if rate.get("pending") or rate.get("failed"):
                raise ValueError("native lifecycle action barrier incomplete")
            return value["effects"]

        expected_target = {"dimension": "minecraft:overworld", "list": "lifecycle_fixture", "waypoint": "target", "displayName": "Lifecycle", "position": [320, -61, 320], "rgb": 0x112233,
                           "expectedMap": {"centerX": 512, "centerZ": 512, "scale": 2, "cursorX": -96, "cursorZ": -96}}
        ids = {}

        def observe(dimension, changed_from=None):
            deadline = time.monotonic() + 20
            while True:
                native = snapshot(probe)
                client = packets()
                decisions = {}
                for player in native["players"]:
                    name = player["name"]
                    if player["dimension"] != dimension or player.get("dead", True) or player.get("health", 0) <= 0:
                        continue
                    maps = [item for item in player["items"] if item["method"] == "map"]
                    if len(maps) != 1:
                        continue
                    ids.setdefault(name, maps[0]["mapState"]["id"])
                    decision = restored_session({"uuid": identities[name], "mapId": ids[name], "methods": METHODS, "target": expected_target}, player, client[name])
                    if changed_from is not None:
                        previous = next(value for value in changed_from["players"] if value["name"] == name)
                        if player.get("textDisplayId") == previous.get("textDisplayId"):
                            continue
                    if decision.status == Status.PASS:
                        decisions[name] = decision
                if len(decisions) == count:
                    return {"native": native, "client": client, "decisions": decisions}
                if time.monotonic() >= deadline:
                    atomic_write(root / "lifecycle-mismatch.json", {"schemaVersion": 1, "dimension": dimension, "native": native, "client": client, "matched": list(decisions)})
                    raise ValueError("native lifecycle target/method/item/display deadline")
                time.sleep(0.1)

        def record(label, value):
            evidence["steps"].append({"label": label, **value})
            atomic_write(root / "lifecycle.json", evidence)

        for name in names:
            execute(probe, "op " + name)
            execute(probe, f"tp {name} 300 -61 300")
            execute(probe, f"give {name} minecraft:stone 64")
            execute(probe, f"give {name} minecraft:diamond 3")
        actions("/wp help")
        actions("/wp navigate minecraft:overworld lifecycle_fixture target all")
        value = snapshot(probe)
        actions(slots={player["name"]: next(item["slot"] for item in player["items"] if item["method"] == "map") for player in value["players"]})
        initial = observe("minecraft:overworld")
        record("before-death", initial)
        ordinary = Counter()
        for player in initial["native"]["players"]:
            stacks = player["ordinary"]
            if sum(value for key, value in stacks.items() if key.startswith("minecraft:stone:")) != 64 or sum(value for key, value in stacks.items() if key.startswith("minecraft:diamond:")) != 3 or sum(stacks.values()) != 67:
                raise ValueError("independent ordinary death fixture missing")
            ordinary.update(stacks)
        for name in names:
            execute(probe, "kill " + name)
        respawned = observe("minecraft:overworld")
        record("actual-death-and-automatic-respawn", respawned)
        probe.send("lifecycle.snapshot")
        observations = probe.wait("lifecycle.snapshot")["data"]
        deaths = [value for value in observations["events"] if value["event"] == "death"]
        respawns = [value for value in observations["events"] if value["event"] == "respawn"]
        if observations["droppedObservations"] or len(deaths) != count or {value["uuid"] for value in deaths} != set(identities.values()) or {value["uuid"] for value in respawns} != set(identities.values()) or any(value["keepInventory"] for value in deaths):
            raise ValueError("actual death/respawn/drop receipts incomplete")
        drops = {"ordinary": Counter(), "protectedItems": 0, "nestedProtectedCarriers": 0}
        for value in deaths:
            drops["ordinary"].update(value["drops"]["ordinary"])
            for key in ("protectedItems", "nestedProtectedCarriers"):
                drops[key] += value["drops"][key]
        # Account for an ordinary item already picked up after respawn as well
        # as still-present item entities; neither form may disappear.
        world = dict(observations["worldItems"])
        world["ordinary"] = Counter(world["ordinary"])
        for player in respawned["native"]["players"]:
            world["ordinary"].update(player["ordinary"])
        decision = death_protection(dict(ordinary), drops, world)
        record("death-drop-conservation", {"observations": observations, "decision": decision})
        if decision.status != Status.PASS:
            raise ValueError("native death drops/protected world-item conservation differs")
        for index, name in enumerate(names):
            x = (index % 5) * 3 - 6 + 0.5
            z = (index // 5) * 3 - 3 + 0.5
            execute(probe, f"execute in minecraft:the_nether run tp {name} {x} 79 {z}")
        nether = observe("minecraft:the_nether", respawned["native"])
        record("actual-nether-world-change", nether)
        for name in names:
            execute(probe, f"execute in minecraft:overworld run tp {name} 300 -61 300")
        returned = observe("minecraft:overworld", nether["native"])
        record("actual-overworld-return", returned)
        probe.send("lifecycle.snapshot")
        observations = probe.wait("lifecycle.snapshot")["data"]
        changes = [value for value in observations["events"] if value["event"] == "world-change"]
        for uuid in identities.values():
            if [(value["from"], value["to"]) for value in changes if value["uuid"] == uuid] != [("minecraft:overworld", "minecraft:the_nether"), ("minecraft:the_nether", "minecraft:overworld")]:
                raise ValueError("native actual world-change receipts differ")
        evidence["observations"] = observations
        driver.close()
        driver = None
        evidence["completed"] = True
    except BaseException as error:
        evidence["error"] = str(error)
        try:
            probe.send("lifecycle.snapshot")
            evidence["failureObservations"] = probe.wait("lifecycle.snapshot", 10)["data"]
        except BaseException as observation_error:
            evidence["observationError"] = str(observation_error)
        raise
    finally:
        try:
            if driver is not None:
                driver.abort()
        finally:
            try:
                stop_owned(process, 30)
            finally:
                atomic_write(root / "lifecycle.json", evidence)
    print(f"native {count}-owner death/respawn, ordinary drops and cross-world navigation verified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--count", type=int, choices=(1, 10), default=1)
    parser.add_argument("--fixture-only", action="store_true")
    args = parser.parse_args()
    run(args.root, args.count, args.fixture_only)
