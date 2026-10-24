"""Process groups with in-process handles and UUID root ownership."""
import os
from pathlib import Path
import signal
import subprocess
import uuid
import time

from controller import owned_root
from models import OwnedProcess

_OWNED = {}


def start_owned(argv, cwd, log):
    cwd, log = Path(cwd).absolute(), Path(log).absolute()
    marker = owned_root(cwd)
    if log.is_symlink() or cwd not in log.parents or log.exists():
        raise ValueError("new owned log path required")
    log.parent.mkdir(parents=True, exist_ok=True)
    owner = str(uuid.uuid4())
    environment = dict(os.environ, SW_PERF_OWNER=owner, SW_PERF_RUN=marker["runId"])
    with log.open("xb") as stream:
        process = subprocess.Popen(list(argv), cwd=cwd, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT, env=environment, start_new_session=True)
    result = OwnedProcess(process.pid, process.pid, owner, cwd, log, process)
    _OWNED[owner] = result
    return result


def stop_owned(process, grace_seconds):
    if _OWNED.get(process.owner) is not process:
        raise ValueError("refusing to stop an unowned process")
    child = process.process
    try:
        if child.poll() is None:
            try:
                child.stdin.write(b"stop\n")
                child.stdin.flush()
            except (BrokenPipeError, OSError):
                pass
            try:
                child.wait(timeout=grace_seconds)
            except subprocess.TimeoutExpired:
                os.killpg(process.pgid, signal.SIGTERM)
                try:
                    child.wait(timeout=grace_seconds)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pgid, signal.SIGKILL)
                    child.wait(timeout=5)
        # Reap any child descendants left in this freshly-created group.
        try:
            os.killpg(process.pgid, signal.SIGTERM)
            deadline = time.monotonic() + grace_seconds
            while True:
                try:
                    os.killpg(process.pgid, 0)
                except ProcessLookupError:
                    break
                if time.monotonic() >= deadline:
                    os.killpg(process.pgid, signal.SIGKILL)
                    break
                time.sleep(0.01)
        except ProcessLookupError:
            pass
    finally:
        child.stdin.close()
        _OWNED.pop(process.owner, None)
