"""Explicit iteration selection; filters never produce a full-release claim."""
from registry import expand_cases, minimal_cases


def select_cases(features, scenarios, runtimes, suite, tiers, targets=(), families=(), variants=(), mode="audit"):
    if targets and set(targets) - {r.id for r in runtimes}:
        raise ValueError("unknown runtime target")
    if suite not in ("all", "zero-player", "minimal-critical"):
        raise ValueError("unknown suite")
    if suite == "minimal-critical":
        if targets and set(targets) != {"paper-1.21.11"}:
            raise ValueError("minimal-critical supports only Paper 1.21.11")
        all_cases = minimal_cases(runtimes, mode)
    else:
        all_cases = expand_cases(features, scenarios, runtimes, mode, tiers)
    known_families = {c.feature for c in all_cases}
    if families and set(families) - known_families:
        raise ValueError("unknown scenario family")
    if variants and set(variants) - {c.variant for c in all_cases}:
        raise ValueError("unknown scenario variant")
    selected = tuple(case for case in all_cases if case.tier in tiers and (not targets or case.runtime in targets) and (not families or case.feature in families) and (not variants or case.variant in variants) and (suite != "zero-player" or (case.clients == 0 and case.feature in ("idle", "world-container", "inventory-regression"))))
    if not selected:
        raise ValueError("selection contains no cases")
    return selected
