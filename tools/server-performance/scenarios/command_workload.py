"""Native scheduled command phases with correlated bot and owner-thread evidence."""
from collections import Counter
import time
from bot_driver import validate_rates
from controller import NativeFailure
from models import Status
from profiles import whole_process_sample
from scenarios.storage_commands import planned_actions, acknowledged_latencies


def summarize_window(raw, warmup_seconds):
    if not raw["samples"] or raw["droppedCallbacks"]:
        raise ValueError("native ticks missing or callback evidence dropped")
    if validate_rates(raw["rates"], 5).status != Status.PASS:
        raise ValueError("driver did not fulfill scheduled demand")
    return {"tickNs": [sample["durationNs"] for sample in raw["samples"]],
            "tickMetric":"native-processing-duration", "warmupSeconds":warmup_seconds,
            "measurementSeconds":raw["seconds"], "offeredActions":len(raw["actions"]),
            "completedActions":len(raw["latencies"]), "actionLatencyNs":raw["latencies"],
            "actionLatencyClock":"client-monotonic", "actionLatencyScope":"client-roundtrip",
            "commandAllocatedBytes":[event["allocatedBytes"] for event in raw["callbacks"]],
            "commandAllocationScope":"server-thread", "completedWithinWindow":raw["completedWithinWindow"],
            "driverRates":raw["rates"]}


def collect_commands(probe, driver, tier, actors, seconds, phase, prefix, mutation_offset=0, evidence_path=None):
    journal = {"schemaVersion":1,"phase":phase,"complete":False}
    try:
        result = _collect_commands(probe,driver,tier,actors,seconds,phase,prefix,mutation_offset,journal)
        journal.update(result,complete=True)
        return result
    except BaseException as error:
        journal["error"] = str(error) or type(error).__name__
        raise
    finally:
        if evidence_path is not None:
            from contracts import atomic_write
            atomic_write(evidence_path,journal)


def _collect_commands(probe, driver, tier, actors, seconds, phase, prefix, mutation_offset, journal):
    # Flush previous callbacks before enabling this phase's tick sampling.
    probe.send("commands.execute", {"commands":[]})
    prior = probe.wait("commands.executed")["data"]
    if prior["droppedCallbacks"]:
        raise ValueError("prior native callback evidence dropped")
    driver.send("barrier")
    baseline = driver.wait("rate_sample")
    actions = planned_actions(tier, actors, seconds, prefix, mutation_offset)
    for _, action in actions:
        action["acknowledged"] = True
    probe.send("scenario.start", {"phase":phase,"eventsPerTick":0,"variant":"plain-0"})
    probe.wait("scenario.started")
    samples, callbacks, results, process_samples = [], [], [], []
    journal.update(samples=samples,callbacks=callbacks,results=results,processSamples=process_samples,actions=[action for _,action in actions])
    dropped = 0
    stopped = None
    rates = None
    expected = Counter((action["bot"], action["command"][1:]) for _, action in actions)
    actual = Counter()
    started = time.monotonic()
    index = 0
    next_sample = 0
    next_drain = 1
    drain_pending = False
    def poll():
        nonlocal dropped, drain_pending, rates, stopped
        for event in driver.poll():
            if event["type"] == "action_result":
                results.append(event["data"])
            elif event["type"] == "rate_sample":
                rates = event["data"]
        if driver.stream.failures:
            raise RuntimeError("; ".join(driver.stream.failures))
        for event in probe.poll():
            data = event["data"]
            if event["type"] == "error":
                raise ValueError(data)
            if event["type"] == "sample":
                if data["phase"] != phase:
                    raise ValueError("native sample has wrong phase")
                samples.append(data)
            elif event["type"] == "commands.executed":
                callbacks.extend(data["callbacks"])
                dropped += data["droppedCallbacks"]
                actual.update((value["actor"], value["input"]) for value in data["callbacks"])
                drain_pending = False
            elif event["type"] == "scenario.stopped":
                stopped = data
        if probe.process.process.poll() is not None:
            raise NativeFailure("native server exited during command phase")
        if driver.process.process.poll() is not None:
            raise RuntimeError("bot exited during command phase")
        if dropped or actual - expected:
            raise ValueError("native command evidence dropped or unexpected callback observed")
    while time.monotonic() - started < seconds:
        elapsed = time.monotonic() - started
        while index < len(actions) and actions[index][0] <= elapsed:
            driver.send("action", actions[index][1], timeout=0.1)
            index += 1
        poll()
        if elapsed >= next_sample:
            process_samples.append({"server":whole_process_sample(probe.process.pid), "driver":whole_process_sample(driver.process.pid)})
            next_sample += 1
        if elapsed >= next_drain and not drain_pending:
            probe.send("commands.execute", {"commands":[]})
            drain_pending = True
            next_drain += 1
        time.sleep(0.005)
    if index != len(actions):
        raise ValueError("controller failed to offer declared command rate")
    completed_within = len(results)
    driver.send("barrier")
    deadline = time.monotonic() + 15
    while rates is None or actual != expected:
        poll()
        if not drain_pending:
            probe.send("commands.execute", {"commands":[]})
            drain_pending = True
        if time.monotonic() >= deadline:
            raise TimeoutError("native command completion/drain deadline")
        time.sleep(0.01)
    probe.send("scenario.stop")
    while stopped is None:
        poll()
        if time.monotonic() >= deadline:
            raise TimeoutError("native command measurement stop deadline")
        time.sleep(0.01)
    # Counters are cumulative across barriers; this phase owns only the delta.
    rates = {**rates, **{key:rates[key]-baseline[key] for key in ("offered","completed","failed")}}
    raw = {"schemaVersion":1,"phase":phase,"actions":[action for _,action in actions],
           "samples":samples,"callbacks":callbacks,"droppedCallbacks":dropped,
           "seconds":stopped["phaseElapsedSeconds"],"scheduledSeconds":seconds,
           "controllerSeconds":time.monotonic()-started,"processSamples":process_samples,
           "completedWithinWindow":completed_within,"rates":rates,
           "latencies":acknowledged_latencies([action for _,action in actions],results)}
    summarize_window(raw, 0)
    return raw
