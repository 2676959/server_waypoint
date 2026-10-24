"""Paper M0: paired immutable worlds and bounded production event dispatch."""
from dataclasses import replace
from pathlib import Path
import shutil
import uuid
import math

from artifacts import sha256, verify_artifact
from budgets import evaluate
from contracts import atomic_write, load_release, load_runtimes, read_document, load_result
from controller import Attempt, collect_window, create_root, owned_root, NativeFailure
from models import CaseResult, Decision, Status
from processes import stop_owned
from profiles import start_jfr, stop_jfr
from reporting import combine_decisions, exit_code
from worlds import boot, install_server, clone_baseline, validate_fixture


def validate_zero_players(samples):
    if not samples or any(sample.get("onlinePlayers") != 0 for sample in samples):
        return Decision(Status.FAIL, ("zero-player workload observed players or lacks online evidence",))
    return Decision(Status.PASS)


def synthetic_count(events_per_tick, ticks):
    if events_per_tick < 0 or events_per_tick > 1000 or ticks < 0:
        raise ValueError("invalid bounded synthetic demand")
    return events_per_tick * ticks


def validate_activity(samples, control, expected_items):
    reasons = []
    if not samples:
        return Decision(Status.INCONCLUSIVE, ("no physical activity samples",))
    if any(sample.get("topLevelItemCount") != expected_items for sample in samples):
        return Decision(Status.FAIL, ("item conservation failed during activity",))
    if any(sample.get("paused") is not False or sample.get("pauseWhenEmptySeconds") != 0 or sample.get("forceLoadedHoppers") != sample.get("expectedHoppers") for sample in samples):
        reasons.append("pause/chunk activity proof missing")
    stagnant = 0
    previous = samples[0]["physicalTransfersObserved"]
    for sample in samples[1:]:
        current = sample["physicalTransfersObserved"]
        stagnant = stagnant + 1 if current <= previous else 0
        previous = current
        if stagnant >= 40:
            reasons.append("physical movement stalled for 40 native ticks")
            break
    return Decision((Status.INCONCLUSIVE if control else Status.FAIL) if reasons else Status.PASS, tuple(reasons))


def validate_physical_pair(control, candidate, expected_items):
    failures, invalid = [], []
    for label, sample in (("control", control), ("candidate", candidate)):
        if any(not isinstance(sample.get(key), (int, float)) or not math.isfinite(sample[key]) or sample[key] < 0 for key in ("physicalTransfersObserved", "elapsedSeconds", "tickCount")):
            invalid.append(f"{label}: invalid physical measurement")
            continue
        if sample.get("topLevelItemCount") != expected_items:
            failures.append(f"{label}: item conservation failed")
        if sample.get("onlinePlayers") != 0:
            failures.append(f"{label}: zero-player assertion failed")
        if sample.get("paused") or sample.get("forceLoadedHoppers") != sample.get("expectedHoppers") or not sample.get("expectedHoppers"):
            invalid.append(f"{label}: active chunk/ticking proof missing")
        if sample.get("elapsedSeconds", 0) <= 0 or sample.get("tickCount", 0) <= 0:
            invalid.append(f"{label}: no native measurement window")
    if control.get("physicalTransfersObserved", 0) <= 0:
        invalid.append("control fixture has no actual inventory movement")
    elif not invalid:
        control_rate = control["physicalTransfersObserved"] / control["elapsedSeconds"]
        candidate_rate = candidate["physicalTransfersObserved"] / candidate["elapsedSeconds"]
        if candidate_rate < control_rate * 0.75:
            failures.append("candidate physical throughput below 75% of active matched control")
    return Decision(Status.FAIL if failures else Status.INCONCLUSIVE if invalid else Status.PASS, tuple(failures + invalid))


def run_independent(cases, runner):
    results = []
    for case in cases:
        try:
            results.append(runner(case))
        except Exception as error:
            if hasattr(case, "feature"):
                results.append(CaseResult(case, Decision(Status.BLOCKED, (str(error),))))
            else:
                results.append(Decision(Status.BLOCKED, (str(error),)))
    return tuple(results)


def physical_summary(window):
    final, initial = window["final"], window["initial"]
    return {**final, "physicalTransfersObserved": final["physicalTransfersObserved"] - initial["physicalTransfersObserved"], "elapsedSeconds": window["seconds"], "tickCount": len(window["samples"])}


def retain_partial(path, case, error):
    blocked = Decision(Status.FAIL if isinstance(error, NativeFailure) else Status.BLOCKED, (str(error) or type(error).__name__,))
    if Path(path).exists():
        result = load_result(path)
        return replace(result, decision=combine_decisions((result.decision, blocked)))
    return CaseResult(case, blocked)


def run_case(case, root):
    partial = Path(root) / "partials" / (str(uuid.uuid4()) + ".json")
    try:
        return _run_case(case, root, partial)
    except KeyboardInterrupt:
        if partial.exists():
            atomic_write(Path(root) / "results" / ("interrupted-" + partial.name), retain_partial(partial, case, KeyboardInterrupt("interrupted after measured work")))
        raise
    except Exception as error:
        return retain_partial(partial, case, error)


def _run_case(case, root, partial):
    root = Path(root)
    if case.applicability_reason:
        return CaseResult(case, Decision(Status.NOT_APPLICABLE, (case.applicability_reason,)))
    if case.feature not in ("idle", "world-container", "inventory-regression") or case.clients:
        return CaseResult(case, Decision(Status.BLOCKED, ("requires later feature/player adapter",)))
    if case.mode != "audit":
        return CaseResult(case, Decision(Status.BLOCKED, ("candidate execution requires an explicit candidate manifest (Task 18)",)))
    if case.feature == "idle" and case.variant != "empty":
        return CaseResult(case, Decision(Status.BLOCKED, ("dataset/settings adapter remains Task 10",)))
    runtime = next(r for r in load_runtimes(root / "runtime-lock.json") if r.id == case.runtime)
    if runtime.id != "paper-1.21.11":
        return CaseResult(case, Decision(Status.BLOCKED, ("native adapter unverified for this runtime",)))
    settings = read_document(root / "prepared.json")
    from preparation import verify_evidence
    verify_evidence(root, settings)
    fixture_key = f"{case.fixture}-{case.tier}"
    prepared = Path(settings["worlds"][fixture_key])
    artifact = next(a for a in load_release(root / "release.json") if a.name == runtime.artifact)
    artifact_path = root / "artifacts" / artifact.name
    verify_artifact(artifact, artifact_path)
    events = int(case.variant.rsplit("-", 1)[-1]) if case.feature == "inventory-regression" else 0
    workload = {"variant": case.variant, "eventsPerTick": events}
    repetitions, outcomes, evidence = [], [], []
    def snapshot_result():
        result = CaseResult(case, Decision(Status.INCONCLUSIVE), owned_root(root)["runId"], artifact={"name": artifact.name, "sha256": artifact.sha256, "revision": artifact.revision, "provenance": artifact.provenance}, identities={"runtime": runtime.id, "serverSha256": runtime.server_sha256, "javaSha256": runtime.java_sha256, "probeSha256": runtime.probe["sha256"], "fixtureSha256": read_document(prepared / "baseline.json")["recipeSha256"]}, observations={"repetitions": list(repetitions), "profiledDiagnostic": settings.get("profile", False)}, paths={"attempts": list(evidence)}, cleanup="see retained attempt records")
        return replace(result, decision=combine_decisions((*outcomes, evaluate(result, root / "budgets.json"))))

    for repetition in range(case.repeats):
        pair = {}
        for control in (True, False):
            label = f"repeat-{repetition + 1}-{'control' if control else 'release'}"
            with Attempt(root, case.id + ":" + label) as attempt:
                server = create_root(attempt.directory / "server")
                install_server(runtime, server, Path(runtime.probe["path"]), None if control else artifact_path)
                clone_baseline(prepared / "frozen", server)
                shutil.copytree(prepared / "fixture", server / "fixture")
                attempt.transition("STARTING")
                process, driver = boot(runtime, server, control, artifact.sha256, attempt.record.attempt_id)
                profile = settings.get("profile", False)
                profile_path = attempt.directory / "profiles/workload.jfr"
                try:
                    driver.send("fixture.verify", {"directory": str(server / "fixture")})
                    fixture = driver.wait("fixture.verified")["data"]
                    if validate_fixture(fixture).status != Status.PASS:
                        raise ValueError("baseline native verification failed")
                    attempt.transition("READY")
                    if case.feature == "world-container":
                        driver.send("fixture.activate")
                        driver.wait("fixture.activated")
                    if profile:
                        outcomes.append(start_jfr(process, runtime.java, profile_path))
                    attempt.transition("WARMUP")
                    print(f"{case.id} {label}: warmup {case.warmup_seconds}s", flush=True)
                    warmup = collect_window(driver, "WARMUP", case.warmup_seconds, workload)
                    attempt.transition("MEASURING")
                    print(f"{case.id} {label}: measurement {case.measurement_seconds}s", flush=True)
                    measured = collect_window(driver, "MEASURING", case.measurement_seconds, workload)
                    atomic_write(attempt.directory / "samples.json", {"schemaVersion": 1, "warmup": warmup, "measurement": measured, "profiled": profile})
                    all_samples = warmup["samples"] + measured["samples"]
                    outcomes.append(validate_zero_players(all_samples))
                    if case.feature == "world-container":
                        outcomes.append(validate_activity(warmup["samples"], control, fixture["topLevelItemCount"]))
                        outcomes.append(validate_activity(measured["samples"], control, fixture["topLevelItemCount"]))
                    if any(sample.get("navigationSessions") != 0 for sample in all_samples):
                        outcomes.append(Decision(Status.FAIL, ("unexpected or unverified navigation sessions",)))
                    if case.feature == "inventory-regression":
                        if any(sample.get("syntheticEvents") != events for sample in measured["samples"]):
                            outcomes.append(Decision(Status.INCONCLUSIVE, ("synthetic offered demand not achieved",)))
                        expected_cancelled = events if not control and case.variant.startswith(("direct-tag", "nested-tag")) else 0
                        if any(sample.get("cancelledSyntheticEvents") != expected_cancelled for sample in measured["samples"]):
                            outcomes.append(Decision(Status.FAIL, ("navigation-item event cancellation mismatch",)))
                    pair["control" if control else "release"] = (warmup, measured)
                    if not control:
                        control_window = pair["control"][1]
                        samples = measured["samples"]
                        repetitions.append({
                            "tickNs": [sample["durationNs"] for sample in samples],
                            "workloadNs": [sample["workloadDurationNs"] for sample in samples],
                            "allocationBytes": [sample["allocatedBytes"] for sample in samples],
                            "controlWorkloadNs": [sample["workloadDurationNs"] for sample in control_window["samples"]],
                            "controlAllocationBytes": [sample["allocatedBytes"] for sample in control_window["samples"]],
                            "allocationScope": samples[0]["allocationScope"] if samples else "unsupported",
                            "tickMetric": "native-processing-duration", "warmupSeconds": warmup["seconds"], "measurementSeconds": measured["seconds"],
                            "controlPhysical": physical_summary(control_window), "candidatePhysical": physical_summary(measured),
                        })
                        if case.feature == "world-container":
                            outcomes.append(validate_physical_pair(physical_summary(control_window), physical_summary(measured), measured["initial"]["topLevelItemCount"]))
                        evidence.append(str(attempt.path))
                        atomic_write(partial, snapshot_result())
                    attempt.transition("QUIESCING")
                    driver.send("fixture.quiesce")
                    quiesced = driver.wait("fixture.quiesced")["data"]
                    if quiesced["topLevelItemCount"] != fixture["topLevelItemCount"]:
                        outcomes.append(Decision(Status.FAIL, ("item conservation failed after quiescence",)))
                    if profile:
                        outcomes.append(stop_jfr(process, runtime.java, profile_path))
                finally:
                    stop_owned(process, 30)
                verify_artifact(artifact, artifact_path)
                if not control:
                    verify_artifact(artifact, server / "plugins/audited.jar")
                attempt.transition("STOPPED")
                attempt.finish(0, "measurement retained; pair decision follows")
                if control:
                    evidence.append(str(attempt.path))
                atomic_write(partial, snapshot_result())
    return replace(snapshot_result(), cleanup="owned groups stopped")
