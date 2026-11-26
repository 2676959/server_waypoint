"""Two prepared terrain rasters, native restoration, client patches and persistence.

The oracle is a fixture, not candidate-rendered output. Owners remain outside the
prepared regions so vanilla exploration cannot overwrite their known pixels.
"""
import base64
import hashlib
import json
from pathlib import Path
import time
import uuid

from artifacts import verify_artifact
from bot_driver import BotDriver, validate_ready
from contracts import atomic_write
from controller import create_root
from models import Decision, Status
from processes import stop_owned
from reporting import combine_decisions
from scenarios.navigation import terrain_roundtrip
from worlds import install_server, clone_baseline, boot
from bots.minimal_functional import check, summarize

REGIONS = {
    "a": {"dimension": "minecraft:overworld", "centerX": 512, "centerZ": 512, "scale": 2},
    "b": {"dimension": "minecraft:overworld", "centerX": -512, "centerZ": -512, "scale": 2},
}


def expected_pixels(owner, region):
    if owner not in (0, 1) or region not in REGIONS:
        raise ValueError("unknown terrain fixture identity")
    base = 5 + owner * 8 + (4 if region == "b" else 0)
    return bytes(base + (x + y) % 4 for y in range(128) for x in range(128))


def fixture_file(server, map_id, region):
    # Java UUID.nameUUIDFromBytes uses MD5 with version/variant bits; GSON
    # serializes the Region record in this declared field order without spaces.
    encoded = json.dumps(REGIONS[region], separators=(",", ":")).encode()
    key = str(uuid.UUID(bytes=hashlib.md5(encoded).digest(), version=3))
    return server / "world/server_waypoint/navigation-map-terrain" / str(map_id) / (key + ".json")


def judge_owner(identity, map_id, region, pixels, player, client):
    if player is None:
        return Decision(Status.BLOCKED, ("native owner missing",))
    if player.get("uuid") != identity or set(player.get("methods", [])) != {"map"}:
        return Decision(Status.FAIL, ("terrain owner or method identity differs",))
    target = player.get("target") or {}
    if any(target.get(k) != v for k, v in {"dimension": "minecraft:overworld", "list": "terrain_fixture", "waypoint": region}.items()):
        return Decision(Status.FAIL, ("terrain target differs",))
    maps = [item for item in player.get("items", []) if item.get("method") == "map"]
    if len(maps) != 1 or maps[0].get("amount") != 1 or len(player.get("items", [])) != 1:
        return Decision(Status.FAIL, ("terrain map item missing or duplicated",))
    state = maps[0].get("mapState", {})
    if state.get("id") != map_id:
        return Decision(Status.FAIL, ("owner map identity changed",))
    received = next((m for m in client.get("maps", []) if m.get("id") == map_id), {})
    return terrain_roundtrip(REGIONS[region], pixels, state, received)


def run(root, binding, baseline):
    runtime, artifact, release = binding
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    clone_baseline(baseline, server)
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    points = [{"name": name, "display_name": "Terrain " + name.upper(), "initials": name.upper(), "pos": pos,
               "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": "Prepared pixel fixture"}
              for name, pos in (("origin", [0, -61, 0]), ("a", [320, -61, 320]), ("b", [-320, -61, -320]))]
    (directory / "minecraft$overworld.json").write_text(json.dumps([{"list_name": "terrain_fixture", "n": 7, "waypoints": points}]))
    evidence = {"schemaVersion": 1, "checks": [], "steps": [], "oracle": "independent owner/region checkerboard fixture; no geographic-color claim"}
    process = driver = probe = None
    names, identities, ids, expected = [], {}, {}, {}
    serial = 0

    def snapshot():
        probe.send("navigation.snapshot")
        return probe.wait("navigation.snapshot")["data"]

    def console(command):
        probe.send("commands.execute", {"commands": [command]})
        return probe.wait("commands.executed")["data"]

    def actions(command=None, slots=None, owners=None):
        nonlocal serial
        selected = names if owners is None else owners
        for name in selected:
            serial += 1
            action_id = "terrain-minimal-" + str(serial)
            data = {"actionId": action_id, "bot": name}
            data.update({"operation": "command", "command": command, "acknowledged": True} if command else {"operation": "select", "slot": slots[name]})
            driver.send("action", data)
            receipt = driver.wait("action_result", 30)
            if receipt.get("actionId") != action_id or receipt.get("error") or receipt.get("success") is False:
                raise RuntimeError("terrain native action receipt absent/mismatched")

    def packets():
        driver.send("barrier")
        value = driver.wait("packet_summary", 30)
        rate = driver.wait("rate_sample", 30)
        if rate.get("pending") or rate.get("failed"):
            raise RuntimeError("terrain action barrier incomplete")
        return value["effects"]

    def connect(phase):
        nonlocal driver, names
        probe.send("commands.tree")
        probe.wait("commands.tree")
        driver = BotDriver(runtime.bot["path"], create_root(root / (phase + "-bots")), probe.stream.run_id, probe.stream.attempt_id)
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": 2, "mode": "vanilla"})
        ready = driver.wait("ready", 150)
        native = snapshot()
        if validate_ready(ready, native["players"], 2, "vanilla").status != Status.PASS:
            raise RuntimeError("terrain actor readiness incomplete")
        names = sorted(p["name"] for p in native["players"])
        for player in native["players"]:
            name = player["name"]
            if name in identities and identities[name] != player["uuid"]:
                raise RuntimeError("terrain restored actor identity differs")
            identities[name] = player["uuid"]
            console("op " + name)
            console(f"tp {name} 0 -61 0")
        actions("/wp help")

    def observe(label, destinations):
        deadline = time.monotonic() + 12
        while True:
            native, client = snapshot(), packets()
            owners = {p["name"]: p for p in native["players"]}
            decisions = {name: judge_owner(identities[name], ids[name], destinations[name], expected[name][destinations[name]], owners.get(name), client.get(name, {})) for name in names}
            decision = combine_decisions(decisions.values())
            if decision.status == Status.PASS or time.monotonic() >= deadline:
                row = {"label": label, "destinations": destinations, "native": native, "client": client, "decisions": decisions}
                evidence["steps"].append(row)
                atomic_write(root / "terrain.json", evidence)
                return decision
            time.sleep(0.1)

    def saved():
        failures = []
        for name in names:
            for region in REGIONS:
                path = fixture_file(server, ids[name], region)
                try:
                    data = json.loads(path.read_text())
                    valid = data.get("region") == REGIONS[region] and base64.b64decode(data["colors"], validate=True) == expected[name][region]
                except (OSError, ValueError, KeyError):
                    valid = False
                if not valid:
                    failures.append(f"saved terrain differs: {name}/{region}")
        return Decision(Status.FAIL if failures else Status.PASS, tuple(failures))

    def record(name, decisions):
        value = combine_decisions(decisions)
        check(evidence, name, value.status.value, value.reasons)
        atomic_write(root / "terrain.json", evidence)

    try:
        process, probe = boot(runtime, server, False, artifact.sha256)
        console("difficulty peaceful")
        connect("initial")
        actions("/wp navigate minecraft:overworld terrain_fixture origin map")
        native = snapshot()
        for index, name in enumerate(names):
            player = next(p for p in native["players"] if p["name"] == name)
            maps = [item for item in player["items"] if item["method"] == "map"]
            if len(maps) != 1:
                raise RuntimeError("terrain fixture map allocation missing")
            ids[name] = maps[0]["mapState"]["id"]
            expected[name] = {region: expected_pixels(index, region) for region in REGIONS}
            for region in REGIONS:
                path = fixture_file(server, ids[name], region)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps({"region": REGIONS[region], "colors": base64.b64encode(expected[name][region]).decode()}))
        if len(set(ids.values())) != 2:
            raise RuntimeError("terrain owners share map identity")
        atomic_write(root / "expected.json", {"schemaVersion": 1, "ids": ids, "owners": identities,
            "pixels": {name: {region: base64.b64encode(colors).decode() for region, colors in rows.items()} for name, rows in expected.items()}})
        actions(slots={p["name"]: next(i["slot"] for i in p["items"] if i["method"] == "map") for p in native["players"]})
        region_decisions = []
        previous_region = None
        for region in ("a", "b", "a"):
            if previous_region is not None:
                # Make a no-op save implementation observable: the active map
                # must overwrite this stale file when switching away.
                for name in names:
                    fixture_file(server, ids[name], previous_region).write_text(json.dumps({
                        "region": REGIONS[previous_region], "colors": base64.b64encode(bytes(16384)).decode()}))
            actions("/wp navigate minecraft:overworld terrain_fixture " + region)
            region_decisions.append(observe("initial-" + region, {name: region for name in names}))
            previous_region = region
        record("regions", region_decisions)
        actions("/wp navigate minecraft:overworld terrain_fixture b", owners=names[:1])
        record("isolation", [observe("one-owner-retarget", {name: "b" if name == names[0] else "a" for name in names})])
        actions("/wp navigate minecraft:overworld terrain_fixture a")
        observe("before-stop-a", {name: "a" for name in names})
        driver.close()
        driver = None
        stop_owned(process, 30)
        process = None
        record("save", [saved()])
        process, probe = boot(runtime, server, False, artifact.sha256)
        connect("reopened")
        native = snapshot()
        actions(slots={p["name"]: next(i["slot"] for i in p["items"] if i["method"] == "map") for p in native["players"]})
        reopened = [observe("restored-a", {name: "a" for name in names})]
        actions("/wp navigate minecraft:overworld terrain_fixture b")
        reopened.append(observe("restored-b", {name: "b" for name in names}))
        record("reopen", reopened)
    except Exception as error:
        check(evidence, "execution", "BLOCKED", [f"{type(error).__name__}: {error}"])
    finally:
        try:
            if driver is not None:
                driver.abort()
        finally:
            if process is not None:
                stop_owned(process, 30)
            result = summarize("terrain", evidence["checks"])
            result["observations"] = evidence
            atomic_write(root / "terrain.json", evidence)
            atomic_write(root / "minimal-functional.json", result)
    return result
