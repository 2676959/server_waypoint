"""Native command/storage probe smoke, with immutable release bytes and disk fixtures."""
import argparse
from dataclasses import replace
import json
from pathlib import Path
import sys
import time
import uuid
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready
from models import Status
from contracts import atomic_write, load_release, load_runtimes
from controller import create_root
from processes import stop_owned
from scenarios.storage_commands import make_dataset, canonical_saved, persisted_state, canonical_snapshot
from worlds import install_server, boot


def execute(probe, command, actor="console"):
    probe.send("commands.execute", {"commands": [command], "actor": actor})
    return probe.wait("commands.executed")["data"]


def snapshot(probe):
    probe.send("players.snapshot")
    return probe.wait("players.snapshot")["data"]


def run(root, tier, behavior=False):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])})
    artifact = next(a for a in load_release(tools / "config/releases/4.0.0.json") if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-cache") / artifact.name
    if not release.exists():
        release = Path("/Volumes/ssd/sw-performance-m0-audit/artifacts") / artifact.name
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    install_server(runtime, server, runtime.probe["path"], release)
    dataset = make_dataset(tier)
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    for dimension, lists in dataset.items():
        (directory / (dimension.replace(":", "$") + ".json")).write_text(json.dumps(lists, indent=4) + "\n")
    atomic_write(root / "dataset.json", {"schemaVersion": 1, "tier": tier, "dimensions": dataset, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"], "botSha256": sha256(runtime.bot["path"]), "serverSha256": runtime.server_sha256, "javaSha256": runtime.java_sha256})
    process, probe = boot(runtime, server, False, artifact.sha256)
    try:
        probe.send("commands.tree")
        tree = probe.wait("commands.tree")["data"]
        atomic_write(root / "command-tree.json", {"schemaVersion": 1, **tree})
        probe.send("players.snapshot")
        loaded = probe.wait("players.snapshot")["data"]
        atomic_write(root / "loaded.json", {"schemaVersion": 1, **loaded})
        expected_count = sum(len(value["waypoints"]) for lists in dataset.values() for value in lists)
        if sum(len(value["waypoints"]) for value in loaded["waypointLists"]) != expected_count:
            raise ValueError("native dataset load count mismatch")
        if any(value["revision"] != 7 for value in loaded["waypointLists"]):
            raise ValueError("native dataset load revision mismatch")
        probe.send("commands.execute", {"commands": ["wp help", "wp list minecraft:overworld perf_00 limit 1"]})
        executed = probe.wait("commands.executed")["data"]
        atomic_write(root / "executed.json", {"schemaVersion": 1, **executed})
        if not executed.get("callbacks") or executed["callbacks"][0]["path"] != "wp help":
            raise ValueError("native command callback execution evidence missing")
        if behavior:
            config = server / "plugins/ServerWaypoint/config.json"
            settings = json.loads(config.read_text())
            settings["CommandPermission"].update(add=2, edit=2, remove=2)
            config.write_text(json.dumps(settings, indent=4) + "\n")
            execute(probe, "wp reload")
            bot_root = create_root(root / "bot")
            driver = BotDriver(runtime.bot["path"], bot_root, str(uuid.uuid4()), str(uuid.uuid4()))
            try:
                port = next(line.split("=", 1)[1] for line in (server / "server.properties").read_text().splitlines() if line.startswith("server-port="))
                driver.send("start", {"address": f"127.0.0.1:{port}", "count": 1, "mode": "compatible"})
                ready = driver.wait("ready", 150)
                native = snapshot(probe)
                if validate_ready(ready, native["players"], 1, "compatible").status != Status.PASS:
                    raise ValueError("native actor readiness failed")
                name = ready["bots"][0]["name"]
                before = snapshot(probe)["waypointLists"]
                driver.send("action", {"actionId": "denied-add", "bot": name, "operation": "command", "command": "/wp add minecraft:overworld denied"})
                driver.wait("action_result")
                after = snapshot(probe)["waypointLists"]
                if before != after:
                    raise ValueError("permission-denied add changed state")
                atomic_write(root / "permission-denied.json", {"schemaVersion": 1, "before": before, "after": after})
                execute(probe, f"op {name}")
                patch = [{"operation": 0, "value": None} for _ in range(10)]
                patch[4] = {"operation": 1, "value": 1122867}
                driver.send("action", {"actionId": "stale-edit", "bot": name, "operation": "edit", "edit": {"requestId": 959, "dimension": "minecraft:overworld", "list": "perf_00", "waypoint": "wp_00000", "revision": 6, "patch": patch}})
                driver.wait("action_result")
                time.sleep(0.2)
                driver.send("barrier")
                driver.wait("rate_sample")
                summaries = [event["data"] for event in driver.events if event["type"] == "packet_summary"]
                replies = [packet for peer in summaries[-1]["peers"] for packet in peer["packets"] if packet.get("messageTypeId") == 3]
                if not replies:
                    raise ValueError("native stale edit reply missing")
                reply = bytes.fromhex(replies[-1]["bodyHex"])
                if int.from_bytes(reply[:8], "big") != 959 or reply[8] != 1 or int.from_bytes(reply[-4:], "big", signed=True) != 7:
                    raise ValueError("stale edit was not rejected at revision 7")
                if snapshot(probe)["waypointLists"] != before:
                    raise ValueError("stale edit changed state")
                atomic_write(root / "stale-edit.json", {"schemaVersion": 1, "replyHex": reply.hex(), "before": before, "after": snapshot(probe)["waypointLists"]})
                for command in ("wp add minecraft:overworld perf_test", "wp add minecraft:overworld perf_test 12 -61 -16 origin P 112233 0 true", "wp edit waypoint minecraft:overworld perf_test origin set identifier renamed", "wp remove minecraft:overworld perf_test renamed"):
                    execute(probe, command)
                probe.send("commands.restore-tokens")
                tokens = probe.wait("commands.restore-tokens")["data"]["tokens"]
                if len(tokens) != 1:
                    raise ValueError("exactly one native restore token required")
                execute(probe, "wp restore " + tokens[0])
                restored = snapshot(probe)["waypointLists"]
                execute(probe, "wp restore " + tokens[0])
                replayed = snapshot(probe)["waypointLists"]
                if restored != replayed:
                    raise ValueError("restore replay mutated state")
                record = next(value for value in replayed if value["name"] == "perf_test")
                if record["waypoints"] != ["renamed"] or record["revision"] != 5:
                    raise ValueError("native rename/remove/restore state incorrect")
                atomic_write(root / "restore.json", {"schemaVersion": 1, "restored": restored, "replayed": replayed})
            finally:
                if driver.stream.failures:
                    driver.abort()
                else:
                    driver.close()
        print(f"native {tier}: {expected_count} waypoints; command tree retained", flush=True)
    finally:
        stop_owned(process, 15)
    if behavior:
        expected = {dimension: list(lists) for dimension, lists in dataset.items()}
        expected["minecraft:overworld"].append({"list_name": "perf_test", "n": 5, "waypoints": [{"name": "renamed", "initials": "P", "pos": [12, -61, -16], "color": "#112233", "yaw": 0, "global": True, "keywords": [], "description": ""}]})
        actual = {path.stem.replace("$", ":"): json.loads(path.read_text()) for path in directory.glob("*.json")}
        decision = persisted_state(canonical_saved(expected), canonical_saved(actual))
        atomic_write(root / "saved.json", {"schemaVersion": 1, "decision": decision, "expected": canonical_saved(expected), "actual": canonical_saved(actual)})
        if decision.status != Status.PASS:
            raise ValueError(decision.reasons)
        process, probe = boot(runtime, server, False, artifact.sha256)
        try:
            reopened = snapshot(probe)
            atomic_write(root / "reopened.json", {"schemaVersion": 1, **reopened})
            if canonical_snapshot(reopened["waypointLists"]) != canonical_snapshot(replayed):
                raise ValueError("reopened committed names/revisions differ")
        finally:
            stop_owned(process, 15)
        print(f"native {tier}: exact disk contents and restart verified", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--tier", choices=("small", "medium"), default="small")
    parser.add_argument("--behavior", action="store_true")
    args = parser.parse_args()
    run(args.root, args.tier, args.behavior)
