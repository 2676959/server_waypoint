#!/usr/bin/env python3
"""Run only explicitly listed disposable server roots; retain logs and reaped process results."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("manifest", type=Path)
parser.add_argument("--case", action="append", default=[])
args = parser.parse_args()
manifest = json.loads(args.manifest.read_text())
results = []
for case in manifest["cases"]:
    if args.case and case["name"] not in args.case:
        continue
    root = Path(case["cwd"]).resolve()
    if not (root / ".noise-platform-test").is_file():
        raise RuntimeError(f"Not a marked disposable environment: {root}")
    evidence = root / "noise-evidence.txt"
    if evidence.exists():
        raise RuntimeError(f"Refusing to reuse evidence: {evidence}")
    artifact = Path(case["artifact"])
    result = {"name": case["name"], "artifact_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
              "command": case["command"], "cwd": str(root), "status": "FAIL"}
    print("START", case["name"], flush=True)
    with (root / "console.log").open("w") as log:
        process = subprocess.Popen(case["command"], cwd=root, stdin=subprocess.PIPE,
                                   stdout=log, stderr=subprocess.STDOUT, text=True, start_new_session=True)
        try:
            deadline = time.monotonic() + case.get("timeout_seconds", 240)
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Server exited before probe and readiness")
                text = (root / "console.log").read_text(errors="replace")
                if evidence.exists():
                    proof = evidence.read_text()
                    if proof.startswith("FAIL"):
                        raise RuntimeError(proof)
                    if proof.startswith("PASS") and re.search(case["ready_pattern"], text):
                        break
                time.sleep(0.25)
            else:
                raise RuntimeError("Timed out awaiting classloader proof and server readiness")
            process.stdin.write(case.get("stop_command", "stop") + "\n")
            process.stdin.flush()
            code = process.wait(timeout=60)
            if code != 0:
                raise RuntimeError(f"Server shutdown exited {code}")
            result.update(status="PASS", evidence=evidence.read_text(), exit_code=code)
        except Exception as failure:
            result["failure"] = str(failure)
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
            process.stdin.close()
    (root / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    results.append(result)
    print(result["status"], case["name"], result.get("failure", ""), flush=True)
args.manifest.with_suffix(".results.json").write_text(json.dumps(results, indent=2) + "\n")
if not results or any(result["status"] != "PASS" for result in results):
    raise SystemExit(1)
