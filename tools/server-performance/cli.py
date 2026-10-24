"""CLI dispatch. Workload implementations live in focused modules."""
import argparse
from pathlib import Path

from contracts import atomic_write, load_release, load_runtimes, read_document, load_case, load_result
from registry import expand_cases

CONFIG = Path(__file__).resolve().parent / "config"


def main(argv=None):
    parser = argparse.ArgumentParser(description="Server Waypoint disposable performance audit")
    sub = parser.add_subparsers(dest="command", required=True)
    inventory = sub.add_parser("inventory")
    inventory.add_argument("--release-manifest", type=Path, default=CONFIG / "releases/4.0.0.json")
    inventory.add_argument("--runtime-lock", type=Path, default=CONFIG / "runtimes.json")
    inventory.add_argument("--output", type=Path, required=True)
    inventory.add_argument("--mode", choices=("audit", "candidate"), default="audit")
    inventory.add_argument("--tiers", default="small,medium,stress")
    doctor = sub.add_parser("doctor")
    doctor.add_argument("--runtime-lock", type=Path, default=CONFIG / "runtimes.json")
    doctor.add_argument("--output", type=Path, required=True)
    doctor.add_argument("--targets", default="")
    doctor.add_argument("--suite", choices=("all", "zero-player"), default="all")
    prepare = sub.add_parser("prepare")
    prepare.add_argument("--release-manifest", type=Path, default=CONFIG / "releases/4.0.0.json")
    prepare.add_argument("--runtime-lock", type=Path, default=CONFIG / "runtimes.json")
    prepare.add_argument("--output", type=Path, required=True)
    prepare.add_argument("--cache", type=Path, default=Path("/Volumes/ssd/sw-performance-cache"))
    prepare.add_argument("--targets", default="paper-1.21.11")
    prepare.add_argument("--scenarios", default="idle,world-container,inventory-regression")
    prepare.add_argument("--variants", default="")
    prepare.add_argument("--tiers", default="small,medium,stress")
    prepare.add_argument("--suite", choices=("all", "zero-player"), default="zero-player")
    prepare.add_argument("--profile", action="store_true", help="Separate profiled diagnostic selection")
    run = sub.add_parser("run")
    run.add_argument("--root", type=Path, required=True)
    run.add_argument("--mode", choices=("audit", "candidate"), default="audit")
    run.add_argument("--suite", choices=("all", "zero-player"), default="zero-player")
    run.add_argument("--tiers", default="small,medium,stress")
    run.add_argument("--scenarios", default="")
    run.add_argument("--variants", default="")
    run.add_argument("--repeats", type=int, default=3)
    report = sub.add_parser("report")
    report.add_argument("--root", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.command == "prepare":
        from preparation import prepare as prepare_run
        print(prepare_run(args))
        return 0
    if args.command in ("run", "report"):
        import hashlib
        from dataclasses import replace
        from controller import owned_root
        from models import RunRecord
        from preparation import input_hashes, verify_evidence
        from reporting import write_report, combine_decisions, exit_code, report_axes, observed_decisions
        marker = owned_root(args.root)
        if marker["state"] != "PREPARED":
            raise ValueError("root not prepared")
        selection = read_document(args.root / "selection.json")
        cases = tuple(load_case(value) for value in selection["cases"])
        if args.command == "run":
            from scenarios.inventory import run_case, run_independent
            verify_evidence(args.root, read_document(args.root / "prepared.json"))
            if read_document(args.root / "prepared.json")["inputs"] != input_hashes():
                raise ValueError("executable suite/config inputs changed since preparation; use a new root")
            if args.repeats < 1:
                raise ValueError("repeats must be positive")
            cases = tuple(replace(case, repeats=args.repeats, mode=args.mode) for case in cases if case.tier in args.tiers.split(",") and (not args.scenarios or case.feature in args.scenarios.split(",")) and (not args.variants or case.variant in args.variants.split(",")) and (args.suite != "zero-player" or case.clients == 0))
            if not cases:
                raise ValueError("empty run selection")
            def execute(case):
                try:
                    result = run_case(case, args.root)
                except Exception as error:
                    from models import CaseResult, Decision, Status
                    result = CaseResult(case, Decision(Status.BLOCKED, (str(error),)), run_id=marker["runId"])
                identifier = hashlib.sha256(case.id.encode()).hexdigest()[:20]
                output = args.root / "results" / f"{identifier}.json"
                if output.exists():
                    # A retry has its own result identity, and never overwrites evidence.
                    import uuid
                    output = output.with_name(identifier + "-" + str(uuid.uuid4()) + ".json")
                atomic_write(output, result)
                return result
            results = [load_result(path) for path in sorted((args.root / "results").glob("*.json"))]
            try:
                for case in cases:
                    results.extend(run_independent((case,), execute))
                    write_report(RunRecord(marker["runId"], tuple(results), tuple(value["id"] for value in selection["cases"]), True), args.root)
            except KeyboardInterrupt:
                results = [load_result(path) for path in sorted((args.root / "results").glob("*.json"))]
                write_report(RunRecord(marker["runId"], tuple(results), tuple(value["id"] for value in selection["cases"]), True), args.root)
                return 130
        else:
            results = [load_result(path) for path in sorted((args.root / "results").glob("*.json"))]
            write_report(RunRecord(marker["runId"], tuple(results), tuple(c.id for c in cases), selection["partial"]), args.root)
        decision = report_axes(tuple(c.id for c in cases), observed_decisions(results))["decision"]
        print(f"{decision.status.value}; report: {args.root / 'report.md'}")
        return exit_code(decision)
    if args.command == "doctor":
        from preflight import check_runtime, required_capabilities
        runtimes = load_runtimes(args.runtime_lock)
        targets = set(args.targets.split(",")) if args.targets else {r.id for r in runtimes}
        if targets - {r.id for r in runtimes}:
            parser.error("unknown target")
        decisions = [check_runtime(r, args.output, required_capabilities(args.suite)) for r in runtimes if r.id in targets]
        atomic_write(args.output / "doctor.json", {"schemaVersion": 1, "suite": args.suite, "decisions": decisions})
        for runtime, decision in zip([r for r in runtimes if r.id in targets], decisions):
            print(f"{runtime.id}: {decision.status.value}: {'; '.join(decision.reasons)}")
        return 2 if any(d.status.value != "PASS" for d in decisions) else 0
    runtimes = load_runtimes(args.runtime_lock) if args.runtime_lock else ()
    cases = expand_cases(CONFIG / "features.json", CONFIG / "scenarios.json", runtimes, args.mode, tuple(args.tiers.split(",")))
    artifacts = load_release(args.release_manifest) if args.release_manifest.exists() else ()
    atomic_write(args.output / "inventory.json", {"schemaVersion": 1, "cardinality": len(cases), "artifacts": artifacts, "cases": cases})
    print(f"{len(cases)} required cases; {len(artifacts)} immutable artifacts; no servers started")
    return 0
