"""Native navigation method selection and client packet observations on sealed release bytes."""
import argparse
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
from scenarios.navigation import METHODS, method_selections, method_coverage, cleanup_counts, retargeted, ordinary_conservation
from worlds import install_server, boot


def snapshot(probe):
    probe.send("navigation.snapshot")
    return probe.wait("navigation.snapshot")["data"]


def execute(probe, command):
    probe.send("commands.execute", {"commands": [command]})
    return probe.wait("commands.executed")["data"]


def run(root, count, snapshot_only=False):
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
    points = [{"name": name, "display_name": label, "initials": name.upper(), "pos": position,
               "color": color, "yaw": 0, "global": True, "keywords": [], "description": "Native navigation fixture"}
              for name, label, position, color in (("a", "Target A", [320, -61, 320], "#112233"),
                                                   ("b", "Target B", [-320, -61, -320], "#445566"))]
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "nav_fixture", "n": 7, "waypoints": points}], indent=4) + "\n")
    evidence = {"schemaVersion": 1, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"],
                "botSha256": runtime.bot["sha256"], "count": count, "scope": "navigation-method-functional-smoke", "steps": []}
    atomic_write(root / "inputs.json", {"schemaVersion": 1, "files": {str(path.relative_to(tools)): sha256(path) for path in
                 (Path(__file__).resolve(), tools / "scenarios/navigation.py", tools / "bot_driver.py", tools / "worlds.py")}})
    process, probe = boot(runtime, server, False, artifact.sha256)
    driver = None
    try:
        evidence["empty"] = snapshot(probe)
        if snapshot_only:
            return
        probe.send("commands.tree")
        atomic_write(root / "command-tree.json", {"schemaVersion": 1, **probe.wait("commands.tree")["data"]})
        execute(probe, "difficulty peaceful")
        driver = BotDriver(runtime.bot["path"], server, probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 120)
        native = snapshot(probe)
        decision = validate_ready(ready, native["players"], count, "vanilla")
        if decision.status != Status.PASS:
            raise ValueError(decision.reasons)
        names = sorted(player["name"] for player in native["players"])
        supported = native["supportedMethods"]
        if set(supported) != METHODS:
            raise ValueError("native supported methods differ from required anchor methods")
        serial = 0

        def actions(command=None, slots=None, only=None):
            nonlocal serial
            selected = names if only is None else only
            for name in selected:
                serial += 1
                data = {"actionId": f"nav-{serial}", "bot": name}
                if command is not None:
                    data.update(operation="command", command=command, acknowledged=True)
                else:
                    data.update(operation="select", slot=slots[name])
                driver.send("action", data)
            results = [driver.wait("action_result", 30) for _ in selected]
            if any(value.get("error") or value.get("success") is False for value in results):
                raise ValueError("native navigation bot action failed")
            return results

        def packets():
            driver.send("barrier")
            value = driver.wait("packet_summary", 30)
            rate = driver.wait("rate_sample", 30)
            if rate.get("pending") or rate.get("failed"):
                raise ValueError("navigation action barrier incomplete")
            return value["effects"]

        for name in names:
            execute(probe, "op " + name)
            execute(probe, f"tp {name} 300 -61 300")
            execute(probe, f"give {name} minecraft:stone 64")
            execute(probe, f"give {name} minecraft:diamond 3")
        actions("/wp help")
        ordinary = {player["name"]: player["ordinary"] for player in snapshot(probe)["players"]}
        for items in ordinary.values():
            if sum(value for key, value in items.items() if key.startswith("minecraft:stone:")) != 64 or sum(value for key, value in items.items() if key.startswith("minecraft:diamond:")) != 3 or sum(items.values()) != 67:
                raise ValueError("ordinary conservation fixture was not independently seeded")
        evidence["ordinaryBefore"] = ordinary
        observations = {name: [] for name in names}
        for selection in method_selections(supported):
            actions("/wp navigate disable")
            time.sleep(0.4)
            before = packets()
            actions("/wp navigate minecraft:overworld nav_fixture a " + selection[0])
            for method in selection[1:]:
                actions("/wp navigate use " + method)
            native = snapshot(probe)
            if "map" in selection:
                slots = {player["name"]: next(item["slot"] for item in player["items"] if item["method"] == "map") for player in native["players"]}
                actions(slots=slots)
            # Wait for real display/map packets rather than assuming dispatch
            # acknowledgement also proves a client has received its effects.
            deadline = time.monotonic() + 12
            while True:
                native = snapshot(probe)
                client = packets()
                step = []
                for player in native["players"]:
                    name = player["name"]
                    if ordinary_conservation(ordinary[name], player["ordinary"]).status != Status.PASS:
                        raise ValueError("ordinary items lost/changed during method selection")
                    effects = client[name]
                    items = player["items"]
                    effect = {"compass": sum(item["amount"] for item in items if item["method"] == "compass") == 1,
                              "map": sum(item["amount"] for item in items if item["method"] == "map") == 1,
                              "bossbar": any("Target A" in value["text"] for value in effects["bossbars"].values()),
                              "text_display": any("Target A" in (value["text"] or "") for value in effects["textDisplays"].values()),
                              "actionbar": effects["actionbarUpdates"] > before[name]["actionbarUpdates"] and any(value["sequence"] > before[name]["sequence"] and "Target A" in value["text"] for value in effects["actionbar"])}
                    if "map" in selection:
                        map_id = next(item["mapState"]["id"] for item in items if item["method"] == "map")
                        effect["map"] &= any(value["id"] == map_id and any(cursor["type"] == "TargetX" and cursor["x"] == -96 and cursor["z"] == -96 and "Target A" in (cursor["caption"] or "") for cursor in value["decorations"]) for value in effects["maps"])
                    step.append({"name": name, "requested": list(selection), "nativeMethods": player["methods"], "effects": effect})
                if all(all(value["effects"][method] == (method in selection) for method in supported) for value in step):
                    break
                if time.monotonic() >= deadline:
                    raise ValueError("method effects did not match selection by deadline: " + str(step))
                time.sleep(0.1)
            evidence["steps"].append({"selection": selection, "observations": step, "native": native, "client": client})
            atomic_write(root / "navigation.json", evidence)
            for value in step:
                observations[value["name"]].append(value)
        evidence["methodCoverage"] = {name: method_coverage(supported, values) for name, values in observations.items()}
        if any(value.status != Status.PASS for value in evidence["methodCoverage"].values()):
            raise ValueError("method combination coverage incomplete")

        def target_state(expected, owners=None):
            deadline = time.monotonic() + 12
            while True:
                native = snapshot(probe)
                client = packets()
                decisions = {}
                for player in native["players"]:
                    if set(player["methods"]) != METHODS:
                        continue
                    desired = expected if owners is None else owners[player["name"]]
                    effects = client[player["name"]]
                    state = dict(player)
                    for item in player["items"]:
                        if item["method"] == "compass":
                            state.update(compassTarget=item.get("compassTarget"), compassCaption=item["caption"])
                        elif item["method"] == "map":
                            state["mapState"] = item["mapState"]
                    state["actionbar"] = effects["actionbar"][-1]["text"] if effects["actionbar"] else ""
                    decision = retargeted(desired, state)
                    if decision.status == Status.PASS:
                        label = desired["displayName"]
                        if not any(label in value["text"] for value in effects["bossbars"].values()) or not any(label in (value["text"] or "") for value in effects["textDisplays"].values()):
                            continue
                        map_id = next(item["mapState"]["id"] for item in player["items"] if item["method"] == "map")
                        map_expected = desired["expectedMap"]
                        if not any(value["id"] == map_id and any(cursor["type"] == "TargetX" and cursor["x"] == map_expected["cursorX"] and cursor["z"] == map_expected["cursorZ"] and label in (cursor["caption"] or "") for cursor in value["decorations"]) for value in effects["maps"]):
                            continue
                        decisions[player["name"]] = decision
                if len(decisions) == count:
                    return {"expected": expected if owners is None else owners, "native": native, "client": client, "decisions": decisions}
                if time.monotonic() >= deadline:
                    atomic_write(root / "target-mismatch.json", {"schemaVersion": 1, "expected": expected, "native": native, "client": client})
                    raise ValueError("native/client navigation retarget mismatch")
                time.sleep(0.1)

        a = {"dimension": "minecraft:overworld", "list": "nav_fixture", "waypoint": "a", "displayName": "Target A", "position": [320, -61, 320], "rgb": 0x112233,
             "expectedMap": {"centerX": 512, "centerZ": 512, "scale": 2, "cursorX": -96, "cursorZ": -96}}
        b = {"dimension": "minecraft:overworld", "list": "nav_fixture", "waypoint": "b", "displayName": "Target B", "position": [-320, -61, -320], "rgb": 0x445566,
             "expectedMap": {"centerX": -512, "centerZ": -512, "scale": 2, "cursorX": 96, "cursorZ": 96}}
        evidence["allMethodsTarget"] = target_state(a)
        before_move = snapshot(probe)
        for name in names:
            serial += 1
            driver.send("action", {"actionId": f"nav-{serial}", "bot": name, "operation": "walk", "milliseconds": 500})
        for _ in names:
            driver.wait("action_result", 30)
        after_move = snapshot(probe)
        if not all(next(value for value in before_move["players"] if value["name"] == player["name"])["position"] != player["position"] for player in after_move["players"]):
            raise ValueError("native player movement absent")
        evidence["movement"] = {"before": before_move, "after": after_move, "target": target_state(a)}
        if count > 1:
            actions("/wp navigate minecraft:overworld nav_fixture b", only=names[:1])
            evidence["ownerIsolation"] = target_state(a, {name: b if name == names[0] else a for name in names})
        actions("/wp navigate minecraft:overworld nav_fixture b")
        evidence["retarget"] = target_state(b)
        execute(probe, "wp edit waypoint minecraft:overworld nav_fixture b set display-name Edited")
        execute(probe, "wp edit waypoint minecraft:overworld nav_fixture b set color AABBCC")
        execute(probe, "wp edit waypoint minecraft:overworld nav_fixture b set position -300 -61 -300")
        execute(probe, "wp edit waypoint minecraft:overworld nav_fixture b set identifier renamed")
        edited = {**b, "waypoint": "renamed", "displayName": "Edited", "rgb": 0xAABBCC, "position": [-300, -61, -300],
                  "expectedMap": {"centerX": -512, "centerZ": -512, "scale": 2, "cursorX": 106, "cursorZ": 106}}
        evidence["editedTarget"] = target_state(edited)
        actions("/wp tp minecraft:overworld nav_fixture renamed")
        deadline = time.monotonic() + 10
        while True:
            arrived = snapshot(probe)
            if all(player["position"] == edited["position"] for player in arrived["players"]):
                break
            if time.monotonic() >= deadline:
                raise ValueError("native waypoint teleport did not arrive at independent expected position")
            time.sleep(0.1)
        evidence["arrival"] = {"native": arrived, "target": target_state(edited)}
        actions("/wp navigate disable")
        time.sleep(0.5)
        before = packets()
        first = snapshot(probe)
        time.sleep(0.6)
        after = packets()
        last = snapshot(probe)
        if last["tick"] - first["tick"] < 5 or any(after[name]["actionbarUpdates"] != before[name]["actionbarUpdates"] or after[name]["bossbars"] or after[name]["textDisplays"] for name in names):
            raise ValueError("navigation client effects continued after disable/quiescence")
        evidence["disabled"] = {"before": first, "after": last, "client": after}
        evidence["ordinaryPreserved"] = all(ordinary_conservation(ordinary[player["name"]], player["ordinary"]).status == Status.PASS for player in last["players"])
        if not evidence["ordinaryPreserved"]:
            raise ValueError("ordinary items lost/changed during retarget/lifecycle")
        driver.close()
        driver = None
        deadline = time.monotonic() + 10
        while True:
            value = snapshot(probe)
            if not value["players"]:
                break
            if time.monotonic() >= deadline:
                raise TimeoutError("native disconnect deadline")
            time.sleep(0.05)
        quiescent_tick = value["tick"] + 10
        while value["tick"] < quiescent_tick:
            time.sleep(0.05)
            value = snapshot(probe)
        expected = {"sessions": 0, "tasks": 0, "entities": 0, "bossbars": 0, "items": 0, "mapTargets": 0, "mapCacheEntries": count,
                    "tickCounters": 0, "targetIndexEntries": 0}
        evidence["cleanup"] = {"expected": expected, "actual": value["resources"], "decision": cleanup_counts(expected, value["resources"])}
        if evidence["cleanup"]["decision"].status != Status.PASS:
            raise ValueError("native navigation cleanup counts differ")
    except BaseException as error:
        evidence["error"] = str(error)
        raise
    finally:
        if driver is not None:
            driver.abort()
        stop_owned(process, 30)
        atomic_write(root / "navigation.json", evidence)
    print(f"native {count}-player navigation: 31 method combinations, client effects, disable and disconnect cleanup")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--count", type=int, choices=(1, 10), default=1)
    parser.add_argument("--snapshot-only", action="store_true")
    args = parser.parse_args()
    run(args.root, args.count, args.snapshot_only)
