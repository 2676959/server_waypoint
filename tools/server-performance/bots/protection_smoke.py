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
from scenarios.navigation import METHODS, physical_receipts, physical_drop_spawned, restored_session, ordinary_conservation, nested_recipes, nested_item_outcome, container_cleanup, container_cases, container_recipe, drag_cases, drag_outcome, player_raw_slot, station_cases, station_outcome, entity_cases, entity_outcome, deleted_navigation
from worlds import install_server, boot
from navigation_smoke import execute, snapshot


def probe_container(probe, name):
    probe.send("protection.container.snapshot", {"player": name})
    return probe.wait("protection.container.snapshot")["data"]


def run(root, count, case_set="physical", candidate=None):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])}, bot={**runtime.bot, "sha256": sha256(runtime.bot["path"])})
    artifact = next(a for a in load_release(tools / "config/releases/4.0.0.json") if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-m0-audit/artifacts") / artifact.name
    if candidate is not None:
        release = Path(candidate).absolute()
        if release.is_symlink() or not release.is_file():
            raise ValueError("candidate must be a regular non-symlink JAR")
        artifact = replace(artifact, sha256=sha256(release), size=release.stat().st_size,
                           revision="development-candidate", provenance="development-candidate")
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    point = {"name": "target", "display_name": "Protected", "initials": "P", "pos": [320, -61, 320], "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": "Physical protection fixture"}
    points = [point]
    if case_set == "delete":
        points.append({"name": "other", "display_name": "Other", "initials": "O", "pos": [64, -61, 64], "color": "#332211", "yaw": 0, "global": True, "keywords": [], "description": "Unaffected navigation target"})
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "protection_fixture", "n": 7, "waypoints": points}], indent=4) + "\n")
    evidence = {"schemaVersion": 1, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"], "botSha256": runtime.bot["sha256"], "count": count, "scope": "physical-drop-swap-inventory", "steps": []}
    evidence["artifactProvenance"] = artifact.provenance
    if case_set == "nested":
        evidence["scope"] = "native-world-spawn-independent-nested-graph"
    if case_set == "container":
        evidence["scope"] = "other-container-open-dirty-close"
    if case_set == "drag":
        evidence["scope"] = "inventory-drag-own-storage-chest-crafting-grid"
    if case_set == "station":
        evidence["scope"] = "crafting-result-and-cartography"
    if case_set == "entity":
        evidence["scope"] = "pickup-frame-equipment"
    if case_set == "delete":
        evidence["scope"] = "waypoint-deletion"
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

        def one(name, values):
            nonlocal serial
            serial += 1
            driver.send("action", {"actionId": f"protection-{serial}", "bot": name, **values})
            return driver.wait("action_result", 30)

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

        if case_set == "entity":
            kinds = {"entity-pickup": "pickup", "inventory-pickup": "hopper", "item-frame": "frame", "armor-stand": "armor-stand", "allay-interact": "allay"}

            def hotbar(owner, material):
                for entry in monitor()["inventories"][owner]["slots"]:
                    if entry["material"] == material and entry["slot"] <= 8:
                        return entry["slot"]
                raise ValueError("hotbar material missing: " + material)

            def empty_hotbar(owner):
                occupied = {entry["slot"] for entry in monitor()["inventories"][owner]["slots"] if entry["slot"] <= 8}
                for slot_index in range(9):
                    if slot_index not in occupied:
                        return slot_index
                raise ValueError("no empty hotbar slot")

            def session_counts(player):
                counts = {"sessionCompass": 0, "sessionMap": 0}
                for item in player["items"]:
                    if item["method"] == "compass":
                        counts["sessionCompass"] += item["amount"]
                    elif item["method"] == "map":
                        counts["sessionMap"] += item["amount"]
                return counts

            def expected_ordinary(case, before_ordinary, moving):
                player = Counter(before_ordinary)
                carrier = {}
                moved = dict(moving.get("ordinary") or {})
                if case["label"] == "entity-pickup-ordinary":
                    player.update(moved)
                elif case["label"] == "hopper-pickup-ordinary":
                    carrier = moved
                elif case["label"] in ("item-frame-ordinary", "armor-stand-ordinary"):
                    for key, amount in moved.items():
                        player[key] -= amount
                        if player[key] == 0:
                            del player[key]
                    carrier = moved
                return dict(player), carrier

            for case in entity_cases():
                kind = kinds[case["event"]]
                mode = "sanitize" if case["injected"] else ("protected" if case["cancelled"] else "ordinary")
                for name in names:
                    probe.send("lifecycle.arm")
                    probe.wait("lifecycle.armed")
                    if mode == "protected":
                        select(slot("compass"))
                    elif mode == "ordinary" and kind in ("frame", "armor-stand"):
                        select({owner: hotbar(owner, "minecraft:stone") for owner in names})
                    elif mode == "sanitize":
                        select({owner: empty_hotbar(owner) for owner in names})
                    probe.send("protection.entity.prepare", {"player": name, "kind": kind, "mode": mode})
                    prepared = probe.wait("protection.entity.prepared")["data"]
                    probe.send("lifecycle.arm")
                    probe.wait("lifecycle.armed")
                    before = {player["name"]: player for player in snapshot(probe)["players"]}
                    time.sleep(0.5)
                    if kind in ("pickup", "hopper"):
                        probe.send("protection.entity.enable", {"player": name})
                        probe.wait("protection.entity.enabled")
                    else:
                        one(name, {"operation": "entity-interact", "entityId": prepared["entityId"]})
                    deadline = time.monotonic() + 8
                    step = {"recipe": case, "player": name, "prepared": prepared}
                    while True:
                        observed = monitor()
                        native = {player["name"]: player for player in snapshot(probe)["players"]}
                        probe.send("protection.entity.snapshot", {"player": name})
                        carrier = probe.wait("protection.entity.snapshot")["data"]["carrier"]
                        receipts = [event for event in observed["events"] if event.get("event") == case["event"] and (
                            event.get("name") == name or event.get("uuid") == identities[name]
                            or (kind == "hopper" and (event.get("x"), event.get("y"), event.get("z")) == (prepared["x"], prepared["y"], prepared["z"])))]
                        expected_player, expected_carrier = expected_ordinary(case, before[name]["ordinary"], prepared["moving"])
                        recipe = {**case, "playerOrdinary": expected_player, "carrierOrdinary": expected_carrier}
                        sample = {"before": session_counts(before[name]), "after": session_counts(native[name]),
                                  "world": observed["worldItems"], "carrier": carrier, "playerOrdinary": native[name]["ordinary"], "receipts": receipts}
                        decision = entity_outcome(recipe, sample)
                        client = packets()
                        owners = {player["name"]: restored_session({"uuid": identities[player["name"]], "mapId": ids[player["name"]], "methods": METHODS, "target": expected_target}, player, client[player["name"]]) for player in native.values()}
                        step.update(decision=decision, sample=sample, ownerDecisions=owners)
                        if decision.status == Status.PASS and len(owners) == count and all(value.status == Status.PASS for value in owners.values()):
                            evidence["steps"].append(step)
                            atomic_write(root / "protection.json", evidence)
                            break
                        if time.monotonic() >= deadline:
                            evidence["steps"].append(step)
                            atomic_write(root / "protection.json", evidence)
                            raise ValueError("native entity protection deadline: " + case["label"] + " " + name + " " + "; ".join(decision.reasons))
                        time.sleep(0.1)
                    probe.send("protection.entity.remove", {"player": name})
                    removed = probe.wait("protection.entity.removed")["data"]
                    if removed.get("ownedRemaining") != 0:
                        raise ValueError("owned entity fixture cleanup incomplete")
            evidence["allCasesExecuted"] = True
            evidence["entityOutcomes"] = dict(Counter(step["decision"].status.value for step in evidence["steps"]))
            evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
            if evidence["serverHealth"].status != Status.PASS:
                raise ValueError("native entity protection server log health failed")
            if any(step["decision"].status != Status.PASS for step in evidence["steps"]):
                raise ValueError("native entity protection assertions failed")
            print(f"native {count}-owner pickup, frame and equipment protection verified")
            return

        if case_set == "delete":
            removed_target = {"dimension": "minecraft:overworld", "list": "protection_fixture", "waypoint": "target"}
            if count > 1:
                for name in names[1:]:
                    one(name, {"operation": "command", "command": "/wp navigate minecraft:overworld protection_fixture other all", "acknowledged": True})
                deadline = time.monotonic() + 8
                while True:
                    native = {player["name"]: player for player in snapshot(probe)["players"]}
                    if all(native[name].get("target", {}).get("waypoint") == "other" for name in names[1:]):
                        break
                    if time.monotonic() >= deadline:
                        raise ValueError("unaffected owners did not retarget before deletion")
                    time.sleep(0.1)
            before = {player["name"]: player for player in snapshot(probe)["players"]}
            probe.send("navigation.persisted")
            before_persisted = probe.wait("navigation.persisted")["data"]
            one(names[0], {"operation": "command", "command": "/wp remove minecraft:overworld protection_fixture target", "acknowledged": True})
            deadline = time.monotonic() + 8
            step = {"removed": removed_target}
            while True:
                after = {player["name"]: player for player in snapshot(probe)["players"]}
                probe.send("navigation.persisted")
                persisted = probe.wait("navigation.persisted")["data"]
                owners = []
                for name in names:
                    owners.append({
                        "before": {"target": {key: before[name]["target"][key] for key in ("dimension", "list", "waypoint")},
                                   "ordinary": before[name]["ordinary"],
                                   "protectedItems": sum(item["amount"] for item in before[name]["items"]),
                                   "persisted": before_persisted[name]},
                        "after": {"target": None if not after[name].get("target") else {key: after[name]["target"][key] for key in ("dimension", "list", "waypoint")},
                                  "ordinary": after[name]["ordinary"],
                                  "protectedItems": sum(item["amount"] for item in after[name]["items"]),
                                  "persisted": persisted[name]},
                    })
                decision = deleted_navigation(removed_target, owners)
                step.update(decision=decision, owners=owners, beforePersisted=before_persisted)
                if decision.status == Status.PASS:
                    evidence["steps"].append(step)
                    atomic_write(root / "protection.json", evidence)
                    break
                if time.monotonic() >= deadline:
                    evidence["steps"].append(step)
                    evidence["deletionOutcome"] = decision.status.value
                    evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
                    atomic_write(root / "protection.json", evidence)
                    if evidence["serverHealth"].status != Status.PASS:
                        raise ValueError("native deletion server log health failed")
                    raise ValueError("native waypoint deletion retained " + decision.status.value + ": " + "; ".join(decision.reasons))
                time.sleep(0.1)
            evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
            if evidence["serverHealth"].status != Status.PASS:
                raise ValueError("native deletion server log health failed")
            print(f"native {count}-owner waypoint deletion verified")
            return

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
                raise ValueError("native nested item assertions failed or incomplete")
            evidence["completed"] = True
            driver.close()
            driver = None
            print(f"native {count}-owner independent nested item graphs verified")
            return

        if case_set == "container":
            chests = {name: {"x": 300 + index * 3, "y": -61, "z": 297} for index, name in enumerate(names)}
            for case in container_cases():
                probe.send("lifecycle.arm")
                probe.wait("lifecycle.armed")
                start = 0
                for name in names:
                    probe.send("protection.container.place", {"player": name, "kind": case["kind"], "method": case["method"], **chests[name]})
                    placed = probe.wait("protection.container.placed")["data"]
                    if placed.get("inventoryType") != "CHEST":
                        raise ValueError("native other-container fixture is not a chest")
                before = {name: probe_container(probe, name) for name in names}
                actions({name: {"operation": "open-container", "hold": case["hold"], **chests[name]} for name in names})
                held = case["hold"]
                if case.get("inject"):
                    for name in names:
                        probe.send("protection.container.dirty", {"player": name, "method": case["method"], "inject": case["inject"]})
                        before[name] = probe.wait("protection.container.dirtied")["data"]
                    actions({name: {"operation": "close-container"} for name in names})
                    held = False
                after = {name: probe_container(probe, name) for name in names}
                observed = monitor()
                if observed["droppedObservations"]:
                    raise ValueError("native other-container receipts overflowed")
                client = packets()
                owners = {player["name"]: restored_session({"uuid": identities[player["name"]], "mapId": ids[player["name"]], "methods": METHODS, "target": expected_target}, player, client[player["name"]]) for player in snapshot(probe)["players"]}
                if len(owners) != count or any(value.status != Status.PASS for value in owners.values()):
                    raise ValueError("other-container case changed owner navigation state")
                decisions = {}
                for name in names:
                    recipe = container_recipe(case, before[name])
                    sample = {"trigger": case["trigger"],
                              "before": {**before[name]["top"], "inventoryType": "CHEST",
                                         "cursorProtectedItems": before[name].get("cursor", {}).get("protectedItems", 0)},
                              "after": {**after[name]["top"], "inventoryType": "CHEST",
                                        "cursorProtectedItems": after[name].get("cursor", {}).get("protectedItems", 0)},
                              "world": observed["worldItems"],
                              "graphs": after[name]["graphs"],
                              "expectedGraphs": before[name]["expectedGraphs"],
                              "receipts": [event for event in observed["events"][start:] if event.get("uuid") == identities[name]]}
                    decisions[name] = container_cleanup(recipe, sample)
                step = {"recipe": case, "before": before, "after": after, "monitor": observed, "ownerDecisions": owners, "decisions": decisions}
                evidence["steps"].append(step)
                atomic_write(root / "protection.json", evidence)
                if held:
                    actions({name: {"operation": "close-container"} for name in names})
                for name in names:
                    probe.send("protection.container.remove", {"player": name})
                    removed = probe.wait("protection.container.removed")["data"]
                    if removed.get("present") is not False:
                        raise ValueError("owned other-container cleanup incomplete")
            evidence["allCasesExecuted"] = True
            evidence["containerOutcomes"] = dict(Counter(step["decisions"][name].status.value for step in evidence["steps"] for name in names))
            evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
            if evidence["serverHealth"].status != Status.PASS:
                raise ValueError("native other-container server log health failed")
            if any(step["decisions"][name].status != Status.PASS for step in evidence["steps"] for name in names):
                raise ValueError("native other-container assertions failed or incomplete")
            evidence["completed"] = True
            driver.close()
            driver = None
            print(f"native {count}-owner other-container open/dirty-close verified")
            return

        if case_set == "drag":
            chests = {name: {"x": 300 + index * 3, "y": -61, "z": 297} for index, name in enumerate(names)}

            def invalid_protected(native_players, chest_top=None):
                count_invalid = 0
                for player in native_players:
                    count_invalid += sum(1 for item in player["items"] if item["slot"] >= 36 and item["slot"] != 40)
                if chest_top is not None:
                    count_invalid += chest_top.get("protectedItems", 0) + chest_top.get("nestedProtectedCarriers", 0)
                return count_invalid

            def ordinary_slot(observed, name, material):
                for entry in observed.get("inventories", {}).get(name, {}).get("slots", []):
                    if entry.get("material") == material:
                        return entry["slot"]
                raise ValueError("ordinary source slot missing: " + material)

            for case in drag_cases():
                probe.send("lifecycle.arm")
                probe.wait("lifecycle.armed")
                start = 0
                chest = case["inventoryType"] == "CHEST"
                if chest:
                    kind = "empty" if case["label"] == "ordinary-chest-control" else "ordinary"
                    for name in names:
                        probe.send("protection.container.place", {"player": name, "kind": kind, "method": "compass", **chests[name]})
                        placed = probe.wait("protection.container.placed")["data"]
                        if placed.get("inventoryType") != "CHEST":
                            raise ValueError("native drag chest fixture is not a chest")
                    actions({name: {"operation": "open-container", "hold": True, **chests[name]} for name in names})
                if case["expectedBeforeCursorProtected"]:
                    for name in names:
                        probe.send("protection.drag.cursor", {"player": name, "amount": case["expectedBeforeCursorProtected"]})
                        cursor = probe.wait("protection.drag.cursor-set")["data"]
                        if cursor.get("cursor", {}).get("protectedItems") != case["expectedBeforeCursorProtected"]:
                            raise ValueError("stackable tagged drag cursor missing")
                else:
                    observed = monitor()
                    sources = {name: player_raw_slot(ordinary_slot(observed, name, "minecraft:stone"), case["inventoryType"]) for name in names}
                    actions({name: {"operation": "pickup", "from": sources[name]} for name in names})
                before_monitor = monitor()
                placed = {name: probe_container(probe, name) for name in names} if chest else {}
                before = {}
                for name in names:
                    cursor = before_monitor.get("inventories", {}).get(name, {}).get("cursor", {})
                    if chest:
                        top = placed[name]["top"]
                        before[name] = {"ordinary": dict(placed[name]["expectedTop"]["ordinary"]), "protectedItems": top.get("protectedItems", 0),
                                        "nestedProtectedCarriers": top.get("nestedProtectedCarriers", 0), "cursorProtectedItems": cursor.get("protectedItems", 0),
                                        "inventoryType": "CHEST"}
                    else:
                        before[name] = {"ordinary": dict(ordinary_by_owner[name]), "protectedItems": 1, "nestedProtectedCarriers": 0,
                                        "cursorProtectedItems": cursor.get("protectedItems", 0), "inventoryType": "CRAFTING"}
                actions({name: {"operation": "drag", "slots": list(case["slots"]), "kind": "left", "hold": True} for name in names})
                after_native = snapshot(probe)
                after_monitor = monitor()
                after_placed = {name: probe_container(probe, name) for name in names} if chest else {}
                if after_monitor["droppedObservations"]:
                    raise ValueError("native inventory-drag receipts overflowed")
                client = packets()
                owners = {player["name"]: restored_session({"uuid": identities[player["name"]], "mapId": ids[player["name"]], "methods": METHODS, "target": expected_target}, player, client[player["name"]]) for player in after_native["players"]}
                if len(owners) != count or any(value.status != Status.PASS for value in owners.values()):
                    raise ValueError("inventory-drag case changed owner navigation state")
                decisions = {}
                for name in names:
                    player = next(value for value in after_native["players"] if value["name"] == name)
                    if case["label"] == "ordinary-chest-control":
                        ordinary = {key: value for key, value in ordinary_by_owner[name].items() if key.startswith("minecraft:stone:")}
                    elif chest:
                        ordinary = dict(before[name]["ordinary"])
                    else:
                        ordinary = dict(ordinary_by_owner[name])
                    recipe = {**{key: case[key] for key in ("label", "inventoryType", "slots", "cancelled",
                                                            "expectedBeforeCursorProtected", "expectedAfterCursorProtected",
                                                            "expectedAfterTopProtected", "expectedAfterInvalidProtected")},
                              "ordinary": ordinary}
                    top = after_placed[name]["top"] if chest else {}
                    after = {"ordinary": top.get("ordinary", player["ordinary"]), "protectedItems": top.get("protectedItems", sum(item["amount"] for item in player["items"])),
                             "nestedProtectedCarriers": top.get("nestedProtectedCarriers", 0), "inventoryType": case["inventoryType"],
                             "cursorProtectedItems": after_monitor.get("inventories", {}).get(name, {}).get("cursor", {}).get("protectedItems", 0),
                             "protectedInInvalidLocations": invalid_protected([player], top if chest else None)}
                    sample = {"before": before[name], "after": after, "world": after_monitor["worldItems"],
                              "receipts": [event for event in after_monitor["events"][start:] if event.get("uuid") == identities[name]]}
                    decisions[name] = drag_outcome(recipe, sample)
                step = {"recipe": case, "before": before, "after": after_placed if chest else after_native, "monitor": after_monitor, "ownerDecisions": owners, "decisions": decisions}
                evidence["steps"].append(step)
                atomic_write(root / "protection.json", evidence)
                actions({name: {"operation": "close-container"} for name in names})
                if chest:
                    for name in names:
                        probe.send("protection.container.remove", {"player": name})
                        removed = probe.wait("protection.container.removed")["data"]
                        if removed.get("present") is not False:
                            raise ValueError("owned drag chest cleanup incomplete")
            evidence["allCasesExecuted"] = True
            evidence["dragOutcomes"] = dict(Counter(step["decisions"][name].status.value for step in evidence["steps"] for name in names))
            evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
            if evidence["serverHealth"].status != Status.PASS:
                raise ValueError("native inventory-drag server log health failed")
            if any(step["decisions"][name].status != Status.PASS for step in evidence["steps"] for name in names):
                raise ValueError("native inventory-drag assertions failed or incomplete")
            evidence["completed"] = True
            driver.close()
            driver = None
            print(f"native {count}-owner inventory-drag own-storage/chest/crafting-grid verified")
            return

        if case_set == "station":
            def material_amount(observed, name, material):
                return sum(entry.get("amount", 0) for entry in observed.get("inventories", {}).get(name, {}).get("slots", []) if entry.get("material") == material)

            def station_counts(observed, native_players, name):
                player = next(value for value in native_players if value["name"] == name)
                session_compass = sum(item["amount"] for item in player["items"] if item.get("method") == "compass")
                session_map = sum(item["amount"] for item in player["items"] if item.get("method") == "map")
                return {"paper": material_amount(observed, name, "minecraft:paper"),
                        "plainCompass": material_amount(observed, name, "minecraft:compass") - session_compass,
                        "emptyMaps": material_amount(observed, name, "minecraft:map"),
                        "plainFilledMaps": material_amount(observed, name, "minecraft:filled_map") - session_map,
                        "sessionCompass": session_compass, "sessionMap": session_map}

            def session_slot(native_players, name, method):
                player = next(value for value in native_players if value["name"] == name)
                matches = [item["slot"] for item in player["items"] if item.get("method") == method and item.get("amount") == 1]
                if len(matches) != 1:
                    raise ValueError("session " + method + " slot missing")
                return matches[0]

            for case in station_cases():
                probe.send("lifecycle.arm")
                probe.wait("lifecycle.armed")
                kind = "workbench" if case["inventoryType"] == "WORKBENCH" else "cartography"
                placed = {}
                for index, name in enumerate(names):
                    probe.send("protection.station.place", {"player": name, "kind": kind, "x": 300 + index * 3, "y": -61, "z": 297})
                    station = probe.wait("protection.station.placed")["data"]
                    if station.get("inventoryType") != case["inventoryType"] or station.get("present") is not True:
                        raise ValueError("native station fixture missing")
                    placed[name] = {"x": station["x"], "y": station["y"], "z": station["z"]}
                seeds = {name: {} for name in names}
                paper_amount = 8 if case["kind"] == "craft" else 1
                for name in names:
                    probe.send("protection.station.seed", {"player": name, "material": "paper", "amount": paper_amount})
                    seeds[name]["paper"] = probe.wait("protection.station.seeded")["data"]["slot"]
                    if not case["protected"] and case["kind"] == "craft":
                        probe.send("protection.station.seed", {"player": name, "material": "compass", "amount": 1})
                        seeds[name]["compass"] = probe.wait("protection.station.seeded")["data"]["slot"]
                    if not case["protected"] and case["kind"] == "cartography":
                        probe.send("protection.station.seed", {"player": name, "material": "filled_map", "amount": 1})
                        seeds[name]["map"] = probe.wait("protection.station.seeded")["data"]["slot"]
                actions({name: {"operation": "open-container", "hold": True, **placed[name]} for name in names})
                before_native = snapshot(probe)
                before_monitor = monitor()
                start = len(before_monitor["events"])
                view = case["inventoryType"]

                def click(slot_for_name, button):
                    actions({name: {"operation": "menu-click", "slot": slot_for_name(name), "button": button} for name in names})

                if case["kind"] == "craft":
                    click(lambda name: player_raw_slot(seeds[name]["paper"], view), "left")
                    for grid in (1, 2, 3, 4, 6, 7, 8, 9):
                        click(lambda name, grid=grid: grid, "right")
                    if case["protected"]:
                        click(lambda name: player_raw_slot(session_slot(before_native["players"], name, "compass"), view), "left")
                        click(lambda name: 5, "left")
                        for name in names:
                            probe.send("protection.station.inject", {"player": name, "kind": "workbench"})
                            injected = probe.wait("protection.station.injected")["data"]
                            if injected.get("cursorProtected") is not False:
                                raise ValueError("protected compass remained on the cursor")
                        click(lambda name: 0, "left")
                    else:
                        click(lambda name: player_raw_slot(seeds[name]["compass"], view), "left")
                        click(lambda name: 5, "left")
                        click(lambda name: 0, "shift")
                elif case["protected"]:
                    click(lambda name: player_raw_slot(seeds[name]["paper"], view), "left")
                    click(lambda name: 1, "left")
                    click(lambda name: player_raw_slot(session_slot(before_native["players"], name, "map"), view), "left")
                    click(lambda name: 0, "left")
                    for name in names:
                        probe.send("protection.station.inject", {"player": name, "kind": "cartography"})
                        injected = probe.wait("protection.station.injected")["data"]
                        if injected.get("cursorProtected") is not False:
                            raise ValueError("protected map remained on the cursor")
                    click(lambda name: 2, "left")
                else:
                    click(lambda name: player_raw_slot(seeds[name]["map"], view), "left")
                    click(lambda name: 0, "left")
                    click(lambda name: player_raw_slot(seeds[name]["paper"], view), "left")
                    click(lambda name: 1, "left")
                    click(lambda name: 2, "shift")
                actions({name: {"operation": "close-container"} for name in names})
                for name in names:
                    probe.send("protection.station.remove", {"player": name})
                    removed = probe.wait("protection.station.removed")["data"]
                    if removed.get("present") is not False:
                        raise ValueError("owned station cleanup incomplete")
                after_native = snapshot(probe)
                after_monitor = monitor()
                if after_monitor["droppedObservations"]:
                    raise ValueError("native station receipts overflowed")
                client = packets()
                owners = {player["name"]: restored_session({"uuid": identities[player["name"]], "mapId": ids[player["name"]], "methods": METHODS, "target": expected_target}, player, client[player["name"]]) for player in after_native["players"]}
                if len(owners) != count or any(value.status != Status.PASS for value in owners.values()):
                    raise ValueError("station case changed owner navigation state")
                decisions = {}
                for name in names:
                    sample = {"before": station_counts(before_monitor, before_native["players"], name),
                              "after": station_counts(after_monitor, after_native["players"], name),
                              "world": after_monitor["worldItems"],
                              "receipts": [event for event in after_monitor["events"][start:] if event.get("uuid") == identities[name]]}
                    decisions[name] = station_outcome(case, sample)
                step = {"recipe": case, "decisions": decisions, "ownerDecisions": owners}
                evidence["steps"].append(step)
                atomic_write(root / "protection.json", evidence)
            evidence["allCasesExecuted"] = True
            evidence["stationOutcomes"] = dict(Counter(step["decisions"][name].status.value for step in evidence["steps"] for name in names))
            evidence["serverHealth"] = native_log_health(server / "logs/latest.log")
            if evidence["serverHealth"].status != Status.PASS:
                raise ValueError("native station server log health failed")
            if any(step["decisions"][name].status != Status.PASS for step in evidence["steps"] for name in names):
                raise ValueError("native crafting/cartography assertions failed or incomplete")
            evidence["completed"] = True
            driver.close()
            driver = None
            print(f"native {count}-owner crafting/cartography protection verified")
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
    parser.add_argument("--case-set", choices=("physical", "nested", "container", "drag", "station", "entity", "delete"), default="physical")
    parser.add_argument("--candidate", type=Path, help="Explicit development candidate JAR; retain its own hash and provenance")
    args = parser.parse_args()
    run(args.root, args.count, args.case_set, args.candidate)
