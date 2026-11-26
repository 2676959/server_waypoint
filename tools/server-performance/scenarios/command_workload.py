"""Native scheduled command phases with correlated bot and owner-thread evidence."""
from collections import Counter
import math
import re
import time
from bot_driver import validate_rates, BotCapacityError, effects_snapshot
from controller import NativeFailure
from models import Status
from profiles import whole_process_sample
from scenarios.storage_commands import planned_actions, acknowledged_latencies, process_allocation, declared_rates


class WorkloadIncomplete(ValueError):
    """Ready actors did not fulfill/validate the declared workload; no numeric pass."""


def offer_due(actions,index,elapsed,offer,poll,can_offer,deadline):
    """Drain acknowledgements while catching up; the bounded queue is backpressure."""
    while index < len(actions) and actions[index][0] <= elapsed:
        poll()
        if time.monotonic() >= deadline:
            break
        action = actions[index][1]
        if not can_offer(action):
            time.sleep(0.005)
            continue
        offer(action)
        index += 1
    return index


def action_log_completions(text, actions):
    expected = Counter()
    for action in actions:
        command = action["command"]
        if command.startswith("/wp edit "):
            expected[("edit","success")] += 1
        elif command == "/wp reload":
            expected[("reload","completed")] += 1
        elif command.startswith("/wp tp "):
            expected[("tp","success")] += 1
    entries = re.findall(r"\[server_waypoint\.actions\] action=(\w+) .*? outcome=(\w+)",text)
    actual = Counter(entries)
    if actual - expected:
        raise ValueError("unexpected/failed asynchronous action-log completion: " + str(actual - expected))
    return entries if actual == expected else None


def validate_suggestion_window(reports, count, prefix, input_text, provider_id):
    expected = {f"{prefix}-{index}" for index in range(count)}
    if len(reports) != count or {value.get("requestId") for value in reports} != expected:
        raise ValueError("suggestion demand has missing/duplicate/wrong request completions")
    for report in reports:
        matching = [value for value in report.get("providerCallbacks",[]) if value.get("input") == input_text
                    and value.get("providerId") == provider_id and value.get("serverObserved") is True
                    and not value.get("exception") and type(value.get("durationNs")) is int and value["durationNs"] >= 0]
        if report.get("input") != input_text or report.get("droppedProviderCallbacks") != 0 or len(matching) != 1:
            raise ValueError("scheduled suggestion lacks an exact native provider completion")


def validate_native_samples(samples, players):
    if type(players) is not int or players <= 0 or not samples:
        raise ValueError("declared native actor count and samples required")
    previous = None
    for sample in samples:
        tick = sample.get("tickIndex")
        if sample.get("onlinePlayers") != players or type(tick) is not int or (previous is not None and tick != previous + 1):
            raise ValueError("native actor count changed or native tick evidence has gaps")
        previous = tick


def validate_command_allocations(callbacks):
    if not callbacks or any(type(value.get("allocatedBytes")) is not int or value["allocatedBytes"] < 0 for value in callbacks):
        raise ValueError("native command-thread allocation unavailable")


def summarize_window(raw, warmup_seconds):
    if not raw["samples"] or raw["droppedCallbacks"]:
        raise WorkloadIncomplete("native ticks missing or callback evidence dropped")
    validate_native_samples(raw["samples"],raw["expectedPlayers"])
    validate_command_allocations(raw["callbacks"])
    lag_limit = max(5,raw["seconds"]) if raw.get("phase") == "WARMUP" else 5
    lateness = raw.get("controllerMaxLatenessSeconds",0)
    if type(lateness) not in (int,float) or not math.isfinite(lateness) or lateness < 0 or lateness > lag_limit or validate_rates(raw["rates"], lag_limit).status != Status.PASS:
        raise WorkloadIncomplete("driver did not fulfill scheduled demand")
    result = {"tickNs": [sample["durationNs"] for sample in raw["samples"]],
            "tickMetric":"native-processing-duration", "warmupSeconds":warmup_seconds,
            "measurementSeconds":raw["seconds"], "offeredActions":len(raw["actions"]),
            "completedActions":len(raw["latencies"]), "actionLatencyNs":raw["latencies"],
            "actionLatencyClock":"client-monotonic", "actionLatencyScope":"client-roundtrip",
            "commandAllocatedBytes":[event["allocatedBytes"] for event in raw["callbacks"]],
            "commandAllocationScope":"server-thread", "completedWithinWindow":raw["completedWithinWindow"],
            "controllerMaxLatenessSeconds":lateness,
            "driverRates":raw["rates"]}
    if "processAllocation" in raw:
        before, after = raw["processAllocation"]
        result["processAllocatedBytes"] = process_allocation(before,after)
        result["processAllocationScope"] = "whole-process; all JVM threads, includes GC/runtime/probe"
    if "suggestionDemand" in raw:
        demand = raw["suggestionDemand"]
        validate_suggestion_window(raw["suggestions"], demand["count"], demand["prefix"], demand["input"], demand["providerId"])
        result["completedSuggestions"] = len(raw["suggestions"])
        result["suggestionRate"] = demand["rate"]
        result["suggestionProviderNs"] = [value["durationNs"] for report in raw["suggestions"] for value in report["providerCallbacks"] if value["providerId"] == demand["providerId"]]
    families = {}
    for event in raw["callbacks"]:
        command = event.get("input","")
        family = "console-execute-as" if command.startswith("execute ") else "console-direct" if event.get("actor") == "CONSOLE" else command.split()[1] if command.startswith("wp ") else "other"
        entry = families.setdefault(family,{"durationNs":[],"allocatedBytes":[]})
        entry["durationNs"].append(event.get("durationNs",0))
        entry["allocatedBytes"].append(event["allocatedBytes"])
    result["nativeCommandFamilies"] = families
    result["consoleCommands"] = len(raw.get("consoleActions",[]))
    result["completedActionsScope"] = "acknowledged bot commands; native console commands recorded separately"
    result["asyncCompletionScope"] = "exact exclusive-window native action-log counts; callback RTT excludes asynchronous commit/teleport/reload delay"
    return result


def collect_commands(probe, driver, tier, actors, seconds, phase, prefix, mutation_offset=0, evidence_path=None, read_commands=None, suggestion=None, mutation_mode="color", console_reads=False):
    journal = {"schemaVersion":1,"phase":phase,"complete":False}
    before = None
    try:
        probe.send("process.snapshot")
        before = probe.wait("process.snapshot")["data"]
        result = _collect_commands(probe,driver,tier,actors,seconds,phase,prefix,mutation_offset,journal,read_commands,suggestion,mutation_mode,console_reads)
        probe.send("process.snapshot")
        after = probe.wait("process.snapshot")["data"]
        process_allocation(before,after)
        result["processAllocation"] = [before,after]
        journal.update(result,complete=True)
        return result
    except BaseException as error:
        journal["error"] = str(error) or type(error).__name__
        raise
    finally:
        if before is not None and "processAllocation" not in journal:
            try:
                probe.send("process.snapshot")
                after = probe.wait("process.snapshot")["data"]
                process_allocation(before,after)
                journal["processAllocation"] = [before,after]
                journal["processAllocationScope"] = "failed-window diagnostic through telemetry response; not a completed measurement"
            except Exception as capture_error:
                journal["allocationCaptureError"] = str(capture_error)
        if evidence_path is not None:
            from contracts import atomic_write
            atomic_write(evidence_path,journal)


def _collect_commands(probe, driver, tier, actors, seconds, phase, prefix, mutation_offset, journal, read_commands=None, suggestion=None, mutation_mode="color", console_reads=False):
    # Flush previous callbacks before enabling this phase's tick sampling.
    probe.send("commands.execute", {"commands":[]})
    prior = probe.wait("commands.executed")["data"]
    if prior["droppedCallbacks"]:
        raise ValueError("prior native callback evidence dropped")
    driver.send("effects")
    effects_snapshot(driver.wait("packet_summary"))
    baseline = driver.wait("rate_sample")
    actions = planned_actions(tier, actors, seconds, prefix, mutation_offset, read_commands, mutation_mode, console_reads)
    bot_actions = [action for _, action in actions if action["transport"] == "bot"]
    console_actions = [action for _, action in actions if action["transport"] == "console"]
    for _, action in actions:
        action["acknowledged"] = True
    probe.send("scenario.start", {"phase":phase,"eventsPerTick":0,"variant":"plain-0"})
    probe.wait("scenario.started")
    samples, callbacks, results, process_samples = [], [], [], []
    journal.update(samples=samples,callbacks=callbacks,results=results,processSamples=process_samples,actions=[action for _,action in actions],offeredCommands=0,offeredBotCommands=0,offeredConsoleCommands=0,offeredSuggestions=0,controllerMaxLatenessSeconds=0)
    dropped = 0
    suggestion_reports = []
    suggestion_rate = declared_rates(tier)[2] if suggestion else 0
    suggestion_count = math.ceil(suggestion_rate * seconds)
    suggestion_index = 0
    journal["suggestions"] = suggestion_reports
    stopped = None
    rates = None
    expected = Counter(("CONSOLE" if action["transport"] == "console" else action["bot"], action["command"][1:]) for _, action in actions)
    actual = Counter()
    started = time.monotonic()
    log_offset = probe.process.log.stat().st_size
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
            elif event["type"] == "commands.suggested":
                suggestion_reports.append(data)
        if probe.process.process.poll() is not None:
            raise NativeFailure("native server exited during command phase")
        if driver.process.process.poll() is not None:
            raise RuntimeError("bot exited during command phase")
        if dropped or actual - expected:
            raise ValueError("native command evidence dropped or unexpected callback observed")
    scheduled = {action["actionId"]:at for at,action in actions}
    def offer(action):
        if action["transport"] == "console":
            probe.send("commands.execute",{"commands":[action["command"][1:]]})
            journal["offeredConsoleCommands"] += 1
        else:
            try:
                driver.send("action", action, timeout=0.1)
            except BotCapacityError as error:
                raise WorkloadIncomplete(str(error)) from error
            journal["offeredBotCommands"] += 1
        journal["offeredCommands"] += 1
        journal["controllerMaxLatenessSeconds"] = max(journal["controllerMaxLatenessSeconds"],time.monotonic()-started-scheduled[action["actionId"]])
    while time.monotonic() - started < seconds:
        elapsed = time.monotonic() - started
        index = offer_due(actions,index,elapsed,offer,poll,lambda action:action["transport"] == "console" or len(driver.stream.pending) < 256,started+seconds)
        while suggestion_index < suggestion_count and suggestion_index / suggestion_rate <= elapsed:
            poll()
            if time.monotonic() >= started + seconds:
                break
            probe.send("commands.suggest",{"input":suggestion["input"],"actor":actors[suggestion_index % len(actors)],"requestId":f"{prefix}-suggest-{suggestion_index}"})
            journal["controllerMaxLatenessSeconds"] = max(journal["controllerMaxLatenessSeconds"],time.monotonic()-started-suggestion_index/suggestion_rate)
            suggestion_index += 1
            journal["offeredSuggestions"] = suggestion_index
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
        raise WorkloadIncomplete("controller failed to offer declared command rate")
    if suggestion_index != suggestion_count:
        raise WorkloadIncomplete("controller failed to offer declared suggestion rate")
    completed_within = len(results)
    driver.send("effects")
    deadline = time.monotonic() + 15
    while rates is None or actual != expected or len(suggestion_reports) < suggestion_count:
        poll()
        if not drain_pending:
            probe.send("commands.execute", {"commands":[]})
            drain_pending = True
        if time.monotonic() >= deadline:
            raise WorkloadIncomplete("native command completion/drain deadline")
        time.sleep(0.01)
    completions = None
    while completions is None:
        poll()
        with probe.process.log.open("rb") as log:
            log.seek(log_offset)
            try:
                completions = action_log_completions(log.read().decode(errors="replace"),bot_actions)
            except ValueError as error:
                raise NativeFailure(str(error)) from error
        if time.monotonic() >= deadline:
            raise TimeoutError("asynchronous mutation/teleport/reload action-log completion deadline")
        if completions is None:
            time.sleep(0.01)
    probe.send("scenario.stop")
    while stopped is None:
        poll()
        if time.monotonic() >= deadline:
            raise TimeoutError("native command measurement stop deadline")
        time.sleep(0.01)
    # Counters are cumulative across barriers; this phase owns only the delta.
    rates = {**rates, **{key:rates[key]-baseline[key] for key in ("offered","completed","failed","elapsedSeconds")}}
    raw = {"schemaVersion":1,"phase":phase,"actions":bot_actions,"consoleActions":console_actions,"scheduledActions":[action for _,action in actions],"actionLogCompletions":completions,
           "controllerMaxLatenessSeconds":journal["controllerMaxLatenessSeconds"],
           "samples":samples,"callbacks":callbacks,"droppedCallbacks":dropped,"expectedPlayers":len(actors),"results":results,
           "seconds":stopped["phaseElapsedSeconds"],"scheduledSeconds":seconds,
           "controllerSeconds":time.monotonic()-started,"processSamples":process_samples,
           "completedWithinWindow":completed_within,"rates":rates,
           "latencies":acknowledged_latencies(bot_actions,results)}
    if suggestion:
        raw["suggestions"] = suggestion_reports
        raw["suggestionDemand"] = {"count":suggestion_count,"prefix":prefix+"-suggest","input":suggestion["input"],"providerId":suggestion["providerId"],"rate":suggestion_rate}
    summarize_window(raw, 0)
    return raw
