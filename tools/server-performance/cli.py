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
    args = parser.parse_args(argv)
    runtimes = load_runtimes(args.runtime_lock) if args.runtime_lock else ()
    cases = expand_cases(CONFIG / "features.json", CONFIG / "scenarios.json", runtimes, args.mode, tuple(args.tiers.split(",")))
    artifacts = load_release(args.release_manifest) if args.release_manifest.exists() else ()
    atomic_write(args.output / "inventory.json", {"schemaVersion": 1, "cardinality": len(cases), "artifacts": artifacts, "cases": cases})
    print(f"{len(cases)} required cases; {len(artifacts)} immutable artifacts; no servers started")
    return 0
