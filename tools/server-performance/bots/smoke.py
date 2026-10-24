"""Native connected-player smoke; no timed performance or wire coverage claim."""
import argparse
from dataclasses import replace
from pathlib import Path
import sys
import time
import uuid
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256
from bot_driver import BotDriver, validate_ready, validate_rates, validate_actions
from contracts import load_runtimes, atomic_write
from controller import create_root
from models import Status
from profiles import whole_process_sample
from processes import stop_owned
from worlds import install_server, boot


def run(root, binary, release):
    runtime = next(r for r in load_runtimes(Path(__file__).resolve().parents[1] / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])})
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    with (server / "server.properties").open("a") as stream:
        stream.write("max-players=64\nnetwork-compression-threshold=-1\n")
    process, probe = boot(runtime, server, False, sha256(release))
    try:
        probe.send("players.snapshot")
        probe.wait("players.snapshot", 10)
        for count in (1, 10, 50):
            bot_root = create_root(root / f"bots-{count}")
            driver = BotDriver(binary, bot_root, str(uuid.uuid4()), str(uuid.uuid4()))
            expected, results, process_samples = [], [], []
            try:
                driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
                ready = driver.wait("ready", 150)
                probe.send("players.snapshot")
                before = probe.wait("players.snapshot")["data"]
                decision = validate_ready(ready, before["players"], count, "vanilla")
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
                if validate_rates(rates, 5).status != Status.PASS:
                    raise ValueError("driver did not complete offered actions")
                time.sleep(0.5)
                probe.send("players.snapshot")
                observed = probe.wait("players.snapshot")["data"]
                for player in observed["players"]:
                    if player["inventory"] != [{"slot": 1, "type": "minecraft:stone", "count": 1}]:
                        raise ValueError(f"native inventory move/conservation failed: {player}")
                actions = validate_actions(expected, results, {p["name"]: p for p in observed["players"]})
                atomic_write(bot_root / "evidence.json", {"schemaVersion": 1, "count": count, "ready": ready, "serverBefore": before, "serverAfter": observed, "rates": rates, "expected": expected, "results": results, "actions": actions, "driverSamples": process_samples, "binarySha256": sha256(binary), "probeSha256": runtime.probe["sha256"], "releaseSha256": sha256(release)})
                if actions.status != Status.PASS:
                    raise ValueError(actions.reasons)
            finally:
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
            atomic_write(bot_root / "disconnected.json", {"schemaVersion": 1, **disconnected})
            print(f"native {count} bots: identities/actions/disconnect verified", flush=True)
    finally:
        stop_owned(process, 15)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--release", type=Path, required=True)
    args = parser.parse_args()
    run(args.root, args.binary.resolve(), args.release.resolve())
