"""Candidate-only world marker acceptance smoke; not a release performance gate."""
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


def assert_marker_configuration(player, size, radius, expected_ids=None):
    """Check stationary native rows against requested settings, never observed scale."""
    def vector(value):
        assert isinstance(value, (list, tuple)) and len(value) == 3, "Missing three-axis vector"
        assert all(type(v) in (int, float) and math.isfinite(v) for v in value), "Non-finite vector"
        return value

    marker = player.get("worldMarker")
    assert isinstance(marker, dict) and marker, "Missing active marker"
    eye = vector(marker.get("sampleEye"))
    velocity = vector(marker.get("observedVelocity"))
    assert all(abs(v) < 0.00001 for v in velocity), "Configuration check requires a stationary sample"
    target = vector(player.get("target", {}).get("position"))
    centre = [v + 0.5 for v in target]
    delta = [centre[i] - eye[i] for i in range(3)]
    distance = math.sqrt(sum(v * v for v in delta))
    factor = min(1.0, radius / distance) if distance else 1.0
    anchor = [eye[i] + factor * delta[i] for i in range(3)]
    displays = marker.get("displays")
    assert isinstance(displays, list) and len(displays) == 3, "Expected exactly three marker rows"
    rows = {row.get("part"): row for row in displays}
    assert set(rows) == {"badge", "distance", "item"}, "Missing, duplicated or unexpected marker part"
    ids = {part: row.get("entityId") for part, row in rows.items()}
    assert all(type(v) is int and v > 0 for v in ids.values()), "Invalid marker entity ID"
    assert len(set(ids.values())) == 3, "Duplicate marker entity IDs"
    if expected_ids is not None:
        assert ids == expected_ids, "Settings unexpectedly respawned marker entities"
    for part, offset, scale in (("badge", 0.3, 1), ("distance", 0, 0.8), ("item", 0.8, 0.4)):
        row = rows[part]
        assert row.get("inWorld") is False, f"{part}: marker inserted into server world"
        assert str(row.get("billboard")).lower() == "center", f"{part}: wrong billboard"
        assert row.get("teleportDuration") == 1, f"{part}: wrong teleport duration"
        assert math.dist(vector(row.get("position")), anchor) < 0.00001, f"{part}: wrong projected block centre"
        assert math.dist(vector(row.get("translation")), [0, offset * size, 0]) < 0.00001, f"{part}: wrong row offset"
        assert all(abs(v - scale * size) < 0.00001 for v in vector(row.get("scale"))), f"{part}: wrong row scale"


def assert_setting_feedback(messages, after_sequence, size, radius):
    """Require both read responses after a client-observed query boundary."""
    expected = [f"World marker size ×{float(size)}", f"World marker distance: {float(radius)} blocks"]
    responses = []
    for text in expected:
        matches = [message for message in messages
                   if message["sequence"] > after_sequence and message["text"] == text]
        assert matches, f"Missing fresh setting readback: {text}"
        responses.append(matches[-1])
    return responses


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
    points.append({**points[0], "name": "near", "initials": "N", "display_name": "Near target",
                   "pos": [300, -61, 300]})
    points.append({**points[0], "name": "far", "initials": "F", "display_name": "Far target",
                   "pos": [620, -61, 300]})
    (directory / "minecraft$overworld.json").write_text(json.dumps([
        {"list_name": "marker_fixture", "n": 1, "waypoints": points}]))
    evidence = {"schemaVersion": 1, "scope": "candidate-world-marker-acceptance-smoke",
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

        # Stationary setting checks are separate from the forecast/movement phase.
        for name in names:
            execute(f"tp {name} 300 -61 300")
        time.sleep(0.3)
        settings_before = snapshot()
        observer_rows = {p["name"]: p["worldMarker"]["displays"]
                         for p in settings_before["players"] if p["name"] != names[0]}
        owner = next(p for p in settings_before["players"] if p["name"] == names[0])
        setting_ids = {row["part"]: row["entityId"] for row in owner["worldMarker"]["displays"]}
        evidence["settings"] = {"before": settings_before, "cases": {}}

        def capture_settings(label, size, radius, commands, stable_ids=None, target="a"):
            receipts = [execute(command, names[0]) for command in commands]
            deadline = time.monotonic() + 20
            while True:
                native = snapshot()
                player = next(p for p in native["players"] if p["name"] == names[0])
                try:
                    assert player["target"]["waypoint"] == target, "Unexpected setting target"
                    assert_marker_configuration(player, size, radius, stable_ids)
                    if label == "maximum":
                        centre = [v + 0.5 for v in player["target"]["position"]]
                        assert math.dist(player["worldMarker"]["sampleEye"], centre) > radius, \
                            "Maximum radius must be tested with a projection-active far target"
                    for observer in native["players"]:
                        if observer["name"] in observer_rows:
                            assert observer["worldMarker"]["displays"] == observer_rows[observer["name"]], \
                                "Settings leaked to another owner"
                    break
                except AssertionError:
                    if time.monotonic() >= deadline:
                        raise
                    time.sleep(0.1)
            expected = {p["name"]: [row["entityId"] for row in p["worldMarker"]["displays"]]
                        for p in native["players"]}
            client = await_display_ids(expected)
            # Flush queued feedback, then observe an ordered packet boundary before querying.
            # This prevents older setter responses from satisfying a broken getter.
            probe.send("commands.feedback-barrier")
            probe.wait("commands.feedback.completed")
            boundary = "world-marker-query:" + label
            execute("tellraw " + names[0] + " " + json.dumps({"text": boundary}))
            deadline = time.monotonic() + 20
            while True:
                messages = packets()[names[0]]["chat"]
                markers = [message for message in messages if message["text"] == boundary]
                if markers:
                    after_sequence = markers[-1]["sequence"]
                    break
                assert time.monotonic() < deadline, "Missing client-observed query boundary"
                time.sleep(0.1)
            receipts.extend(execute(command, names[0]) for command in
                ("wp navigate config world_marker size", "wp navigate config world_marker distance"))
            deadline = time.monotonic() + 20
            while True:
                client = packets()
                try:
                    readback = assert_setting_feedback(client[names[0]]["chat"], after_sequence, size, radius)
                    break
                except AssertionError:
                    if time.monotonic() >= deadline:
                        raise
                    time.sleep(0.1)
            evidence["settings"]["cases"][label] = {"size": size, "radius": radius,
                "commands": receipts, "native": native, "client": client,
                "queryBoundarySequence": after_sequence, "readback": readback}

        for label, size, radius, target in (("minimum", 0.25, 1, "a"), ("maximum", 4, 32, "far"),
                                           ("fractional", 1.75, 8.5, "a")):
            capture_settings(label, size, radius,
                [f"wp navigate minecraft:overworld marker_fixture {target} world_marker",
                 f"wp navigate config world_marker size {size}",
                 f"wp navigate config world_marker distance {radius}"], setting_ids, target)
        capture_settings("retarget-near", 1.75, 8.5,
            ["wp navigate minecraft:overworld marker_fixture near world_marker"], setting_ids, "near")
        capture_settings("retarget-far", 1.75, 8.5,
            ["wp navigate minecraft:overworld marker_fixture a world_marker"], setting_ids)
        execute("wp navigate disable world_marker", names[0])
        preservation_disabled = snapshot()
        assert not next(p for p in preservation_disabled["players"] if p["name"] == names[0])["worldMarker"]
        evidence["settings"]["preservationDisabled"] = preservation_disabled
        capture_settings("reenabled-preserves-settings", 1.75, 8.5, ["wp navigate use world_marker"])
        execute("wp navigate disable world_marker", names[0])
        disabled = snapshot()
        assert not next(p for p in disabled["players"] if p["name"] == names[0])["worldMarker"]
        evidence["settings"]["disabled"] = disabled
        capture_settings("configured-while-disabled", 0.5, 6.5,
            ["wp navigate config world_marker size 0.5", "wp navigate config world_marker distance 6.5",
             "wp navigate use world_marker"])
        owner = next(p for p in snapshot()["players"] if p["name"] == names[0])
        setting_ids = {row["part"]: row["entityId"] for row in owner["worldMarker"]["displays"]}
        capture_settings("reset-defaults", 1, 12, ["wp navigate config world_marker size 1",
            "wp navigate config world_marker distance 12"], setting_ids)

        capture_settings("saved-before-reconnect", 1.75, 8.5,
            ["wp navigate config world_marker size 1.75", "wp navigate config world_marker distance 8.5"], setting_ids)
        saved_identities = {p["name"]: p["uuid"] for p in snapshot()["players"]}
        driver.close()
        driver = None
        deadline = time.monotonic() + 20
        while True:
            disconnected = snapshot()
            if not disconnected["players"]:
                break
            assert time.monotonic() < deadline, "Saved-setting actors did not disconnect"
            time.sleep(0.1)
        assert all(disconnected["resources"][key] == 0 for key in ("entities", "sessions", "markerMotionTracks"))
        evidence["settings"]["disconnected"] = disconnected
        driver = BotDriver(runtime.bot["path"], create_root(root / "reconnect-bots"),
                           probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 120)
        reconnected = snapshot()
        assert validate_ready(ready, reconnected["players"], count, "vanilla").status == Status.PASS
        assert {p["name"]: p["uuid"] for p in reconnected["players"]} == saved_identities, "Reconnect changed actor identities"
        for name in names:
            execute(f"tp {name} 300 -61 300")
        await_labels({name: labels("Target A", 28) for name in names})
        time.sleep(0.3)
        reconnected = snapshot()
        observer_rows = {p["name"]: p["worldMarker"]["displays"]
                         for p in reconnected["players"] if p["name"] != names[0]}
        evidence["settings"]["reconnected"] = reconnected
        capture_settings("restored-after-reconnect", 1.75, 8.5, [])
        execute("wp navigate disable", names[0])
        capture_settings("new-session-defaults", 1, 12,
            ["wp navigate minecraft:overworld marker_fixture a world_marker"])

        # Private commands resolve each actor's own store, even when both actors are operators.
        private_command = ('wp private add minecraft:overworld marker_private 320 -61 320 Home H 44CCFF 0 false '
                           '"" "" icon minecraft:diamond')
        private_add = execute(private_command, names[0])
        capture_settings("private-target", 1, 12,
            ["wp private navigate minecraft:overworld marker_private Home world_marker"], target="Home")
        private_before = snapshot()
        outsider_before = next(p for p in private_before["players"] if p["name"] == names[1])
        before_chat = packets()[names[1]]["chat"]
        chat_sequence = max((value["sequence"] for value in before_chat), default=0)
        rejection = execute("wp private navigate minecraft:overworld marker_private Home world_marker", names[1])
        deadline = time.monotonic() + 20
        while True:
            private_packets = packets()
            fresh_errors = [value for value in private_packets[names[1]]["chat"]
                if value["sequence"] > chat_sequence and
                ("has no lists yet." in value["text"] or "No list called marker_private" in value["text"])]
            if fresh_errors:
                break
            assert time.monotonic() < deadline, "Missing fresh Private owner-isolation error feedback"
            time.sleep(0.1)
        private_after = snapshot()
        outsider_after = next(p for p in private_after["players"] if p["name"] == names[1])
        assert outsider_after["target"] == outsider_before["target"], "Outsider accessed another owner's Private target"
        assert outsider_after["worldMarker"]["displays"] == outsider_before["worldMarker"]["displays"]
        owner_before = next(p for p in private_before["players"] if p["name"] == names[0])
        owner_after = next(p for p in private_after["players"] if p["name"] == names[0])
        assert owner_after["worldMarker"]["displays"] == owner_before["worldMarker"]["displays"]
        owner_private_ids = {str(row["entityId"]) for row in owner_after["worldMarker"]["displays"]}
        outsider_ids = set(private_packets[names[1]]["textDisplays"]) | set(private_packets[names[1]]["itemDisplays"])
        assert not owner_private_ids & outsider_ids, "Private marker packets reached another owner"
        evidence["privateOwnerIsolation"] = {"addCommand": private_add, "rejectedCommand": rejection,
            "before": private_before, "after": private_after, "client": private_packets, "errors": fresh_errors}
        capture_settings("shared-after-private", 1, 12,
            ["wp navigate minecraft:overworld marker_fixture a world_marker"])

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
