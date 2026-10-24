"""Lifecycle simulator only; never runtime performance evidence."""
import sys
import subprocess

if len(sys.argv) > 1:
    from pathlib import Path
    child = subprocess.Popen([sys.executable, "-c", "import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); time.sleep(300)"])
    Path(sys.argv[1]).write_text(str(child.pid))

print("simulator ready", flush=True)
for line in sys.stdin:
    if line.strip() == "stop":
        break
