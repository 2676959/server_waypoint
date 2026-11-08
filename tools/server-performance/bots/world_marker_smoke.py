"""Short candidate-only world marker experiment; not a release performance gate."""
import argparse
from dataclasses import replace
import json
import math
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256
from bot_driver import BotDriver, validate_ready
from contracts import atomic_write, load_runtimes
from controller import create_root
from models import Status
from processes import stop_owned
from worlds import install_server, boot


def run(root, artifact, count):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])})
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], artifact)
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    points = [{"name": name, "display_name": label, "initials": name.upper(), "pos": pos,
               "color": "#44CCFF", "yaw": 0, "global": True, "keywords": [], "description": ""}
              for name, label, pos in (("a", "Target A", [320, -61, 320]),
                                       ("b", "Target B", [-320, -61, -320]))]
    points[0]["icon"] = "minecraft:diamond"
    points[1]["icon"] = "voxelmap:house"
    points[1]["color"] = "#FFFFFF"
    points.append({**points[0], "name": "c", "initials": "C", "display_name": "Target C"})
    points[-1].pop("icon")
    (directory / "minecraft$overworld.json").write_text(json.dumps([
        {"list_name": "marker_fixture", "n": 1, "waypoints": points}]))
    evidence = {"schemaVersion": 1, "scope": "experimental-candidate-packet-and-handler-smoke",
                "count": count, "artifactSha256": sha256(artifact),
                "serverSha256": runtime.server_sha256, "probeSha256": runtime.probe["sha256"],
                "botSha256": sha256(runtime.bot["path"]), "phases": {}}
    process, probe = boot(runtime, server, False, sha256(artifact))
    driver = None
    try:
        def execute(command, actor="console"):
            probe.send("commands.execute", {"commands": [command], "actor": actor})
            return probe.wait("commands.executed")["data"]

        def snapshot():
            probe.send("navigation.snapshot")
            return probe.wait("navigation.snapshot")["data"]

        execute("difficulty peaceful")
        driver = BotDriver(runtime.bot["path"], server, probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 120)
        assert validate_ready(ready, snapshot()["players"], count, "vanilla").status == Status.PASS
        names = sorted(player["name"] for player in snapshot()["players"])
        for name in names:
            execute("op " + name)
            execute(f"tp {name} 300 -61 300")
        time.sleep(1)

        def packets():
            driver.send("effects")
            return driver.wait("packet_summary")["effects"]

        def await_labels(expected):
            deadline = time.monotonic() + 20
            while True:
                effects = packets()
                if all(sorted(v["text"] for v in effects[name]["textDisplays"].values()) == sorted(labels)
                       for name, labels in expected.items()):
                    return effects
                if time.monotonic() >= deadline:
                    raise AssertionError(f"Client marker labels differ: {effects}")
                time.sleep(0.1)

        def await_display_ids(expected):
            deadline = time.monotonic() + 20
            while True:
                effects = packets()
                if all((set(effects[name]["textDisplays"]) | set(effects[name]["itemDisplays"]))
                       == {str(value) for value in ids}
                       and all(value["text"] is not None for value in effects[name]["textDisplays"].values())
                       for name, ids in expected.items()):
                    return effects
                assert time.monotonic() < deadline, "Independent display entities differ from expected IDs"
                time.sleep(0.1)


        def labels(target, distance):
            return [target[-1], f"{distance}m"]

        execute("wp navigate minecraft:overworld marker_fixture a world_marker", names[0])
        first = await_labels({name: labels("Target A", 28) if name == names[0] else [] for name in names})
        evidence["ownerIsolation"] = first
        for name in names[1:]:
            execute("wp navigate minecraft:overworld marker_fixture a world_marker", name)
        await_labels({name: labels("Target A", 28) for name in names})
        start = snapshot()
        ids = [v["entityId"] for p in start["players"] for v in p["worldMarker"]["displays"]]
        assert len(set(ids)) == count * 3
        assert start["resources"]["entities"] == count * 3
        for player in start["players"]:
            rows = {row["part"]: row for row in player["worldMarker"]["displays"]}
            assert set(rows) == {"badge", "distance", "item"}
            assert rows["badge"]["text"] == "A" and rows["distance"]["text"] == "28m"
            assert rows["badge"]["flags"] == 2 and rows["badge"]["background"] & 0xFFFFFFFF == 0x8044CCFF
            assert rows["badge"]["textRgb"] == 0xFFFFFF
            assert rows["distance"]["flags"] == 7
            assert rows["item"]["item"] == "minecraft:diamond" and rows["item"]["context"].lower() == "gui"
            for part, offset, scale in (("badge", 0.3, 1), ("distance", 0, 0.8), ("item", 0.8, 0.4)):
                row = rows[part]
                assert row["teleportDuration"] == 1 and row["billboard"].lower() == "center" and not row["inWorld"]
                assert abs(row["translation"][1] - offset) < 0.00001
                assert all(abs(value - scale) < 0.00001 for value in row["scale"])
                assert row["position"] == rows["badge"]["position"]
        expected = {p["name"]: [v["entityId"] for v in p["worldMarker"]["displays"]] for p in start["players"]}
        evidence["initialClientRows"] = await_display_ids(expected)
        time.sleep(8)
        end = snapshot()
        for player in end["players"]:
            before = next(p for p in start["players"] if p["name"] == player["name"])["worldMarker"]
            after = player["worldMarker"]
            assert after["updates"] > before["updates"]
            assert all(after[k] == before[k] for k in ("metadata", "spawns", "removals"))
            elapsed = end["tick"] - start["tick"]
            assert abs((after["updates"] - before["updates"]) - elapsed) <= 1
            assert after["moves"] - before["moves"] == (after["updates"] - before["updates"]) * 3
            old_samples = {v["entityId"]: v for v in before["displays"]}
            for sample in after["displays"]:
                assert sample["position"] == old_samples[sample["entityId"]]["position"]
        evidence["phases"]["stationary"] = {"before": start, "after": end}
        start = end
        for name in names:
            execute(f"execute as {name} at @s run tp @s ~ ~ ~ 135 40")
        time.sleep(0.5)
        end = snapshot()
        for player in end["players"]:
            before = next(p for p in start["players"] if p["name"] == player["name"])
            assert player["position"] == before["position"]
            assert player["worldMarker"]["metadata"] == before["worldMarker"]["metadata"]
            assert player["worldMarker"]["moves"] > before["worldMarker"]["moves"]
        evidence["phases"]["head-turn"] = {"before": start, "after": end}
        start = end
        for index, name in enumerate(names):
            driver.send("action", {"actionId": f"walk-{index}", "bot": name,
                                   "operation": "walk", "milliseconds": 3000})
        time.sleep(0.8)
        moving = snapshot()
        for player in moving["players"]:
            marker = player["worldMarker"]
            eye = marker["sampleEye"]
            vx, _, vz = marker["observedVelocity"]
            assert math.hypot(vx, vz) > 0.01, "Native walking sample has no observed horizontal movement"
            tx, ty, tz = player["target"]["position"]
            delta = [tx + 0.5 - eye[0], ty + 0.5 - eye[1], tz + 0.5 - eye[2]]
            factor = min(1, 12 / math.sqrt(sum(v * v for v in delta)))
            baseline = [eye[i] + delta[i] * factor for i in range(3)]
            predicted = marker["displays"][0]["position"]
            assert ((predicted[0] - baseline[0]) * vx + (predicted[2] - baseline[2]) * vz) > 0.0001, \
                "Every-tick marker is not leading the unpredicted position in the movement direction"
            assert math.dist(predicted, baseline) <= 2.001, "Prediction exceeded its displacement bound"
        evidence["movingPrediction"] = moving
        for _ in names:
            result = driver.wait("action_result", 30)
            assert not result.get("error") and result.get("success") is not False
        time.sleep(0.5)
        end = snapshot()
        for player in end["players"]:
            before = next(p for p in start["players"] if p["name"] == player["name"])
            assert player["position"] != before["position"]
            assert player["worldMarker"]["moves"] > before["worldMarker"]["moves"]
        evidence["phases"]["walking"] = {"before": start, "after": end}
        execute("wp navigate minecraft:overworld marker_fixture b world_marker", names[0])
        deadline = time.monotonic() + 20
        while True:
            effects = packets()
            if all(len(effects[name]["textDisplays"]) == 2 and
                   any(value["text"] == ("B" if name == names[0] else "A")
                       for value in effects[name]["textDisplays"].values()) for name in names):
                break
            assert time.monotonic() < deadline, "Retarget did not reach intended owner"
            time.sleep(0.1)
        retargeted = snapshot()
        expected = {p["name"]: [v["entityId"] for v in p["worldMarker"]["displays"]] for p in retargeted["players"]}
        received = await_display_ids(expected)
        owner = next(p for p in retargeted["players"] if p["name"] == names[0])
        assert {v["part"] for v in owner["worldMarker"]["displays"]} == {"badge", "distance"}
        badge = next(v for v in owner["worldMarker"]["displays"] if v["part"] == "badge")
        assert badge["textRgb"] == 0 and badge["background"] & 0xFFFFFFFF == 0x80FFFFFF
        assert not received[names[0]]["itemDisplays"]
        evidence["retargetIsolation"] = {"native": retargeted, "client": received}
        execute("wp navigate minecraft:overworld marker_fixture c world_marker", names[0])
        no_icon = snapshot()
        expected = {p["name"]: [v["entityId"] for v in p["worldMarker"]["displays"]] for p in no_icon["players"]}
        received = await_display_ids(expected)
        assert not received[names[0]]["itemDisplays"]
        evidence["noIcon"] = {"native": no_icon, "client": received}
        execute("wp navigate minecraft:overworld marker_fixture a world_marker", names[0])
        restored = snapshot()
        expected = {p["name"]: [v["entityId"] for v in p["worldMarker"]["displays"]] for p in restored["players"]}
        received = await_display_ids(expected)
        assert len(received[names[0]]["itemDisplays"]) == 1
        evidence["restoredItem"] = {"native": restored, "client": received}
        evidence["diagnosticCommand"] = execute("wp navigate config world_marker", names[0])

        execute("wp navigate use text_display", names[0])
        combined = snapshot()
        owner = next(p for p in combined["players"] if p["name"] == names[0])
        marker_ids = [v["entityId"] for v in owner["worldMarker"]["displays"]]
        hud_id = owner["textDisplayId"]
        assert hud_id not in marker_ids
        expected = {p["name"]: [v["entityId"] for v in p["worldMarker"]["displays"]] for p in combined["players"]}
        expected[names[0]].append(hud_id)
        combined_packets = await_display_ids(expected)
        evidence["combinedDisplays"] = {"native": combined, "client": combined_packets}

        execute("wp navigate disable world_marker", names[0])
        hud_only = snapshot()
        owner = next(p for p in hud_only["players"] if p["name"] == names[0])
        assert owner["textDisplayId"] == hud_id and not owner["worldMarker"]
        expected[names[0]] = [hud_id]
        hud_packets = await_display_ids(expected)
        evidence["disableMarkerKeepsHud"] = {"native": hud_only, "client": hud_packets}

        execute("wp navigate use world_marker", names[0])
        combined = snapshot()
        owner = next(p for p in combined["players"] if p["name"] == names[0])
        marker_ids = [v["entityId"] for v in owner["worldMarker"]["displays"]]
        assert owner["textDisplayId"] == hud_id
        expected[names[0]] = [hud_id, *marker_ids]
        await_display_ids(expected)
        execute("wp navigate disable text_display", names[0])
        marker_only = snapshot()
        owner = next(p for p in marker_only["players"] if p["name"] == names[0])
        assert [v["entityId"] for v in owner["worldMarker"]["displays"]] == marker_ids and "textDisplayId" not in owner
        expected[names[0]] = marker_ids
        marker_packets = await_display_ids(expected)
        evidence["disableHudKeepsMarker"] = {"native": marker_only, "client": marker_packets}
        for name in names:
            execute("wp navigate disable", name)
        await_labels({name: [] for name in names})
        await_display_ids({name: [] for name in names})
        stopped = snapshot()
        assert all(not p["methods"] and not p["worldMarker"] for p in stopped["players"])
        assert stopped["resources"]["entities"] == 0
        assert stopped["resources"]["markerMotionTracks"] == 0
        evidence["stopped"] = stopped
        for name in names:
            execute("wp navigate minecraft:overworld marker_fixture a world_marker", name)
        driver.close()
        driver = None
        deadline = time.monotonic() + 10
        while True:
            disconnected = snapshot()
            if not disconnected["players"]:
                break
            assert time.monotonic() < deadline, "Actors did not disconnect"
            time.sleep(0.1)
        assert disconnected["resources"]["entities"] == 0
        assert disconnected["resources"]["markerMotionTracks"] == 0
        assert disconnected["resources"]["sessions"] == 0
        evidence["disconnected"] = disconnected
        evidence["passed"] = True
    except Exception as error:
        evidence["passed"] = False
        evidence["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        atomic_write(root / "world-marker.json", evidence)
        try:
            if driver is not None:
                driver.close()
        finally:
            stop_owned(process, 30)
    print(json.dumps({"passed": True, "evidence": str(root / "world-marker.json")}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--count", type=int, choices=range(2, 11), default=2)
    args = parser.parse_args()
    run(args.root.resolve(), args.artifact.resolve(), args.count)
