"""CLI dispatch. Workload implementations live in focused modules."""
import argparse
from pathlib import Path

from contracts import atomic_write, load_release, load_runtimes
from registry import expand_cases

CONFIG = Path(__file__).resolve().parent / "config"


def main(argv=None):
    parser = argparse.ArgumentParser(description="Server Waypoint disposable performance audit")
    sub = parser.add_subparsers(dest="command", required=True)
    inventory = sub.add_parser("inventory")
    inventory.add_argument("--release-manifest", type=Path, default=CONFIG / "releases/4.0.0.json")
    inventory.add_argument("--runtime-lock", type=Path)
    inventory.add_argument("--output", type=Path, required=True)
    inventory.add_argument("--mode", choices=("audit", "candidate"), default="audit")
    inventory.add_argument("--tiers", default="small,medium,stress")
    doctor = sub.add_parser("doctor")
    doctor.add_argument("--runtime-lock", type=Path, default=CONFIG / "runtimes.json")
    doctor.add_argument("--output", type=Path, required=True)
    doctor.add_argument("--targets", default="")
    doctor.add_argument("--suite", choices=("all", "zero-player"), default="all")
    args = parser.parse_args(argv)
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
