"""Local JFR only. Failure is diagnostic evidence, never an erased measurement."""
from pathlib import Path
import subprocess
import time

from artifacts import sha256
from contracts import atomic_write
from models import Decision, Status


def start_jfr(process, java, output):
    output = Path(output)
    tool = Path(java).parent / "jcmd"
    if not tool.is_file() or output.exists() or output.is_symlink():
        return Decision(Status.BLOCKED, ("JFR tool missing or output unsafe",))
    output.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run([str(tool), str(process.pid), "JFR.start", "name=swperf", "settings=profile", f"filename={output}", "dumponexit=true"], capture_output=True, text=True, timeout=30)
    atomic_write(output.with_suffix(".start.json"), {"schemaVersion": 1, "jcmdSha256": sha256(tool), "exitCode": result.returncode, "output": result.stdout + result.stderr})
    return Decision(Status.PASS if result.returncode == 0 else Status.BLOCKED, () if result.returncode == 0 else ("JFR start failed",))


def stop_jfr(process, java, output):
    result = subprocess.run([str(Path(java).parent / "jcmd"), str(process.pid), "JFR.stop", "name=swperf"], capture_output=True, text=True, timeout=30)
    output = Path(output)
    good = result.returncode == 0 and output.is_file() and output.stat().st_size > 0
    atomic_write(output.with_suffix(".stop.json"), {"schemaVersion": 1, "exitCode": result.returncode, "output": result.stdout + result.stderr, "sha256": sha256(output) if good else None})
    return Decision(Status.PASS if good else Status.BLOCKED, () if good else ("JFR capture missing/failed",))


def whole_process_sample(pid):
    result = subprocess.run(["ps", "-o", "rss=,time=,%cpu=", "-p", str(pid)], capture_output=True, text=True, timeout=5)
    if result.returncode or not result.stdout.strip():
        raise RuntimeError("whole-process CPU/RSS sampler failed")
    rss, cpu_time, cpu_percent = result.stdout.split()
    return {"clock": "controller-monotonic", "monotonicSeconds": time.monotonic(), "rssBytes": int(rss) * 1024, "cumulativeCpuTime": cpu_time, "cpuPercent": float(cpu_percent), "scope": "whole-process"}
