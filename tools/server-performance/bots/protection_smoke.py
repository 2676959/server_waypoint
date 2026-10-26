"""Normal protocol drop/swap/inventory interactions with native protection receipts."""
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
from server_health import native_log_health
from scenarios.navigation import METHODS, physical_receipts, physical_drop_spawned, restored_session, ordinary_conservation, nested_recipes, nested_item_outcome
from worlds import install_server, boot
from navigation_smoke import execute, snapshot


def run(root, count, case_set="physical"):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])}, bot={**runtime.bot, "sha256": sha256(runtime.bot["path"])})
    artifact = next(a for a in load_release(tools / "config/releases/4.0.0.json") if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-m0-audit/artifacts") / artifact.name
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    point = {"name": "target", "display_name": "Protected", "initials": "P", "pos": [320, -61, 320], "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": "Physical protection fixture"}
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "protection_fixture", "n": 7, "waypoints": [point]}], indent=4) + "\n")
    evidence = {"schemaVersion": 1, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"], "botSha256": runtime.bot["sha256"], "count": count, "scope": "physical-drop-swap-inventory", "steps": []}
    if case_set == "nested":
        evidence["scope"] = "native-world-spawn-independent-nested-graph"
    paths = (Path(__file__).resolve(), tools / "bots/navigation_smoke.py", tools / "scenarios/navigation.py", tools / "bot_driver.py", tools / "worlds.py", tools / "server_health.py")
    atomic_write(root / "inputs.json", {"schemaVersion": 1, "files": {str(path.relative_to(tools)): sha256(path) for path in paths}})
    for path in paths:
        target = root / "controller-inputs" / path.relative_to(tools)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
    process, probe = boot(runtime, server, False, artifact.sha256)
    driver = None
    try:
        probe.send("lifecycle.arm")
        probe.wait("lifecycle.armed")
        probe.send("commands.tree")
        probe.wait("commands.tree")
        execute(probe, "difficulty peaceful")
        driver = BotDriver(runtime.bot["path"], create_root(root / "bots"), probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 150)
        initial = snapshot(probe)
        if validate_ready(ready, initial["players"], count, "vanilla").status != Status.PASS:
            raise ValueError("native protection actor count/identity mismatch")
        names = sorted(player["name"] for player in initial["players"])
        identities = {player["name"]: player["uuid"] for player in initial["players"]}
        serial = 0

        def actions(values):
            nonlocal serial
            for name in names:
                serial += 1
                driver.send("action", {"actionId": f"protection-{serial}", "bot": name, **values[name]})
            return [driver.wait("action_result", 30) for _ in names]

        def command(text):
            return actions({name: {"operation": "command", "command": text, "acknowledged": True} for name in names})

        def monitor():
            probe.send("lifecycle.snapshot")
            return probe.wait("lifecycle.snapshot")["data"]

        def packets():
            driver.send("barrier")
            value = driver.wait("packet_summary", 30)
            rate = driver.wait("rate_sample", 30)
            if rate.get("pending") or rate.get("failed"):
                raise ValueError("native protection action barrier incomplete")
            return value["effects"]

        for index, name in enumerate(names):
            execute(probe, "op " + name)
            execute(probe, f"tp {name} {300 + index * 3} -61 300")
            execute(probe, f"give {name} minecraft:stone 64")
            execute(probe, f"give {name} minecraft:diamond 3")
        command("/wp help")
        command("/wp navigate minecraft:overworld protection_fixture target all")
        before = snapshot(probe)
        ordinary = Counter()
        ids = {}
        for player in before["players"]:
            if sum(player["ordinary"].values()) != 67:
                raise ValueError("ordinary fixture count missing")
            ordinary.update(player["ordinary"])
            ids[player["name"]] = next(item["mapState"]["id"] for item in player["items"] if item["method"] == "map")
        ordinary_by_owner = {player["name"]: player["ordinary"] for player in before["players"]}
        expected_target = {"dimension": "minecraft:overworld", "list": "protection_fixture", "waypoint": "target", "displayName": "Protected", "position": [320, -61, 320], "rgb": 0x112233,
                           "expectedMap": {"centerX": 512, "centerZ": 512, "scale": 2, "cursorX": -96, "cursorZ": -96}}

        def slot(method):
            return {player["name"]: next(item["slot"] for item in player["items"] if item["method"] == method) for player in snapshot(probe)["players"]}

        def select(slots):
            results = actions({name: {"operation": "select", "slot": slots[name]} for name in names})
            deadline = time.monotonic() + 8
            while True:
                native = snapshot(probe)
                if len(native["players"]) == count and all(player.get("heldSlot") == slots[player["name"]] for player in native["players"]):
                    return results
                if time.monotonic() >= deadline:
                    raise ValueError("native selected-hotbar confirmation deadline")
                time.sleep(0.05)

        def verify(label, start, required, expected_slots=None, ordinary_drop=False):
            deadline = time.monotonic() + 8
            while True:
                observed = monitor()
                native = snapshot(probe)
                client = packets()
                receipt = physical_receipts(observed["events"][start:], required)
                if receipt.status == Status.PASS and ordinary_drop:
                    receipt = physical_drop_spawned(observed["events"][start:], observed.get("worldEntities", []),
                                                    [{"uuid": identities[name], "material": "minecraft:diamond", "amount": 3} for name in names])
                decisions = {player["name"]: restored_session({"uuid": identities[player["name"]], "mapId": ids[player["name"]], "methods": METHODS, "target": expected_target}, player, client[player["name"]]) for player in native["players"]}
                conserved = Counter(observed["worldItems"]["ordinary"])
                for inventory in observed.get("inventories", {}).values():
                    conserved.update(inventory["ordinary"])
                valid_locations = observed["worldItems"]["protectedItems"] == 0 and observed["worldItems"]["nestedProtectedCarriers"] == 0 and len(observed.get("inventories", {})) == count
                for player in native["players"]:
                    valid_locations &= player.get("dead") is False and player.get("health", 0) > 0 and player["dimension"] == "minecraft:overworld"
                    valid_locations &= all(item["slot"] < 36 or item["slot"] == 40 for item in player["items"])
                    inventory = observed.get("inventories", {}).get(player["name"], {})
                    cursor = inventory.get("cursor", {})
                    valid_locations &= inventory.get("protectedItems") == 2 and inventory.get("nestedProtectedCarriers") == 0 and cursor.get("protectedItems") == 0 and cursor.get("nestedProtectedCarriers") == 0
                    if expected_slots is not None:
                        valid_locations &= next((item["slot"] for item in player["items"] if item["method"] == "compass"), None) == expected_slots[player["name"]]
                valid_locations &= not any(event["event"] in ("death", "respawn", "world-change") for event in observed["events"])
                for event in observed["events"][start:]:
                    if event["event"] == "inventory-close":
                        valid_locations &= all(event[key]["protectedItems"] == 0 and event[key]["nestedProtectedCarriers"] == 0 for key in ("topItems", "cursorItems"))
                conservation = ordinary_conservation(dict(ordinary), dict(conserved))
                value = {"label": label, "native": native, "client": client, "monitor": observed, "receiptDecision": receipt, "ownerDecisions": decisions,
                         "validLocations": valid_locations, "conservation": conservation}
                if observed["droppedObservations"]:
                    raise ValueError("native protection receipts overflowed")
                if receipt.status == Status.PASS and len(decisions) == count and all(value.status == Status.PASS for value in decisions.values()) and valid_locations and conservation.status == Status.PASS:
                    evidence["steps"].append(value)
                    atomic_write(root / "protection.json", evidence)
                    return value
                if time.monotonic() >= deadline:
                    evidence["steps"].append(value)
                    raise ValueError("native protection receipts/state/conservation deadline: " + label)
                time.sleep(0.1)

        if case_set == "nested":
            for recipe in nested_recipes():
                for name in names:
                    probe.send("lifecycle.arm")
                    probe.wait("lifecycle.armed")
                    step = {"recipe": recipe, "player": name}
                    evidence["steps"].append(step)
                    probe.send("protection.fixture.spawn", {"kind": recipe["kind"], "method": recipe["method"], "player": name})
                    spawned = probe.wait("protection.fixture.spawned")["data"]
                    step["spawned"] = spawned
                    atomic_write(root / "protection.json", evidence)
                    probe.send("protection.fixture.snapshot", {"entityUuid": spawned["entityUuid"]})
                    actual = probe.wait("protection.fixture.snapshot")["data"]
                    decision = nested_item_outcome(recipe, actual)
                    step.update(sample=actual, decision=decision)
                    deadline = time.monotonic() + 8
                    while True:
                        native = snapshot(probe)
                        client = packets()
                        owners = {player["name"]: restored_session({"uuid": identities[player["name"]], "mapId": ids[player["name"]], "methods": METHODS, "target": expected_target}, player, client[player["name"]]) for player in native["players"]}
                        ordinary_preserved = all(ordinary_conservation(ordinary_by_owner[player["name"]], player["ordinary"]).status == Status.PASS for player in native["players"])
                        if len(owners) == count and all(value.status == Status.PASS for value in owners.values()) and ordinary_preserved:
                            break
                        if time.monotonic() >= deadline:
                            step.update(native=native, client=client, ownerDecisions=owners, ordinaryPreserved=ordinary_preserved)
                            raise ValueError("native nested fixture changed unrelated owner state")
                        time.sleep(0.1)
                    observed = monitor()
                    if observed["droppedObservations"]:
                        raise ValueError("native nested fixture observation overflow")
                    step.update(native=native, client=client, ownerDecisions=owners, ordinaryPreserved=ordinary_preserved, monitor=observed)
                    probe.send("protection.fixture.remove", {"entityUuid": spawned["entityUuid"]})
                    step["cleanup"] = probe.wait("protection.fixture.removed")["data"]
                    if step["cleanup"].get("worldPresent") is not False or step["cleanup"].get("ownedRemaining") != 0:
                        raise ValueError("owned nested fixture entity cleanup incomplete")
                    atomic_write(root / "protection.json", evidence)
            evidence["allCasesExecuted"] = True
            evidence["nestedOutcomes"] = dict(Counter(step["decision"].status.value for step in evidence["steps"]))
            evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
            if evidence["serverHealth"].status != Status.PASS:
                raise ValueError("native nested fixture server log health failed")
            if any(step["decision"].status != Status.PASS for step in evidence["steps"]):
                raise ValueError("original-release nested item assertions failed or incomplete")
            evidence["completed"] = True
            driver.close()
            driver = None
            print(f"native {count}-owner independent nested item graphs verified")
            return

        for method in ("compass", "map"):
            select(slot(method))
            start = len(monitor()["events"])
            actions({name: {"operation": "drop", "all": True} for name in names})
            verify("protected-drop-" + method, start, [{"event": "drop", "uuid": identities[name], "material": "minecraft:compass" if method == "compass" else "minecraft:filled_map", "amount": 1, "cancelled": True} for name in names])
        held = slot("compass")
        select(held)
        for label in ("protected-swap-to-offhand", "protected-swap-back"):
            start = len(monitor()["events"])
            actions({name: {"operation": "swap"} for name in names})
            verify(label, start, [{"event": "swap", "uuid": identities[name], "cancelled": False} for name in names],
                   expected_slots={name: 40 for name in names} if label.endswith("offhand") else held)
        for label, destination, cancelled in (("protected-own-storage", 9, False), ("protected-armor-denied", 5, True), ("protected-result-denied", 0, True)):
            source = slot("compass")
            start = len(monitor()["events"])
            actions({name: {"operation": "inventory", "from": source[name] + 36 if source[name] < 9 else source[name], "to": destination} for name in names})
            verify(label, start, [{"event": "inventory-click", "uuid": identities[name], "rawSlot": destination, "cancelled": cancelled} for name in names] + [{"event": "inventory-close", "uuid": identities[name]} for name in names],
                   expected_slots={name: 9 for name in names} if label == "protected-own-storage" else None)
        start = len(monitor()["events"])
        actions({name: {"operation": "inventory", "from": 36, "to": 10} for name in names})
        verify("ordinary-storage-control", start, [{"event": "inventory-click", "uuid": identities[name], "rawSlot": 10, "cancelled": False} for name in names] + [{"event": "inventory-close", "uuid": identities[name]} for name in names])
        select({name: 1 for name in names})
        start = len(monitor()["events"])
        actions({name: {"operation": "drop", "all": True} for name in names})
        verify("ordinary-drop-control", start, [{"event": "drop", "uuid": identities[name], "material": "minecraft:diamond", "amount": 3, "cancelled": False} for name in names], ordinary_drop=True)
        evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
        if evidence["serverHealth"].status != Status.PASS:
            raise ValueError("native server stability failed: " + "; ".join(evidence["serverHealth"].reasons))
        evidence["completed"] = True
        driver.close()
        driver = None
    except BaseException as error:
        evidence["error"] = str(error)
        try:
            evidence["failureObservations"] = monitor()
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
                atomic_write(root / "protection.json", evidence)
    print(f"native {count}-owner physical drop/swap/inventory protection and ordinary controls verified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--count", type=int, choices=(1, 10), default=1)
    parser.add_argument("--case-set", choices=("physical", "nested"), default="physical")
    args = parser.parse_args()
    run(args.root, args.count, args.case_set)
