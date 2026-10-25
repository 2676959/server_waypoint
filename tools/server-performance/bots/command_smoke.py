"""Native command/storage probe smoke, with immutable release bytes and disk fixtures."""
import argparse
from dataclasses import replace
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256, verify_artifact
from contracts import atomic_write, load_release, load_runtimes
from controller import create_root
from processes import stop_owned
from scenarios.storage_commands import make_dataset
from worlds import install_server, boot


def run(root, tier):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools / "config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime, probe={**runtime.probe, "sha256": sha256(runtime.probe["path"])})
    artifact = next(a for a in load_release(tools / "config/releases/4.0.0.json") if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-cache") / artifact.name
    if not release.exists():
        release = Path("/Volumes/ssd/sw-performance-m0-audit/artifacts") / artifact.name
    verify_artifact(artifact, release)
    root = create_root(root)
    server = create_root(root / "server")
    install_server(runtime, server, runtime.probe["path"], release)
    dataset = make_dataset(tier)
    directory = server / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    for dimension, lists in dataset.items():
        (directory / (dimension.replace(":", "$") + ".json")).write_text(json.dumps(lists, indent=4) + "\n")
    atomic_write(root / "dataset.json", {"schemaVersion": 1, "tier": tier, "dimensions": dataset, "artifactSha256": artifact.sha256, "probeSha256": runtime.probe["sha256"]})
    process, probe = boot(runtime, server, False, artifact.sha256)
    try:
        probe.send("commands.tree")
        tree = probe.wait("commands.tree")["data"]
        atomic_write(root / "command-tree.json", {"schemaVersion": 1, **tree})
        probe.send("players.snapshot")
        snapshot = probe.wait("players.snapshot")["data"]
        atomic_write(root / "loaded.json", {"schemaVersion": 1, **snapshot})
        expected_count = sum(len(value["waypoints"]) for lists in dataset.values() for value in lists)
        if sum(len(value["waypoints"]) for value in snapshot["waypointLists"]) != expected_count:
            raise ValueError("native dataset load count mismatch")
        if any(value["revision"] != 7 for value in snapshot["waypointLists"]):
            raise ValueError("native dataset load revision mismatch")
        probe.send("commands.execute", {"commands": ["wp help", "wp list minecraft:overworld perf_00 limit 1"]})
        executed = probe.wait("commands.executed")["data"]
        atomic_write(root / "executed.json", {"schemaVersion": 1, **executed})
        if not executed.get("callbacks") or executed["callbacks"][0]["path"] != "wp help":
            raise ValueError("native command callback execution evidence missing")
        print(f"native {tier}: {expected_count} waypoints; command tree retained", flush=True)
    finally:
        stop_owned(process, 15)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--tier", choices=("small", "medium"), default="small")
    args = parser.parse_args()
    run(args.root, args.tier)
