"""Explicit iteration selection; filters never produce a full-release claim."""
from registry import expand_cases


def select_cases(features, scenarios, runtimes, suite, tiers, targets=(), families=(), variants=(), mode="audit"):
    if targets and set(targets) - {r.id for r in runtimes}:
        raise ValueError("unknown runtime target")
    all_cases = expand_cases(features, scenarios, runtimes, mode, tiers)
    known_families = {c.feature for c in all_cases}
    if families and set(families) - known_families:
        raise ValueError("unknown scenario family")
    if variants and set(variants) - {c.variant for c in all_cases}:
        raise ValueError("unknown scenario variant")
    selected = tuple(case for case in all_cases if (not targets or case.runtime in targets) and (not families or case.feature in families) and (not variants or case.variant in variants) and (suite != "zero-player" or (case.clients == 0 and case.feature in ("idle", "world-container", "inventory-regression"))))
    if not selected:
        raise ValueError("selection contains no cases")
    return selected
