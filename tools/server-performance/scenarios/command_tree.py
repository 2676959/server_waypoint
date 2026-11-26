"""Live Paper command-tree correlation; clocks never cross process boundaries."""
import math
import statistics
from models import Decision, Status
from reporting import combine_decisions
from contracts import read_document
from pathlib import Path

FIXTURE = read_document(Path(__file__).resolve().parents[1] / "config/command-tree.json")

COMBINATIONS = tuple((role, trigger) for role in ("ordinary", "operator") for trigger in ("login", "refresh"))


def positive(value):
    return type(value) in (int, float) and math.isfinite(value) and value > 0


def judge_session(server, client):
    decisions = []
    for role, trigger in COMBINATIONS:
        native = [s for s in server if (s.get("role"), s.get("trigger")) == (role, trigger)]
        received = [s for s in client if (s.get("role"), s.get("trigger")) == (role, trigger)]
        label = f"{role}/{trigger}"
        expected = (role == "operator") == (trigger == "login")
        expected_branches = set(FIXTURE["baseBranches"][role]) | ({"tp"} if expected else set())
        # A proved wrong packet view is a product failure even if its counterpart
        # is missing or duplicated. Absence is an observation gap, never false.
        for value in native:
            if type(value.get("permission")) is not bool:
                decisions.append(Decision(Status.BLOCKED, (label + ": native permission observation missing",)))
            elif value["permission"] != expected:
                decisions.append(Decision(Status.FAIL, (label + ": native permission tree differs from expected grant/revocation",)))
        for value in received:
            if not isinstance(value.get("branches"), list):
                decisions.append(Decision(Status.BLOCKED, (label + ": decoded command paths missing",)))
            elif set(value["branches"]) != expected_branches:
                decisions.append(Decision(Status.FAIL, (label + ": unexpected Server Waypoint command paths",)))
        if len(native) != 1 or len(received) != 1:
            decisions.append(Decision(Status.BLOCKED, (label + ": missing/duplicate send or receipt",)))
            continue
        a, b = native[0], received[0]
        gaps = []
        for key in ("connectionId", "playerUuid", "role", "trigger", "sequence", "packetBodyBytes", "packetCrc32", "nodeCount"):
            if a.get(key) is None or a.get(key) != b.get(key):
                gaps.append("unmatched " + key)
        if a.get("sequence") != (1 if trigger == "login" else 2):
            gaps.append("unexpected tree sequence")
        if a.get("clock") != "server-monotonic" or b.get("clock") != "client-monotonic":
            gaps.append("local clock identity missing")
        if a.get("instrumented") is not True or b.get("installed") is not True or not isinstance(b.get("branches"), list):
            gaps.append("native instrumentation/client installation absent")
        for key in ("sendDurationNs", "dispatchNs", "asyncWorkNs", "syncWorkNs", "packetBodyBytes", "nodeCount"):
            if not positive(a.get(key)):
                gaps.append("invalid native " + key)
        if not positive(b.get("clientElapsedNs")) or b.get("timingScope") != ("connect-to-installed-tree" if trigger == "login" else "request-to-installed-tree"):
            gaps.append("invalid client-local timing")
        decisions.append(Decision(Status.BLOCKED if gaps else Status.PASS, tuple(label + ": " + g for g in gaps)))
    if len(server) != 4 or len(client) != 4:
        decisions.append(Decision(Status.BLOCKED, ("unexpected total send/receipt count",)))
    for role in ("ordinary", "operator"):
        samples = [s for s in server if s.get("role") == role]
        if len(samples) == 2 and (len({s.get("connectionId") for s in samples}) != 1 or len({s.get("playerUuid") for s in samples}) != 1):
            decisions.append(Decision(Status.BLOCKED, (role + ": refresh connection changed",)))
    return combine_decisions(decisions)


def summarize_sessions(sessions):
    decisions = [judge_session(s.get("server", []), s.get("client", [])) for s in sessions]
    if len(sessions) != 3 or len({s.get("pid") for s in sessions}) != 3 or any(not s.get("pid") or s.get("shutdown") is not True or not s.get("ticks") for s in sessions):
        decisions.append(Decision(Status.BLOCKED, ("three fresh processes with tick/shutdown evidence required",)))
    for session in sessions:
        if session.get("errors"):
            decisions.append(Decision(Status.BLOCKED, tuple(session["errors"])))
        health = session.get("health")
        if isinstance(health, dict):
            health = Decision(Status(health["status"]), tuple(health.get("reasons", [])))
        if health is not None:
            decisions.append(health)
    metrics = []
    for role, trigger in COMBINATIONS:
        values = {"role": role, "trigger": trigger}
        for side, keys in (("server", ("sendDurationNs", "dispatchNs", "asyncWorkNs", "syncWorkNs", "packetBodyBytes", "nodeCount")), ("client", ("clientElapsedNs",))):
            for key in keys:
                samples = [v[key] for s in sessions for v in s.get(side, []) if (v.get("role"), v.get("trigger")) == (role, trigger) and positive(v.get(key))]
                values[key] = {"samples": samples, "median": statistics.median(samples) if samples else None, "maximum": max(samples) if samples else None}
        metrics.append(values)
    return {"decision": combine_decisions(decisions), "metrics": metrics, "latencyBudget": "not established"}


def run_case(case, root):
    """Retain three new processes and source identities under a prepared minimal root."""
    import shutil
    import uuid
    from pathlib import Path
    from contracts import atomic_write, read_document
    from controller import owned_root
    from artifacts import sha256
    from models import CaseResult
    from preparation import REPOSITORY
    from scenarios.minimal import load_binding
    directory = None
    sessions = []
    metadata = {}
    try:
        if case.mode != "candidate" or case.runtime != "paper-1.21.11" or case.repeats != 3 or case.warmup_seconds or case.measurement_seconds:
            raise ValueError("command-tree requires three untimed candidate sessions on Paper 1.21.11")
        root = Path(root).absolute()
        binding = load_binding(root)
        runtime, artifact, release = binding
        if runtime.probe.get("commandTreeAgent") is not True:
            raise ValueError("command-tree native send agent required in runtime lock")
        settings = read_document(root / "prepared.json")
        prepared = Path(settings["worlds"][f"{case.fixture}-{case.tier}"])
        if root not in prepared.resolve().parents:
            raise ValueError("command-tree fixture must belong to prepared root")
        identifier = str(uuid.uuid4())
        directory = root / "command-tree" / identifier
        directory.mkdir(parents=True)
        metadata = dict(run_id=owned_root(root)["runId"], attempt_id=identifier,
            artifact={"name":artifact.name,"sha256":artifact.sha256,"revision":artifact.revision,"provenance":artifact.provenance},
            identities={"runtime":runtime.id,"probeSha256":runtime.probe["sha256"],"botSha256":runtime.bot["sha256"],
                        "fixtureSha256":sha256(prepared / "baseline.json")},
            paths={"commandTree":str(directory / "delivery.json")})
        atomic_write(directory / "inputs.json", {"schemaVersion":1,"case":case, **metadata,"sourceHashes":settings["inputs"]})
        for name in settings["inputs"]:
            target = directory / "source" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPOSITORY / name, target)
        from bots.command_tree_smoke import run_session
        for index in range(3):
            print(f"Command-tree fresh-process session {index + 1}/3", flush=True)
            sessions.append(run_session(directory / f"session-{index+1}", binding, prepared / "frozen"))
            atomic_write(directory / "delivery.json", {"schemaVersion":1,"sessions":sessions, **summarize_sessions(sessions)})
        summary = summarize_sessions(sessions)
        result = CaseResult(case, summary["decision"], **metadata,
            observations={"sessions":sessions,"metrics":summary["metrics"],"latencyBudget":"not established"},
            cleanup="verified saved shutdown" if all(s["shutdown"] for s in sessions) else "unverified")
    except Exception as error:
        decisions = [Decision(Status.BLOCKED, (f"command-tree execution: {type(error).__name__}: {error}",))]
        if sessions:
            decisions.append(summarize_sessions(sessions)["decision"])
        result = CaseResult(case, combine_decisions(decisions), **metadata, observations={"sessions":sessions})
    if directory is not None:
        atomic_write(directory / "result.json", result)
    return result
