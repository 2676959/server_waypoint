"""Candidate-only minimal functional execution; historical adapters stay separate."""
from pathlib import Path

from artifacts import sha256, verify_artifact
from contracts import load_release, load_runtimes, read_document
from controller import owned_root
from preparation import input_hashes, verify_evidence


def load_binding(root):
    root = Path(root).resolve()
    if owned_root(root)["state"] != "PREPARED":
        raise ValueError("minimal-critical root not prepared")
    settings = read_document(root / "prepared.json")
    verify_evidence(root, settings)
    if settings.get("inputs") != input_hashes():
        raise ValueError("minimal-critical executable inputs changed; use a new prepared root")
    if read_document(root / "selection.json").get("suite") != "minimal-critical":
        raise ValueError("minimal-critical prepared selection required")
    runtime = next(r for r in load_runtimes(root / "runtime-lock.json") if r.id == "paper-1.21.11")
    artifact = next(a for a in load_release(root / "release.json") if a.name == runtime.artifact)
    if artifact.provenance != "candidate":
        raise ValueError("minimal-critical requires candidate provenance")
    release = root / "artifacts" / artifact.name
    verify_artifact(artifact, release)
    for label, path, expected in (
        ("server", runtime.server, runtime.server_sha256),
        ("java", runtime.java, runtime.java_sha256),
        ("probe", runtime.probe.get("path"), runtime.probe.get("sha256")),
        ("bot", runtime.bot.get("path"), runtime.bot.get("sha256")),
    ):
        if not path or not expected or not Path(path).is_file() or sha256(path) != expected:
            raise ValueError(f"minimal-critical locked {label} bytes changed or missing")
    return runtime, artifact, release


def judge_evidence(evidence, required):
    """A product failure survives gaps; every named check is needed for a PASS."""
    from contracts import json_value
    from models import Decision, Status
    from reporting import combine_decisions
    evidence = json_value(evidence)
    decisions = []
    observed = set()
    for check in evidence.get("checks", []):
        name = check.get("name")
        if name in observed:
            decisions.append(Decision(Status.BLOCKED, (f"duplicate functional check: {name}",)))
        observed.add(name)
        value = check.get("decision", {})
        if value.get("status") not in ("PASS", "FAIL", "BLOCKED"):
            decisions.append(Decision(Status.BLOCKED, (f"missing/invalid functional decision: {name}",)))
        else:
            decisions.append(Decision(Status(value["status"]), tuple(f"{name}: {r}" for r in value.get("reasons", ()))))
    for name in required:
        if name not in observed:
            decisions.append(Decision(Status.BLOCKED, (f"missing functional check: {name}",)))
    if evidence.get("completed") is not True:
        decisions.append(Decision(Status.BLOCKED, ("functional execution incomplete",)))
    if not required:
        decisions.append(Decision(Status.BLOCKED, ("functional requirements missing",)))
    return combine_decisions(decisions)


REQUIRED = {
    "minimal-storage-permissions": ("create", "edit", "remove", "restore", "denied", "save", "reopen"),
    "minimal-lifecycle": ("methods", "retarget", "delete", "stop", "cleanup"),
    "minimal-terrain": ("regions", "isolation", "save", "reopen"),
    "minimal-direct-protection": ("inventory", "chest", "drop", "pickup"),
}


def run_case(case, root):
    """Run one untimed functional attempt, retaining its exact inputs and checks."""
    import shutil
    import uuid
    from contracts import atomic_write, json_value
    from models import CaseResult, Decision, Status
    from preparation import REPOSITORY
    from reporting import combine_decisions
    from server_health import native_log_health
    if case.variant not in REQUIRED:
        return CaseResult(case, Decision(Status.BLOCKED, (f"minimal-critical native adapter pending: {case.variant}",)))
    root = Path(root).absolute()
    directory = None
    metadata = {}
    try:
        if case.mode != "candidate" or case.runtime != "paper-1.21.11" or case.repeats != 1 or case.warmup_seconds or case.measurement_seconds:
            raise ValueError("minimal functional case must be one untimed candidate execution on Paper 1.21.11")
        binding = load_binding(root)
        runtime, artifact, release = binding
        settings = read_document(root / "prepared.json")
        prepared = Path(settings["worlds"][f"{case.fixture}-{case.tier}"])
        if root not in prepared.resolve().parents:
            raise ValueError("minimal fixture must belong to this prepared root")
        baseline = prepared / "frozen"
        metadata = dict(run_id=owned_root(root)["runId"],
            artifact={"name": artifact.name, "sha256": artifact.sha256, "revision": artifact.revision, "provenance": artifact.provenance},
            identities={"runtime": runtime.id, "serverSha256": runtime.server_sha256, "javaSha256": runtime.java_sha256,
                "probeSha256": runtime.probe["sha256"], "botSha256": runtime.bot["sha256"],
                "fixtureSha256": sha256(prepared / "baseline.json")})
        identifier = str(uuid.uuid4())
        directory = root / "functional" / identifier
        directory.mkdir(parents=True)
        metadata["attempt_id"] = identifier
        metadata["paths"] = {"functional": str(directory / "functional.json"), "native": str(directory / "native")}
        atomic_write(directory / "inputs.json", {"schemaVersion": 1, "case": case, **metadata, "sourceHashes": settings["inputs"]})
        for name in settings["inputs"]:
            target = directory / "source" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPOSITORY / name, target)
        from bots.minimal_functional import run
        evidence = run(case.variant, directory / "native", binding, baseline)
        atomic_write(directory / "functional.json", {"schemaVersion": 1, **json_value(evidence)})
        decision = judge_evidence(evidence, REQUIRED[case.variant])
        # Startup logs are distinct per process, so reopen failures cannot be
        # hidden by the last latest.log. Never invent shutdown proof.
        logs = sorted((directory / "native").rglob("server-*.log"))
        shutdown = bool(logs) and all("All dimensions are saved" in log.read_text(errors="replace") for log in logs)
        outcomes = [decision, *(native_log_health(log) for log in logs)]
        if not shutdown:
            outcomes.append(Decision(Status.BLOCKED, ("native save/shutdown proof missing",)))
        result = CaseResult(case, combine_decisions(outcomes), **metadata,
            assertions={check["name"]: json_value(check.get("decision", {})).get("status") == "PASS" for check in evidence.get("checks", [])},
            observations={"checks": evidence.get("checks", []), "completed": evidence.get("completed") is True,
                "serverLogs": [str(log) for log in logs], "timed": False},
            cleanup="verified saved shutdown" if shutdown else "unverified")
        atomic_write(directory / "result.json", result)
        return result
    except Exception as error:
        decision = Decision(Status.BLOCKED, (f"minimal-critical execution: {type(error).__name__}: {error}",))
        if directory is not None and (directory / "functional.json").exists():
            decision = combine_decisions((judge_evidence(read_document(directory / "functional.json"), REQUIRED[case.variant]), decision))
        result = CaseResult(case, decision, **metadata)
        if directory is not None:
            atomic_write(directory / "result.json", result)
        return result
