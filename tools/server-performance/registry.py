"""Deterministic expansion; absent evidence never changes applicability."""
from contracts import read_document, load_release, load_runtimes
from models import CaseSpec, Decision, Status


def validate_entrypoints(entries, features):
    identifiers = set()
    for entry in entries:
        if entry["id"] in identifiers:
            raise ValueError("duplicate entrypoint")
        identifiers.add(entry["id"])
        if entry.get("feature") not in features and not entry.get("exclusion_reason"):
            raise ValueError(f"unmapped entrypoint: {entry['id']}")


def expand_cases(features, scenarios, runtimes, mode, tiers):
    if mode not in ("audit", "candidate") or not set(tiers) <= {"small", "medium", "stress"}:
        raise ValueError("invalid mode/tiers")
    registry = read_document(features)
    names = {feature["id"] for feature in registry["features"]}
    validate_entrypoints(registry["entrypoints"], names)
    definitions = read_document(scenarios)["scenarios"]
    if names != {scenario["feature"] for scenario in definitions}:
        raise ValueError("missing scenario family")
    result = []
    for runtime in (runtimes or (None,)):
        runtime_id = runtime.id if runtime else "unmapped"
        for scenario in definitions:
            for tier in sorted(set(tiers)):
                for variant in scenario["variants"]:
                    reason = ""
                    if scenario["feature"] == "private-development" and mode == "audit":
                        reason = "Private is absent at published release d9f532f8; development-only storage."
                    result.append(CaseSpec(
                        f"{runtime_id}:{scenario['feature']}:{variant}:{tier}", runtime_id,
                        scenario["feature"], variant, tier, mode,
                        variant if scenario["feature"] == "world-container" else scenario["fixture"],
                        tuple(scenario["metrics"]), tuple(scenario["budgets"]),
                        clients=1 if variant == "navigation-100" else scenario["clients"], offered_rate=scenario.get("rates", {}).get(tier, 0),
                        applicability_reason=reason))
    return tuple(sorted(result, key=lambda case: case.id))


def coverage_decision(expected, observed):
    missing = sorted(set(expected) - observed.keys())
    return Decision(Status.BLOCKED, tuple(f"missing case: {case}" for case in missing)) if missing else Decision(Status.PASS)
