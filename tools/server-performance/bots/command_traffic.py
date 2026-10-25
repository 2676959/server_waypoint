"""Scheduled native command smoke; acknowledged roundtrip/timed-gate evidence is separate."""
from collections import Counter
from pathlib import Path
import time
import uuid

from artifacts import sha256
from bot_driver import BotDriver, validate_ready, validate_rates
from contracts import atomic_write
from controller import create_root
from models import Status
from profiles import whole_process_sample
from scenarios.storage_commands import planned_actions


def run_traffic(root, server, runtime, probe, tier, seconds=5):
    if not 1 <= seconds <= 10:
        raise ValueError("this smoke is bounded to ten seconds; it is not the timed audit runner")
    root = create_root(root)
    port = next(line.split("=", 1)[1] for line in (Path(server) / "server.properties").read_text().splitlines() if line.startswith("server-port="))
    count = 1 if tier == "small" else 10
    driver = BotDriver(runtime.bot["path"], root, probe.stream.run_id, probe.stream.attempt_id)
    evidence = {"schemaVersion": 1, "tier": tier, "plannedSeconds": seconds, "scope": "scheduled-action-smoke", "botSha256": sha256(runtime.bot["path"])}
    try:
        driver.send("start", {"address": f"127.0.0.1:{port}", "count": count, "mode": "vanilla"})
        ready = driver.wait("ready", 150)
        probe.send("players.snapshot")
        initial = probe.wait("players.snapshot")["data"]
        decision = validate_ready(ready, initial["players"], count, "vanilla")
        if decision.status != Status.PASS:
            raise ValueError(decision.reasons)
        actors = [bot["name"] for bot in ready["bots"]]
        # This throughput smoke declares operator actors to avoid vanilla chat-spam
        # admission. Permission-denial behavior has its own non-operator fixture.
        probe.send("commands.execute", {"commands": [f"op {actor}" for actor in actors]})
        probe.wait("commands.executed")
        actions = planned_actions(tier, actors, seconds, str(uuid.uuid4()))
        evidence.update(ready=ready, actorPermission="operator (vanilla chat-spam exemption)", expected=actions)
        started = time.monotonic()
        index = 0
        process_samples = []
        next_sample = 0
        while time.monotonic() - started < seconds:
            elapsed = time.monotonic() - started
            while index < len(actions) and actions[index][0] <= elapsed:
                driver.send("action", actions[index][1], timeout=0.1)
                index += 1
            driver.poll()
            if driver.stream.failures:
                raise RuntimeError("; ".join(driver.stream.failures))
            if elapsed >= next_sample:
                process_samples.append(whole_process_sample(driver.process.pid))
                next_sample += 1
            time.sleep(0.005)
        if index != len(actions):
            raise ValueError("controller did not offer the declared workload")
        driver.send("barrier")
        rates = driver.wait("rate_sample", 15)
        evidence.update(rates=rates, driverSamples=process_samples, controllerElapsedSeconds=time.monotonic()-started)
        if validate_rates(rates, 5).status != Status.PASS:
            raise ValueError("driver overloaded/stalled during scheduled smoke")
        expected = Counter((action["bot"], action["command"][1:]) for _, action in actions)
        actual = Counter()
        native_events = []
        drain_started = time.monotonic()
        deadline = drain_started + 10
        first_count = None
        while actual != expected:
            probe.send("commands.execute", {"commands": []})
            callbacks = probe.wait("commands.executed")["data"]
            native_events.extend(callbacks["callbacks"])
            actual.update((event["actor"], event["input"]) for event in callbacks["callbacks"])
            if first_count is None:
                first_count = sum(actual.values())
            evidence["nativeCallbacks"] = native_events
            evidence["nativeFirstDrainCount"] = first_count
            evidence["nativeDrainSeconds"] = time.monotonic() - drain_started
            if callbacks["droppedCallbacks"] or actual - expected:
                raise ValueError("native callbacks were dropped or unexpected actor/command executed")
            if actual == expected:
                break
            if time.monotonic() >= deadline:
                raise ValueError("native callbacks did not fulfill scheduled demand by drain deadline")
            time.sleep(0.05)
        probe.send("players.snapshot")
        final = probe.wait("players.snapshot")["data"]
        if validate_ready(ready, final["players"], count, "vanilla").status != Status.PASS:
            raise ValueError("native actor identities changed during scheduled demand")
        mutations = sum(" set color " in action["command"] for _, action in actions)
        before = next(value for value in initial["waypointLists"] if value["dimension"] == "minecraft:overworld" and value["name"] == "perf_00")
        after = next(value for value in final["waypointLists"] if value["dimension"] == "minecraft:overworld" and value["name"] == "perf_00")
        if after["revision"] != before["revision"] + mutations or after["waypoints"] != before["waypoints"]:
            raise ValueError("scheduled mutations did not preserve names and exact revisions")
        evidence.update(final=final, mutations=mutations, fulfilled=True,
                        actionLatencyScope="client-issued only; no client-roundtrip claim",
                        performanceAcceptance="INCONCLUSIVE: smoke duration and acknowledged client latency missing")
        return evidence
    except BaseException as error:
        evidence.update(fulfilled=False, error=str(error))
        raise
    finally:
        atomic_write(root / "traffic.json", evidence)
        if driver.stream.failures:
            driver.abort()
        else:
            driver.close()
