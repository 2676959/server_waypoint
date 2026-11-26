"""Bounded candidate-only idle and acknowledged A/B retarget measurements."""
from collections import Counter
from dataclasses import replace
from pathlib import Path
import shutil
import time
import uuid

from artifacts import sha256
from bot_driver import BotDriver, validate_ready
from budgets import evaluate
from contracts import atomic_write, read_document
from controller import create_root, owned_root
from metrics import summarize
from models import CaseResult, Decision, Status
from preparation import REPOSITORY
from processes import stop_owned
from reporting import combine_decisions
from scenarios.minimal import load_binding
from scenarios.navigation import MINIMAL_METHODS, cleanup_counts, ordinary_conservation, retargeted
from scenarios.navigation_workload import native_command_latency, repetition_record
from server_health import native_log_health
from worlds import boot, clone_baseline, install_server


TARGETS = {
    "a": {"dimension":"minecraft:overworld", "list":"nav_fixture", "waypoint":"a", "displayName":"Target A",
        "position":[320,-61,320], "rgb":0x112233,
        "expectedMap":{"centerX":512,"centerZ":512,"scale":2,"cursorX":-96,"cursorZ":-96}},
    "b": {"dimension":"minecraft:overworld", "list":"nav_fixture", "waypoint":"b", "displayName":"Target B",
        "position":[-320,-61,-320], "rgb":0x445566,
        "expectedMap":{"centerX":-512,"centerZ":-512,"scale":2,"cursorX":96,"cursorZ":96}},
}


def schedule(case, seconds, owners, phase):
    if len(owners) != case.clients or len(set(owners)) != case.clients:
        raise ValueError("exact distinct native owner set required")
    if case.variant == "minimal-idle":
        return []
    if case.variant != "minimal-retarget" or case.offered_rate != case.clients or seconds != int(seconds):
        raise ValueError("retarget requires one operation per owner per second and whole windows")
    return [{"actionId":f"{phase.lower()}-{second}-{index}", "bot":owner, "operation":"command",
        "command":"/wp navigate minecraft:overworld nav_fixture " + ("b" if second % 2 == 0 else "a"),
        "acknowledged":True}
        for second in range(int(seconds)) for index, owner in enumerate(owners)]


def judge_delivery(actions, receipts, issued, pending):
    gaps = []
    ids = [a["actionId"] for a in actions]
    counts = Counter(r.get("actionId") for r in receipts)
    if issued != len(actions) or pending or set(counts) != set(ids) or any(n != 1 for n in counts.values()):
        gaps.append("missing/duplicate/unissued native command receipts or pending work")
    by_id = {r.get("actionId"):r for r in receipts}
    for action in actions:
        receipt = by_id.get(action["actionId"], {})
        ack = receipt.get("nativeAck", {})
        if native_command_latency(action, receipt) is None or receipt.get("bot") != action["bot"] or ack.get("returned") != 1:
            gaps.append(action["actionId"] + ": successful matched native completion missing")
    return Decision(Status.BLOCKED if gaps else Status.PASS, tuple(gaps))


def judge_state(snapshot, owners, ordinary, target):
    decisions = []
    players = snapshot.get("players")
    if not isinstance(players, list) or len(players) != len(owners) or {p.get("name"):p.get("uuid") for p in players} != owners:
        return Decision(Status.BLOCKED, ("native owner identities/count differ",))
    for player in players:
        name = player["name"]
        methods = set(player.get("methods", []))
        if methods != MINIMAL_METHODS or player.get("dead") is not False:
            decisions.append(Decision(Status.FAIL, (name + ": all six live navigation methods required",)))
        items = player.get("items", [])
        if len(items) != 2 or any(sum(i.get("amount", 0) for i in items if i.get("method") == m) != 1 for m in ("compass", "map")):
            decisions.append(Decision(Status.FAIL, (name + ": method items missing or duplicated",)))
        state = {**player, "methods":list(methods - {"actionbar"})}
        # The native snapshot has no client actionbar history. Step 2 checks its
        # client effect; this adapter judges native session/items/displays.
        for item in items:
            if item.get("method") == "compass":
                state.update(compassTarget=item.get("compassTarget"), compassCaption=item.get("caption", ""))
            elif item.get("method") == "map":
                state["mapState"] = item.get("mapState", {})
        decisions.extend((retargeted(TARGETS[target], state), ordinary_conservation(ordinary.get(name), player.get("ordinary"))))
    return combine_decisions(decisions)


def as_decision(value):
    if isinstance(value, Decision):
        return value
    return Decision(Status(value["status"]), tuple(value.get("reasons", [])))


def judge_window(case, window, seconds, check_budget=True):
    decisions = [as_decision(window.get("delivery", {"status":"BLOCKED", "reasons":["delivery absent"]}))]
    gaps = []
    ticks = window.get("tickNs", [])
    counts = window.get("sessionCounts", [])
    if not ticks or len(counts) != len(ticks) or any(c != case.clients for c in counts):
        gaps.append("native active-session count absent or below required owners")
    if len(ticks) < max(1, int(seconds * 10)):
        gaps.append("insufficient native tick coverage")
    if window.get("clock") != "server-monotonic" or window.get("scope") != "server-thread":
        gaps.append("native tick clock/scope missing")
    if window.get("seconds", 0) < seconds:
        gaps.append("shortened timing window")
    if window.get("offeredActions") != int(case.offered_rate * seconds) or window.get("completedActions") != window.get("offeredActions"):
        gaps.append("offered/completed native work differs from required load")
    if window.get("maxOfferLagSeconds", float("inf")) >= 1:
        gaps.append("offered work slipped by at least one owner interval")
    if gaps:
        decisions.append(Decision(Status.BLOCKED, tuple(gaps)))
    if ticks and check_budget:
        metrics = summarize(ticks, "ns")
        if metrics["p95"] >= 40_000_000 or metrics["p99"] >= 50_000_000:
            decisions.append(Decision(Status.FAIL, ("native tick budget exceeded",)))
    return combine_decisions(decisions)


def collect_window(case, probe, driver, phase, directory, owners):
    seconds = case.warmup_seconds if phase == "WARMUP" else case.measurement_seconds
    actions = schedule(case, seconds, owners, phase)
    window = {"seconds":0, "tickNs":[], "sessionCounts":[], "receipts":[], "issued":0,
        "offeredActions":len(actions), "completedActions":0, "maxOfferLagSeconds":0,
        "clock":"server-monotonic", "scope":"server-thread", "phase":phase, "offerTimesSeconds":[], "errors":[]}
    destination = Path(directory) / (phase.lower() + ".json")
    tick_indices = []
    try:
        probe.send("scenario.start", {"phase":phase})
        probe.wait("scenario.started")
        started = time.monotonic()
        stopped = None

        def poll():
            nonlocal stopped
            for event in probe.poll():
                data = event.get("data", {})
                if event["type"] == "sample" and data.get("phase") == phase:
                    if data.get("clock") != "server-monotonic" or data.get("scope") != "server-thread" or type(data.get("durationNs")) is not int or data["durationNs"] < 0:
                        raise ValueError("invalid native tick sample")
                    window["tickNs"].append(data["durationNs"])
                    window["sessionCounts"].append(data.get("navigationSessions"))
                    tick_indices.append(data.get("tickCount"))
                elif event["type"] == "scenario.stopped":
                    stopped = data
                elif event["type"] == "error":
                    raise ValueError(data)
            if driver is not None:
                for event in driver.poll():
                    if event["type"] == "action_result" or (event["type"] == "error" and event.get("data", {}).get("actionId")):
                        window["receipts"].append(event["data"])

        while time.monotonic() - started < seconds:
            poll()
            elapsed = time.monotonic() - started
            cursor = window["issued"]
            if cursor < len(actions) and elapsed >= cursor / case.offered_rate:
                action = actions[cursor]
                # The native completion marker supports one outstanding command
                # per connection. Never overwrite it or hide delayed delivery.
                if not any(a["bot"] == action["bot"] and a["actionId"] in driver.stream.pending for a in actions[:cursor]):
                    driver.send("action", action)
                    window["issued"] += 1
                    window["offerTimesSeconds"].append(elapsed)
                    window["maxOfferLagSeconds"] = max(window["maxOfferLagSeconds"], elapsed - cursor / case.offered_rate)
            time.sleep(0.005)
        probe.send("scenario.stop")
        deadline = time.monotonic() + 30
        while stopped is None or (driver is not None and driver.stream.pending):
            poll()
            if time.monotonic() >= deadline:
                raise TimeoutError("phase stop/native completion deadline")
            time.sleep(0.01)
        window["seconds"] = stopped["phaseElapsedSeconds"]
    except BaseException as error:
        window["errors"].append(f"{type(error).__name__}: {error}")
        if not isinstance(error, Exception):
            raise
    finally:
        window["delivery"] = judge_delivery(actions, window["receipts"], window["issued"], len(driver.stream.pending) if driver else 0)
        window["completedActions"] = sum(native_command_latency(a, r) is not None and r.get("bot") == a["bot"] and r.get("nativeAck", {}).get("returned") == 1
            for a in actions for r in window["receipts"] if r.get("actionId") == a["actionId"])
        window["actionLatencyNs"] = [r["clientElapsedNs"] for r in window["receipts"] if "clientElapsedNs" in r]
        if not tick_indices or any(type(t) is not int for t in tick_indices) or any(b != a + 1 for a, b in zip(tick_indices, tick_indices[1:])):
            window["delivery"] = combine_decisions((window["delivery"], Decision(Status.BLOCKED, ("native tick stream gap/duplicate",))))
        window["decision"] = combine_decisions((judge_window(case, window, seconds, phase == "MEASURING"),
            Decision(Status.BLOCKED if window["errors"] else Status.PASS, tuple(window["errors"]))))
        atomic_write(destination, {"schemaVersion":1, "actions":actions, "tickIndices":tick_indices, **window})
    return window


def snapshot(probe):
    probe.send("navigation.snapshot")
    return probe.wait("navigation.snapshot")["data"]


def execute(probe, commands):
    probe.send("commands.execute", {"commands":list(commands)})
    return probe.wait("commands.executed")["data"]


def command_batch(driver, owners, command, prefix):
    actions = [{"actionId":f"{prefix}-{i}", "bot":name, "operation":"command", "command":command, "acknowledged":True} for i, name in enumerate(owners)]
    for action in actions:
        driver.send("action", action)
    receipts = [driver.wait("action_result", 30) for _ in actions]
    return {"actions":actions, "receipts":receipts, "decision":judge_delivery(actions, receipts, len(actions), len(driver.stream.pending))}


def run_repetition(case, directory, binding, baseline):
    runtime, artifact, release = binding
    directory = create_root(directory)
    server = create_root(directory / "server")
    evidence = {"schemaVersion":1, "decisions":[], "windows":{}, "shutdown":False, "errors":[]}
    outcomes = evidence["decisions"]
    process = driver = probe = None
    try:
        port = install_server(runtime, server, runtime.probe["path"], release)
        clone_baseline(baseline, server)
        if case.clients:
            from scenarios.navigation_runner import seed_navigation_fixture
            seed_navigation_fixture(server)
        process, probe = boot(runtime, server, False, artifact.sha256)
        evidence["pid"] = process.pid
        if case.clients:
            driver = BotDriver(runtime.bot["path"], create_root(directory / "bots"), probe.stream.run_id, probe.stream.attempt_id)
            driver.send("start", {"address":f"127.0.0.1:{port}", "count":case.clients, "mode":"vanilla"})
            ready = driver.wait("ready", 150)
            native = snapshot(probe)
            readiness = validate_ready(ready, native["players"], case.clients, "vanilla")
            outcomes.append(readiness)
            if readiness.status != Status.PASS:
                raise ValueError("native owners not ready")
            owners = {p["name"]:p["uuid"] for p in native["players"]}
            names = tuple(sorted(owners))
            evidence["owners"] = owners
            execute(probe, ["difficulty peaceful", *(c for n in names for c in
                (f"op {n}", f"gamemode creative {n}", f"tp {n} 300 -61 300", f"give {n} minecraft:stone 64", f"give {n} minecraft:diamond 3"))])
            probe.send("commands.tree")
            probe.wait("commands.tree")
            native = snapshot(probe)
            ordinary = {p["name"]:p["ordinary"] for p in native["players"]}
            if any(sum(v for k,v in row.items() if k.startswith("minecraft:stone:")) != 64 or sum(v for k,v in row.items() if k.startswith("minecraft:diamond:")) != 3 or sum(row.values()) != 67 for row in ordinary.values()):
                raise ValueError("independently seeded ordinary items absent")
            evidence["ordinary"] = ordinary
            evidence["preflight"] = []
            for target, suffix in (("a", " all"), ("b", ""), ("a", "")):
                batch = command_batch(driver, names, "/wp navigate minecraft:overworld nav_fixture " + target + suffix, "preflight-" + str(len(evidence["preflight"])))
                evidence["preflight"].append(batch)
                outcomes.append(batch["decision"])
                time.sleep(0.2)
                native = snapshot(probe)
                batch["native"] = native
                batch["stateDecision"] = judge_state(native, owners, ordinary, target)
                outcomes.append(batch["stateDecision"])
            if combine_decisions(outcomes).status != Status.PASS:
                raise ValueError("retarget adapter preflight not PASS; timing not started")
        else:
            names = ()
            native = snapshot(probe)
            evidence["idleBefore"] = native
            if native["players"] or native["resources"]["sessions"]:
                raise ValueError("idle control must have zero owners/sessions")
        for phase in ("WARMUP", "MEASURING"):
            print(f"  {phase}: {case.warmup_seconds if phase == 'WARMUP' else case.measurement_seconds}s, {case.clients} owners", flush=True)
            window = collect_window(case, probe, driver, phase, directory, names)
            evidence["windows"][phase] = window
            outcomes.append(window["decision"])
            if window.get("errors"):
                raise ValueError("timing phase incomplete")
            if case.clients:
                native = snapshot(probe)
                target = "b" if int(case.warmup_seconds if phase == "WARMUP" else case.measurement_seconds) % 2 else "a"
                state = judge_state(native, owners, ordinary, target)
                evidence[phase.lower() + "State"] = {"native":native, "expectedTarget":target, "decision":state}
                outcomes.append(state)
        evidence["beforeDisconnect"] = snapshot(probe)
        if driver is not None:
            try:
                driver.close()
            finally:
                driver = None
        deadline = time.monotonic() + 20
        disconnected_tick = None
        while True:
            native = snapshot(probe)
            if native.get("players") == []:
                if disconnected_tick is None:
                    disconnected_tick = native["tick"]
                if native["tick"] - disconnected_tick >= 10:
                    break
            else:
                disconnected_tick = None
            if time.monotonic() >= deadline:
                raise TimeoutError("ten native post-disconnect ticks missing")
            time.sleep(0.05)
        expected = {key:0 for key in ("sessions", "tasks", "entities", "markerMotionTracks", "bossbars", "items", "mapTargets", "tickCounters", "targetIndexEntries")}
        expected["mapCacheEntries"] = case.clients
        cleanup = cleanup_counts(expected, native["resources"])
        evidence["cleanup"] = {"native":native, "expected":expected, "decision":cleanup, "disconnectedTick":disconnected_tick}
        outcomes.append(cleanup)
        evidence["completed"] = True
    except BaseException as error:
        evidence["errors"].append(f"{type(error).__name__}: {error}")
        outcomes.append(Decision(Status.BLOCKED, (evidence["errors"][-1],)))
        if not isinstance(error, Exception):
            raise
    finally:
        try:
            if driver is not None:
                try:
                    driver.abort()
                except Exception as error:
                    outcomes.append(Decision(Status.BLOCKED, ("bot cleanup: " + str(error),)))
        finally:
            try:
                if process is not None:
                    stop_owned(process, 30)
                    evidence["shutdown"] = "All dimensions are saved" in process.log.read_text(errors="replace")
                    outcomes.append(native_log_health(process.log))
            except Exception as error:
                evidence["errors"].append("shutdown: " + str(error))
                outcomes.append(Decision(Status.BLOCKED, (evidence["errors"][-1],)))
            finally:
                # A collection or shutdown exception must not detach its already
                # observed failure from the repetition verdict.
                for phase in ("WARMUP", "MEASURING"):
                    path = directory / (phase.lower() + ".json")
                    if path.is_file() and phase not in evidence["windows"]:
                        window = read_document(path)
                        evidence["windows"][phase] = window
                        outcomes.append(as_decision(window["decision"]))
                if not evidence.get("completed"):
                    outcomes.append(Decision(Status.BLOCKED, ("repetition incomplete",)))
                if not evidence["shutdown"]:
                    outcomes.append(Decision(Status.BLOCKED, ("saved shutdown proof missing",)))
                evidence["decision"] = combine_decisions(outcomes)
                atomic_write(directory / "repetition.json", evidence)
    return evidence


def run_case(case, root):
    directory = None
    repetitions = []
    metadata = {}
    try:
        if case.mode != "candidate" or case.runtime != "paper-1.21.11" or case.repeats != 3 or case.warmup_seconds != 30 or case.measurement_seconds != 60:
            raise ValueError("bounded timing requires three 30s/60s candidate processes on Paper 1.21.11")
        expected = (0, 0) if case.variant == "minimal-idle" else (10, 10) if case.variant == "minimal-retarget" else None
        if expected != (case.clients, case.offered_rate):
            raise ValueError("bounded timing owner/rate selection differs")
        root = Path(root).resolve()
        binding = load_binding(root)
        runtime, artifact, release = binding
        settings = read_document(root / "prepared.json")
        prepared = Path(settings["worlds"][f"{case.fixture}-{case.tier}"])
        if root not in prepared.resolve().parents or settings.get("profile"):
            raise ValueError("owned unprofiled fixture required")
        identifier = str(uuid.uuid4())
        directory = root / "performance" / identifier
        directory.mkdir(parents=True)
        metadata = dict(run_id=owned_root(root)["runId"], attempt_id=identifier,
            artifact={"name":artifact.name, "sha256":artifact.sha256, "revision":artifact.revision, "provenance":artifact.provenance},
            identities={"runtime":runtime.id, "serverSha256":runtime.server_sha256, "javaSha256":runtime.java_sha256,
                "probeSha256":runtime.probe["sha256"], "botSha256":runtime.bot["sha256"], "fixtureSha256":sha256(prepared / "baseline.json")},
            paths={"performance":str(directory / "performance.json")})
        atomic_write(directory / "inputs.json", {"schemaVersion":1,"case":case, **metadata,"sourceHashes":settings["inputs"]})
        for name in settings["inputs"]:
            target = directory / "source" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPOSITORY / name, target)
        for index in range(3):
            print(f"{case.variant}: fresh process {index + 1}/3", flush=True)
            repetitions.append(run_repetition(case, directory / f"repeat-{index + 1}", binding, prepared / "frozen"))
            atomic_write(directory / "performance.json", {"schemaVersion":1,"repetitions":repetitions})
        decisions = [as_decision(r["decision"]) for r in repetitions]
        pids = {r.get("pid") for r in repetitions}
        if len(pids) != 3 or None in pids:
            decisions.append(Decision(Status.BLOCKED, ("three distinct native processes missing",)))
        records = []
        for repetition in repetitions:
            windows = repetition["windows"]
            if "WARMUP" in windows and "MEASURING" in windows:
                w, m = windows["WARMUP"], windows["MEASURING"]
                records.append(repetition_record(case, w["seconds"], m["seconds"], m["tickNs"], m["offeredActions"], m["completedActions"], m.get("actionLatencyNs", []), "client-monotonic"))
        result = CaseResult(case, combine_decisions(decisions), **metadata,
            observations={"repetitions":records,"nativeRepetitions":repetitions}, cleanup="verified saved shutdown" if all(r["shutdown"] for r in repetitions) else "unverified")
        result = replace(result, decision=combine_decisions((result.decision, evaluate(result, root / "budgets.json"))))
    except BaseException as error:
        if directory is not None:
            repetitions = [read_document(p) for p in sorted(directory.glob("repeat-*/repetition.json"))]
        decisions = [as_decision(r["decision"]) for r in repetitions]
        decisions.append(Decision(Status.BLOCKED, (f"minimal performance: {type(error).__name__}: {error}",)))
        result = CaseResult(case, combine_decisions(decisions), **metadata, observations={"nativeRepetitions":repetitions})
        if not isinstance(error, Exception):
            if directory is not None:
                atomic_write(directory / "performance.json", {"schemaVersion":1,"repetitions":repetitions})
                atomic_write(directory / "result.json", result)
                atomic_write(root / "results" / ("interrupted-" + directory.name + ".json"), result)
            raise
    if directory is not None:
        atomic_write(directory / "result.json", result)
    return result
