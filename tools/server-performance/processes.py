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


def start_owned(argv, cwd, log, *, stderr_log=None):
    cwd, log = Path(cwd).absolute(), Path(log).absolute()
    marker = owned_root(cwd)
    if log.is_symlink() or cwd not in log.parents or log.exists():
        raise ValueError("new owned log path required")
    log.parent.mkdir(parents=True, exist_ok=True)
    owner = str(uuid.uuid4())
    environment = dict(os.environ, SW_PERF_OWNER=owner, SW_PERF_RUN=marker["runId"])
    from contextlib import ExitStack
    with ExitStack() as stack:
        stream = stack.enter_context(log.open("xb"))
        errors = subprocess.STDOUT
        if stderr_log is not None:
            stderr_log = Path(stderr_log).absolute()
            if stderr_log.is_symlink() or cwd not in stderr_log.parents or stderr_log.exists():
                raise ValueError("new owned stderr path required")
            errors = stack.enter_context(stderr_log.open("xb"))
        process = subprocess.Popen(list(argv), cwd=cwd, stdin=subprocess.PIPE, stdout=stream, stderr=errors, env=environment, start_new_session=True)
    result = OwnedProcess(process.pid, process.pid, owner, cwd, log, process)
    _OWNED[owner] = result
    return result


def stop_owned(process, grace_seconds, *, graceful_command=b"stop\n"):
    if _OWNED.get(process.owner) is not process:
        raise ValueError("refusing to stop an unowned process")
    child = process.process
    try:
        if child.poll() is None:
            try:
                if graceful_command is not None:
                    child.stdin.write(graceful_command)
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
