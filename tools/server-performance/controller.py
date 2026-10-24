"""Owned roots and crash-safe preparation/attempt state."""
from dataclasses import replace
from pathlib import Path
import socket
import uuid
import time

from contracts import atomic_write, read_document, safe_name
from models import AttemptRecord

STAGES = ("CREATED", "STARTING", "READY", "WARMUP", "MEASURING", "QUIESCING", "STOPPED", "REPORTED")
PREPARATION = ("CREATED", "ARTIFACTS_VERIFIED", "FIXTURES_VALIDATED", "PREPARED")
REPOSITORY = Path(__file__).resolve().parents[2]


def reject_aliases(path):
    path = Path(path).absolute()
    if any(parent.is_symlink() for parent in (path, *path.parents)):
        raise ValueError("symlink runtime root/ancestor refused")
    if path == REPOSITORY or REPOSITORY in path.parents:
        raise ValueError("writable runtime roots must be outside the checkout")
    return path


def create_root(path):
    path = reject_aliases(path)
    if path.exists():
        raise ValueError("runtime root already exists")
    path.mkdir(parents=True)
    atomic_write(path / "owner.json", {"schemaVersion": 1, "runId": str(uuid.uuid4()), "state": "CREATED", "root": str(path)})
    return path


def owned_root(path):
    path = reject_aliases(path)
    marker = read_document(path / "owner.json")
    if marker["root"] != str(path) or str(uuid.UUID(marker["runId"])) != marker["runId"]:
        raise ValueError("root ownership mismatch")
    return marker


def preparation_transition(root, target):
    marker = owned_root(root)
    if PREPARATION.index(target) != PREPARATION.index(marker["state"]) + 1:
        raise ValueError("invalid preparation transition")
    marker["state"] = target
    atomic_write(Path(root) / "owner.json", marker)


def available_port(requested=0):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", requested))
        return listener.getsockname()[1]


class Attempt:
    def __init__(self, root, case_id):
        marker = owned_root(root)
        identifier = str(uuid.uuid4())
        self.directory = Path(root) / "attempts" / identifier
        self.directory.mkdir(parents=True)
        self.path = self.directory / "attempt.json"
        self.record = AttemptRecord(marker["runId"], identifier, case_id)
        atomic_write(self.path, self.record)

    def transition(self, stage):
        if self.record.state not in STAGES or stage not in STAGES or STAGES.index(stage) != STAGES.index(self.record.state) + 1:
            raise ValueError(f"invalid attempt transition: {self.record.state} -> {stage}")
        self.record = replace(self.record, state=stage, last_completed=stage)
        atomic_write(self.path, self.record)

    def finish(self, exit_code, reason=""):
        if self.record.state != "STOPPED":
            raise ValueError("attempt must stop before reporting")
        self.transition("REPORTED")
        self.record = replace(self.record, exit_code=exit_code, reason=reason)
        atomic_write(self.path, self.record)

    def __enter__(self):
        return self

    def __exit__(self, kind, error, traceback):
        if kind:
            interrupted = issubclass(kind, KeyboardInterrupt)
            self.record = replace(self.record, state="INTERRUPTED" if interrupted else "FAILED", reason=str(error) or kind.__name__, exit_code=130 if interrupted else 2)
            atomic_write(self.path, self.record)
        return False


def collect_window(driver, phase, seconds, workload=None):
    """Server acknowledges phase boundaries; Python only controls the deadline."""
    driver.send("scenario.start", {"phase": phase, **(workload or {})})
    initial = driver.wait("scenario.started")["data"]
    deadline = time.monotonic() + seconds
    samples = []
    from profiles import whole_process_sample
    process_samples = []
    next_process_sample = 0
    while time.monotonic() < deadline:
        if time.monotonic() >= next_process_sample:
            process_samples.append(whole_process_sample(driver.process.pid))
            next_process_sample = time.monotonic() + 1
        for event in driver.poll():
            if event["type"] == "sample":
                if event["data"]["phase"] != phase:
                    raise ValueError("sample from wrong measurement phase")
                samples.append(event["data"])
            elif event["type"] == "error":
                raise ValueError(event["data"])
        if driver.process.process.poll() is not None:
            raise RuntimeError("server exited during measurement")
        time.sleep(0.025)
    driver.send("scenario.stop")
    stop_deadline = time.monotonic() + 30
    while time.monotonic() < stop_deadline:
        for event in driver.poll():
            if event["type"] == "sample":
                samples.append(event["data"])
            elif event["type"] == "scenario.stopped":
                return {"schemaVersion": 1, "phase": phase, "seconds": event["data"]["phaseElapsedSeconds"], "initial": initial, "final": event["data"], "samples": samples, "processSamples": process_samples}
            elif event["type"] == "error":
                raise ValueError(event["data"])
        time.sleep(0.025)
    raise TimeoutError("server did not acknowledge measurement completion")
