"""Coverage completeness and observed failures are independent report axes."""
from pathlib import Path
from html import escape

from contracts import atomic_write, read_document
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


def _cell(value):
    if value is None or value == "":
        return "Unavailable"
    return "<br>".join(escape(str(value), quote=False).replace("|", "&#124;").replace("`", "&#96;").splitlines())


def system_reference(environment):
    lines = ["## System build reference", ""]
    if environment is None:
        return lines + ["System information was not recorded for this run.", ""]
    memory = environment.get("memoryBytes")
    maximum = environment.get("cpuMaxHz")
    fields = (
        ("Captured at (preparation)", environment.get("capturedAt")),
        ("System", environment.get("platform")),
        ("OS version", environment.get("osVersion")),
        ("OS build", environment.get("osBuild")),
        ("Kernel release", environment.get("kernel")),
        ("Machine model", environment.get("machineModel")),
        ("Architecture", environment.get("machine")),
        ("CPU", environment.get("cpu")),
        ("Physical cores", environment.get("physicalCores")),
        ("Logical CPUs", environment.get("logicalCores")),
        ("Memory", f"{memory / 1024 ** 3:.2f} GiB ({memory:,} bytes)" if memory else None),
        ("OS-reported maximum CPU frequency", f"{maximum / 1_000_000_000:.2f} GHz" if maximum else None),
        ("Java flags", " ".join(environment.get("javaFlags", []))),
        ("Load generator", environment.get("loadGenerator")),
        ("Profiled diagnostic", environment.get("profiledDiagnostic")),
    )
    lines += ["| Property | Recorded value |", "| --- | --- |"]
    lines.extend(f"| {label} | {_cell(value)} |" for label, value in fields)
    lines += ["", "CPU clock is OS-managed; the suite applies no clock-speed cap. Actual frequency and thermal throttling are not sampled. Timing results apply to the recorded host and workload.", ""]
    lines += ["### Java runtime builds", "", "| Runtime | Java executable | Version/build |", "| --- | --- | --- |"]
    for runtime in environment.get("javaRuntimes", []):
        lines.append(f"| {_cell(runtime['id'])} | {_cell(runtime['java'])} | {_cell(runtime['version'])} |")
    return lines + [""]


def write_report(run, root):
    root = Path(root)
    environment_path = root / "environment.json"
    environment = read_document(environment_path) if environment_path.exists() else None
    axes = report_axes(run.expected, observed_decisions(run.results))
    atomic_write(root / "report.json", {"schemaVersion": 1, "run": run, "axes": axes, "environment": environment})
    atomic_write(root / "coverage.json", {"schemaVersion": 1, **axes, "partialSelection": run.partial})
    failures = [result for result in run.results if result.decision.status == Status.FAIL]
    atomic_write(root / "findings.json", {"schemaVersion": 1, "failures": failures})
    lines = ["# Performance report", "", f"Run: `{run.run_id}`. Selection: {'partial' if run.partial else 'full required registry'}.", f"Coverage complete within selection: {axes['complete']}. Decision: {axes['decision'].status.value}.", ""]
    lines += system_reference(environment)
    lines += ["## Results", "", "| Case | Decision | Reasons |", "| --- | --- | --- |"]
    for result in run.results:
        lines.append(f"| `{result.case.id}` | {result.decision.status.value} | {'; '.join(result.decision.reasons)} |")
    (root / "report.md").write_text("\n".join(lines) + "\n")
