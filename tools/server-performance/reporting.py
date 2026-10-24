"""Coverage completeness and observed failures are independent report axes."""
from pathlib import Path

from contracts import atomic_write
from models import Decision, Status

PRIORITY = {Status.FAIL: 5, Status.BLOCKED: 4, Status.INCONCLUSIVE: 3, Status.PASS: 2, Status.NOT_APPLICABLE: 1}


def combine_decisions(decisions):
    decisions = tuple(decisions)
    if not decisions:
        return Decision(Status.BLOCKED, ("no decisions",))
    return Decision(max(decisions, key=lambda d: PRIORITY[d.status]).status, tuple(reason for decision in decisions for reason in decision.reasons))


def report_axes(expected, observed):
    missing = sorted(set(expected) - observed.keys())
    decisions = list(observed.values())
    if missing:
        decisions.append(Decision(Status.BLOCKED, tuple(f"missing case: {case}" for case in missing)))
    return {"complete": not missing, "missing": missing, "decision": combine_decisions(decisions)}


def observed_decisions(results):
    groups = {}
    for result in results:
        groups.setdefault(result.case.id, []).append(result.decision)
    return {identifier: combine_decisions(decisions) for identifier, decisions in groups.items()}


def exit_code(decision):
    return 1 if decision.status == Status.FAIL else 0 if decision.status in (Status.PASS, Status.NOT_APPLICABLE) else 2


def write_report(run, root):
    root = Path(root)
    axes = report_axes(run.expected, observed_decisions(run.results))
    atomic_write(root / "report.json", {"schemaVersion": 1, "run": run, "axes": axes})
    atomic_write(root / "coverage.json", {"schemaVersion": 1, **axes, "partialSelection": run.partial})
    failures = [result for result in run.results if result.decision.status == Status.FAIL]
    atomic_write(root / "findings.json", {"schemaVersion": 1, "failures": failures})
    lines = ["# Performance report", "", f"Run: `{run.run_id}`. Selection: {'partial' if run.partial else 'full required registry'}.", f"Coverage complete within selection: {axes['complete']}. Decision: {axes['decision'].status.value}.", "", "| Case | Decision | Reasons |", "| --- | --- | --- |"]
    for result in run.results:
        lines.append(f"| `{result.case.id}` | {result.decision.status.value} | {'; '.join(result.decision.reasons)} |")
    (root / "report.md").write_text("\n".join(lines) + "\n")
