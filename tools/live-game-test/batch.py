"""One unattended command: select, export, prepare, run, retain and summarize."""
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import redirect_stdout
from datetime import datetime, timezone
import fcntl
import json
from pathlib import Path
import threading
import uuid

from clients import (choose_jdk, compile_exporter, expand_versions,
                     export_client, find_hmcl, jdk_homes, runtime_fabric_api, select_client)
from diagnostics import print_result, write_summary
from prepare import prepare
from runner import fresh_directory, run_profile, write_json


def run_batch(args):
    logs = args.logs.resolve()
    logs.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = fresh_directory(logs / (stamp + "-" + uuid.uuid4().hex[:8]))
    output.chmod(0o700)
    results, ready = [], []
    cancelled = threading.Event()
    versions = args.clients.resolve()
    repo = args.repo.resolve(strict=True)
    names = expand_versions(versions, args.versions)
    jdk = classpath = None
    homes = jdk_homes()
    if args.jdk:
        homes.insert(0, args.jdk.resolve())
    groups = defaultdict(list)
    interrupted = False

    def failed(name, stage, failure):
        safe = name if "/" not in name and name not in {".", ".."} else "invalid-" + uuid.uuid4().hex[:8]
        home = output / safe
        home.mkdir(exist_ok=True)
        row = {"profile": name, "suite": args.suite, "status": "FAIL", "stage": stage,
               "failure": str(failure), "evidence": str(home)}
        write_json(home / "result.json", row)
        results.append(row)
        print_result(row)
        write_summary(output, results)

    # All writes to reusable installed clients are serialized across batch invocations.
    lock_path = versions.parent / ".live-game-test.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("This client store already has a running batch")
        try:
            for name in names:
                stage = "selection"
                try:
                    row = select_client(repo, versions, name)
                    name = row["name"]
                    home = fresh_directory(output / name)
                    stage = "export"
                    script = next((directory / (name + ".sh") for directory in args.exports
                                   if (directory / (name + ".sh")).is_file()), None)
                    if script:
                        row["launch_script"] = str(script.resolve())
                        row["launcher_agents"] = []
                    else:
                        if jdk is None:
                            jdk = choose_jdk([args.jdk.resolve()] if args.jdk else homes, 25)
                        if classpath is None:
                            classpath = compile_exporter(find_hmcl(args.hmcl), jdk, output)
                        export_client(row, versions, jdk, classpath, home, homes)
                    stage = "dependencies"
                    runtime_fabric_api(row, versions)
                    if args.suite == "core":
                        row["suite"] = "core"
                    groups[(row["target"], row.get("suite", "editor"))].append(row)
                except Exception as failure:
                    # Never use an unvalidated request string as a directory name.
                    failed(name, stage, failure)
            for index, rows in enumerate(groups.values(), 1):
                build = output / ("prepare-" + str(index))
                manifest = output / ("manifest-" + str(index) + ".json")
                write_json(manifest, {"profiles": rows})
                try:
                    if jdk is None:
                        jdk = choose_jdk([args.jdk.resolve()] if args.jdk else homes, 25)
                    with (output / ("prepare-" + str(index) + ".log")).open("w") as stream, redirect_stdout(stream):
                        prepare(repo, manifest, build, jdk, [])
                    prepared = json.loads((build / "prepared.json").read_text())
                    ready.extend((build, row) for row in prepared["profiles"])
                except Exception as failure:
                    for row in rows:
                        failed(row["name"], "prepare", failure)
            pool = ThreadPoolExecutor(max_workers=args.jobs)
            futures = {}
            try:
                for build, row in ready:
                    # Export/setup evidence lives beside runtime evidence in this profile directory.
                    futures[pool.submit(run_profile, build, row, output, args.suite, args.heap,
                                        args.startup_timeout, args.step_timeout, cancelled,
                                        quiet=True, existing_home=True)] = row
                for future in as_completed(futures):
                    row = futures[future]
                    try:
                        result = future.result()
                        result["evidence"] = str(output / row["name"])
                        results.append(result)
                        print_result(result)
                        write_summary(output, results)
                    except Exception as failure:
                        failed(row["name"], "runner", failure)
            finally:
                cancelled.set()
                for future in futures:
                    future.cancel()
                pool.shutdown(wait=True)
        except KeyboardInterrupt:
            interrupted = True
        finally:
            # Recover client results produced during cancellation and label all unstarted profiles.
            by_name = {row["profile"].lower(): row for row in results}
            for path in output.glob("*/result.json"):
                row = json.loads(path.read_text())
                row["evidence"] = str(path.parent)
                by_name[row["profile"].lower()] = row
            for name in names:
                if name not in by_name:
                    by_name[name] = {"profile": name, "suite": args.suite, "status": "FAIL",
                                     "stage": "interrupted", "failure": "Batch interrupted before completion"}
                    safe = name if "/" not in name and name not in {".", ".."} else "invalid-" + uuid.uuid4().hex[:8]
                    home = output / safe
                    home.mkdir(exist_ok=True)
                    by_name[name]["evidence"] = str(home)
                    write_json(home / "result.json", by_name[name])
            results = sorted(by_name.values(), key=lambda row: row["profile"].lower())
            write_summary(output, results)
    passed = sum(row["status"] == "PASS" for row in results)
    print(f"{passed}/{len(results)} passed; {len(results) - passed} failed; logs={output}", flush=True)
    return 130 if interrupted else (0 if passed == len(results) else 1)
