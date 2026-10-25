"""Budget semantics are strict/inclusive exactly as declared in the specification."""
from contracts import read_document
from metrics import summarize, incremental
from models import Decision, Status
import math


def compare_relative(candidate, baseline, unit, candidate_identity, baseline_identity, approved, noise_floor):
    if not approved:
        return Decision(Status.BLOCKED, ("healthy baseline lacks approval; original defect is not a baseline",))
    if candidate_identity != baseline_identity:
        return Decision(Status.INCONCLUSIVE, ("runtime/host/fixture/instrumentation pair differs",))
    if noise_floor is None:
        return Decision(Status.INCONCLUSIVE, ("scenario noise floor uncalibrated",))
    try:
        current, healthy = summarize(candidate, unit), summarize(baseline, unit)
    except ValueError as error:
        return Decision(Status.INCONCLUSIVE, (str(error),))
    difference = current["p95"] - healthy["p95"]
    failed = difference > noise_floor and current["p95"] > healthy["p95"] * 1.25
    return Decision(Status.FAIL if failed else Status.PASS, ("relative p95 regression >25% and noise floor",) if failed else ())


def evaluate(result, budgets):
    config = read_document(budgets)["budgets"]
    failures, incomplete = [], []
    if any(value is False for value in result.assertions.values()):
        failures.append("correctness assertion failed")
    repetitions = result.observations.get("repetitions", [])
    if len(repetitions) != result.case.repeats:
        incomplete.append("missing independent repetitions")
    for index, repetition in enumerate(repetitions, 1):
        prefix = f"repeat {index}"
        if "completed-actions" in result.case.metrics:
            offered = repetition.get("offeredActions")
            completed = repetition.get("completedActions")
            if type(offered) is not int or type(completed) is not int or offered <= 0 or completed != offered:
                incomplete.append(f"{prefix}: offered/completed action evidence missing or incomplete")
        if "action-latency" in result.case.metrics:
            try:
                action_latency = summarize(repetition.get("actionLatencyNs", []), "ns")
                if repetition.get("actionLatencyClock") != "client-monotonic" or repetition.get("actionLatencyScope") != "client-roundtrip":
                    incomplete.append(f"{prefix}: action latency is not acknowledged client roundtrip time")
                if action_latency["count"] != repetition.get("completedActions"):
                    incomplete.append(f"{prefix}: missing per-action latency samples")
            except (TypeError, ValueError):
                incomplete.append(f"{prefix}: missing/invalid acknowledged action latency")
        try:
            if any(not math.isfinite(repetition.get(key, 0)) or repetition.get(key, 0) < 0 for key in ("warmupSeconds", "measurementSeconds")):
                incomplete.append(f"{prefix}: invalid phase duration")
                continue
            ticks = summarize(repetition.get("tickNs", []), "ns")
            if ticks["count"] < result.case.minimum_samples:
                incomplete.append(f"{prefix}: too few native samples")
            if repetition.get("tickMetric") != "native-processing-duration":
                incomplete.append(f"{prefix}: invalid native tick metric")
                continue
            if repetition.get("warmupSeconds", 0) < result.case.warmup_seconds or repetition.get("measurementSeconds", 0) < result.case.measurement_seconds:
                incomplete.append(f"{prefix}: shortened measurement (smoke only)")
            if "backend-medium" in result.case.budgets and result.case.tier == "medium":
                limits = config["backend-medium"]
                if ticks["p95"] >= limits["p95MsExclusive"] * 1_000_000 or ticks["p99"] >= limits["p99MsExclusive"] * 1_000_000:
                    failures.append(f"{prefix}: native tick budget exceeded")
            if "inventory-100" in result.case.budgets and result.case.variant == "shulker-books-100":
                if repetition.get("allocationScope") != "server-thread":
                    incomplete.append(f"{prefix}: required server-thread allocation unavailable")
                    continue
                latency = incremental(repetition.get("workloadNs", []), repetition.get("controlWorkloadNs", []), "ns")
                allocation = incremental(repetition.get("allocationBytes", []), repetition.get("controlAllocationBytes", []), "bytes")
                limits = config["inventory-100"]
                if latency["p95"] > limits["p95MsInclusive"] * 1_000_000:
                    failures.append(f"{prefix}: inventory incremental p95 >5 ms")
                if allocation["max"] > limits["bytesPerTickInclusive"]:
                    failures.append(f"{prefix}: inventory allocation >2097152 bytes/tick")
        except (ValueError, TypeError):
            incomplete.append(f"{prefix}: invalid/empty measurement")
    return Decision(Status.FAIL if failures else Status.INCONCLUSIVE if incomplete else Status.PASS, tuple(failures + incomplete))
