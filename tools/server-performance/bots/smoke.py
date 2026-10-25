"""Native connected-player smoke; no timed performance or wire coverage claim."""
import argparse
from dataclasses import replace
from pathlib import Path
import sys
import time
import uuid
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready, validate_rates, validate_actions
from contracts import load_runtimes, load_release, atomic_write
from controller import create_root
from models import Status
from profiles import whole_process_sample
from processes import stop_owned
from worlds import install_server, boot


def run(root, binary, release, wire=False, adversarial=False):
    runtime = next(r for r in load_runtimes(Path(__file__).resolve().parents[1] / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])})
    artifact = next(a for a in load_release(Path(__file__).resolve().parents[1] / "config/releases/4.0.0.json") if a.name == runtime.artifact)
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    with (server / "server.properties").open("a") as stream:
        stream.write("max-players=64\nnetwork-compression-threshold=-1\n")
    process, probe = boot(runtime, server, False, sha256(release))
    try:
        if wire:
            process.process.stdin.write(b"wp add minecraft:overworld perf\nwp add minecraft:overworld perf 0 -61 0 origin\n")
            process.process.stdin.flush()
            time.sleep(1)
        probe.send("players.snapshot")
        probe.wait("players.snapshot", 10)
        for mode, count in ([(mode, 1) for mode in ("vanilla", "compatible", "incompatible")] if wire else [("vanilla", count) for count in (1, 10, 50)]):
            bot_root = create_root(root / f"bots-{mode}-{count}")
            driver = BotDriver(binary, bot_root, str(uuid.uuid4()), str(uuid.uuid4()))
            expected, results, process_samples = [], [], []
            aborted = False
            try:
                driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": mode})
                ready = driver.wait("ready", 150)
                probe.send("players.snapshot")
                before = probe.wait("players.snapshot")["data"]
                decision = validate_ready(ready, before["players"], count, mode)
                if decision.status != Status.PASS:
                    raise ValueError(decision.reasons)
                for index, bot in enumerate(ready["bots"]):
                    name = bot["name"]
                    process.process.stdin.write(f"clear {name}\ntp {name} {index * 4} -60 0\ngive {name} minecraft:stone 1\n".encode())
                process.process.stdin.flush()
                time.sleep(2)
                probe.send("players.reset")
                probe.wait("players.reset")
                for bot in ready["bots"]:
                    for operation in ("command", "walk", "inventory"):
                        action = {"actionId": str(uuid.uuid4()), "bot": bot["name"], "operation": operation, "command": "/help", "milliseconds": 250}
                        expected.append(action)
                        driver.send("action", action)
                        results.append(driver.wait("action_result"))
                        process_samples.append(whole_process_sample(driver.process.pid))
                driver.send("barrier")
                rates = driver.wait("rate_sample")
                wire_events = [e["data"] for e in driver.events if e["type"] == "packet_summary"]
                if wire:
                    peers = wire_events[-1]["peers"]
                    downloads = [p for peer in peers for p in peer["packets"] if p.get("messageTypeId") == 0]
                    if mode == "compatible" and (not downloads or any(not p["checksumVerified"] for p in downloads)):
                        raise ValueError("compatible native snapshot download missing")
                    if mode == "compatible":
                        if before["waypointLists"] != downloads[-1]["waypointLists"] or not before["waypointLists"]:
                            raise ValueError("downloaded names/revisions differ from native committed state")
                    if mode != "compatible" and downloads:
                        raise ValueError("unnegotiated client received chunked data")
                if validate_rates(rates, 5).status != Status.PASS:
                    raise ValueError("driver did not complete offered actions")
                time.sleep(0.5)
                probe.send("players.snapshot")
                observed = probe.wait("players.snapshot")["data"]
                for player in observed["players"]:
                    if player["inventory"] != [{"slot": 1, "type": "minecraft:stone", "count": 1}]:
                        raise ValueError(f"native inventory move/conservation failed: {player}")
                if observed.get("waypointLists") != before.get("waypointLists"):
                    raise ValueError("native wire probe unexpectedly mutated committed waypoints")
                actions = validate_actions(expected, results, {p["name"]: p for p in observed["players"]})
                atomic_write(bot_root / "evidence.json", {"schemaVersion": 1, "count": count, "ready": ready, "serverBefore": before, "serverAfter": observed, "rates": rates, "wire": wire_events, "expected": expected, "results": results, "actions": actions, "driverSamples": process_samples, "binarySha256": sha256(binary), "probeSha256": runtime.probe["sha256"], "releaseSha256": sha256(release)})
                if actions.status != Status.PASS:
                    raise ValueError(actions.reasons)
                if adversarial and mode == "compatible":
                    for index in range(20):
                        driver.send("action", {"actionId": f"burst-{index}", "bot": ready["bots"][0]["name"], "operation": "walk", "milliseconds": 300})
                    for _ in range(20):
                        driver.wait("action_result", 15)
                    driver.send("barrier")
                    burst = driver.wait("rate_sample")
                    atomic_write(bot_root / "overload.json", {"schemaVersion": 1, **burst})
                    if validate_rates(burst, 5).status != Status.INCONCLUSIVE:
                        raise ValueError("queued driver overload was not detected")
                    probe.send("players.malformed")
                    probe.wait("players.malformed")
                    try:
                        driver.wait("error", 5)
                    except RuntimeError as error:
                        if "checksum" not in str(error):
                            raise
                        atomic_write(bot_root / "malformed.json", {"schemaVersion": 1, "rejected": True, "reason": str(error)})
                    else:
                        raise ValueError("malformed native payload did not invalidate bot stream")
                    driver.abort()
                    aborted = True
            finally:
                if not aborted:
                    driver.close()
            deadline = time.monotonic() + 15
            while True:
                probe.send("players.snapshot")
                disconnected = probe.wait("players.snapshot")["data"]
                if not disconnected["players"]:
                    break
                if time.monotonic() > deadline:
                    raise ValueError("players survived disconnect")
                time.sleep(0.1)
            if wire and any(disconnected.get(key) != 0 for key in ("chunkedMessageSessions", "chunkedMessageCapablePlayers", "chunkedRetainedBytes")):
                raise ValueError("protocol buffers/sessions survived disconnect")
            atomic_write(bot_root / "disconnected.json", {"schemaVersion": 1, **disconnected})
            print(f"native {mode} {count} bots: identities/actions/disconnect verified", flush=True)
    finally:
        stop_owned(process, 15)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--wire", action="store_true")
    parser.add_argument("--adversarial", action="store_true")
    args = parser.parse_args()
    run(args.root, args.binary.resolve(), args.release.resolve(), args.wire, args.adversarial)
