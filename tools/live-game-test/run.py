#!/usr/bin/env python3
"""Prepare and run production Minecraft mixin/editor checks."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import fcntl
import json
from pathlib import Path
import sys
import threading

from prepare import prepare
from clients import default_clients
from runner import fresh_directory, run_profile, select_profiles, sha256, write_json


TOOL = Path(__file__).resolve().parent
REPO = TOOL.parent.parent


def create_manifest(args):
    rows = select_profiles(json.loads(args.matrix.read_text()), args.profile)
    overrides = {}
    for entry in args.fabric_api:
        name, separator, value = entry.partition("=")
        if not separator or name in overrides or name not in {row["name"] for row in rows}:
            raise ValueError("Use --fabric-api SELECTED_PROFILE=/absolute/path/api.jar once per profile")
        path = Path(value).resolve(strict=True)
        overrides[name] = {"path": str(path), "sha256": sha256(path)}
    profiles = []
    for row in rows:
        candidates = [directory / (row["name"] + ".sh") for directory in args.exports]
        script = next((path.resolve() for path in candidates if path.is_file()), None)
        if script is None:
            raise ValueError("No launch export for " + row["name"])
        from runner import read_launch_script
        launch, _ = read_launch_script(script)
        agents = []
        for argument in launch:
            if argument.startswith("-javaagent:"):
                agent = Path(argument[len("-javaagent:"):].split("=", 1)[0]).resolve(strict=True)
                if agent not in [path.resolve(strict=True) for path in args.allow_launcher_agent]:
                    raise ValueError("Declare this export's launcher helper with --allow-launcher-agent: " + str(agent))
                agents.append({"path": str(agent), "sha256": sha256(agent)})
        profile = {**row, "launch_script": str(script), "launcher_agents": agents}
        if row["name"] in overrides:
            profile["fabric_api"] = overrides[row["name"]]
            from prepare import fabric_api_override
            fabric_api_override(profile)
        profiles.append(profile)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x") as stream:
        json.dump({"profiles": profiles}, stream, indent=4)
        stream.write("\n")
    print("Manifest:", args.output, flush=True)


def run(args):
    prepared = args.prepared.resolve(strict=True)
    data = json.loads((prepared / "prepared.json").read_text())
    for name in ("tools", "agent"):
        if sha256(prepared / (name + ".jar")) != data[name + "_sha256"]:
            raise ValueError("Prepared probe JAR changed: " + name)
    rows = select_profiles(data["profiles"], args.profile)
    output = fresh_directory(args.output)
    results = []
    cancelled = threading.Event()
    # Independent invocations against one preparation cannot multiply its client cap.
    with (prepared / "run.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("This preparation already has a running verification")
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            futures = [pool.submit(run_profile, prepared, row, output, args.suite, args.heap,
                                   args.startup_timeout, args.step_timeout, cancelled) for row in rows]
            try:
                for future in futures:
                    results.append(future.result())
                    write_json(output / "summary.json", results)
            except KeyboardInterrupt:
                cancelled.set()
                for future in futures:
                    future.cancel()
                raise
            finally:
                # Each profile records its own result even if interrupted.
                pool.shutdown(wait=True)
                results = [json.loads(path.read_text()) for path in sorted(output.glob("*/result.json"))]
                write_json(output / "summary.json", results)
                text = ["# Live Minecraft verification", "", "| Profile | Suite | Outcome |", "| --- | --- | --- |"]
                text += [f"| {row['profile']} | {row['suite']} | {row['status']} |" for row in results]
                text += ["", "Native game-thread/editor checks; this does not certify visual appearance or every gameplay path.", ""]
                (output / "report.md").write_text("\n".join(text))
    print("Report:", output / "report.md", flush=True)
    if len(results) != len(rows) or any(row["status"] != "PASS" for row in results):
        return 1
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    batch = commands.add_parser("batch", help="Unattended build and live tests using reusable installed clients")
    batch.add_argument("--versions", nargs="+", required=True, metavar="VERSION[-LOADER]")
    batch.add_argument("--clients", type=Path, default=default_clients(),
                       help="Installed versions directory; LIVE_GAME_TEST_CLIENTS or ~/.minecraft/versions")
    batch.add_argument("--logs", type=Path, required=True, help="Full evidence root; each invocation creates a fresh subdirectory")
    batch.add_argument("--repo", type=Path, default=REPO)
    batch.add_argument("--jdk", type=Path, help="Compile JDK 25+ home; automatically discovered by default")
    batch.add_argument("--hmcl", type=Path, help="HMCL JAR; defaults to LIVE_GAME_TEST_HMCL")
    batch.add_argument("--exports", type=Path, action="append", default=[], help="Optional pre-exported launch directory")
    batch.add_argument("--suite", choices=["audit", "editor", "core"], default="editor")
    batch.add_argument("--jobs", type=int, choices=range(1, 5), default=1)
    batch.add_argument("--heap", type=int, choices=[512, 1024, 2048], default=1024)
    batch.add_argument("--startup-timeout", type=int, default=300)
    batch.add_argument("--step-timeout", type=int, default=180)
    manifest = commands.add_parser("manifest", help="Create a private manifest from literal HMCL launch exports")
    manifest.add_argument("--matrix", type=Path, default=TOOL / "top-downloads.json")
    manifest.add_argument("--exports", type=Path, action="append", required=True)
    manifest.add_argument("--output", type=Path, required=True)
    manifest.add_argument("--profile", action="append", default=[])
    manifest.add_argument("--allow-launcher-agent", type=Path, action="append", default=[])
    manifest.add_argument("--fabric-api", action="append", default=[], metavar="PROFILE=PATH",
                          help="Explicit client Fabric API override when runtime differs from build target")
    build = commands.add_parser("prepare", help="Assemble production JARs and compile/remap fresh probes")
    build.add_argument("--repo", type=Path, default=REPO)
    build.add_argument("--manifest", type=Path, required=True)
    build.add_argument("--output", type=Path, required=True)
    build.add_argument("--jdk", type=Path, required=True, help="JDK 25+ home, used for compilation")
    build.add_argument("--profile", action="append", default=[])
    launch = commands.add_parser("run", help="Launch isolated production clients and retain evidence")
    launch.add_argument("--prepared", type=Path, required=True)
    launch.add_argument("--output", type=Path, required=True)
    launch.add_argument("--profile", action="append", default=[])
    launch.add_argument("--suite", choices=["audit", "editor"], default="editor")
    launch.add_argument("--jobs", type=int, choices=range(1, 5), default=1)
    launch.add_argument("--heap", type=int, choices=[512, 1024, 2048], default=1024)
    launch.add_argument("--startup-timeout", type=int, default=300)
    launch.add_argument("--step-timeout", type=int, default=180)
    args = parser.parse_args()
    try:
        if args.command == "manifest":
            create_manifest(args)
        elif args.command == "prepare":
            prepare(args.repo.resolve(strict=True), args.manifest.resolve(strict=True), args.output,
                    args.jdk.resolve(strict=True), args.profile)
        else:
            if args.startup_timeout <= 0 or args.step_timeout <= 0:
                parser.error("Timeouts must be positive")
            if args.command == "batch":
                from batch import run_batch
                return run_batch(args)
            return run(args)
    except KeyboardInterrupt:
        print("Interrupted; owned clients are being stopped and results retained.", file=sys.stderr)
        return 130
    except Exception as failure:
        # Never print the contents of launch exports or Java arguments.
        print(type(failure).__name__ + ": " + str(failure), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
