"""Validated SW_PERF messages. All deadlines use controller monotonic time."""
import json
from pathlib import Path
import time

from contracts import validate_document


class ProbeStream:
    def __init__(self, run_id, attempt_id):
        self.run_id, self.attempt_id = run_id, attempt_id
        self.sequence = 0

    def parse(self, line):
        if "SW_PERF " not in line:
            return None
        value = validate_document(json.loads(line.split("SW_PERF ", 1)[1]))
        if value.get("runId") != self.run_id or value.get("attemptId") != self.attempt_id:
            raise ValueError("stale/wrong probe identity")
        sequence = value.get("sequence")
        if type(sequence) is not int or sequence <= self.sequence:
            raise ValueError("duplicate/out-of-order probe sequence")
        self.sequence = sequence
        return value


class ProbeDriver:
    def __init__(self, process, run_id, attempt_id):
        self.process = process
        self.stream = ProbeStream(run_id, attempt_id)
        self.offset = 0

    def send(self, operation, payload=None):
        import base64
        encoded = base64.urlsafe_b64encode(json.dumps(payload or {}).encode()).decode()
        self.process.process.stdin.write(f"swperf {self.stream.run_id} {self.stream.attempt_id} {operation} {encoded}\n".encode())
        self.process.process.stdin.flush()

    def poll(self):
        with self.process.log.open() as stream:
            stream.seek(self.offset)
            values = []
            while True:
                position = stream.tell()
                line = stream.readline()
                if not line or not line.endswith("\n"):
                    stream.seek(position)
                    break
                value = self.stream.parse(line)
                if value:
                    values.append(value)
            self.offset = stream.tell()
        return values

    def wait(self, event_type, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for event in self.poll():
                if event["type"] == "error":
                    raise ValueError(event.get("data", {}))
                if event["type"] == event_type:
                    return event
            if self.process.process.poll() is not None:
                raise RuntimeError("server exited before probe readiness")
            time.sleep(0.05)
        raise TimeoutError(f"probe deadline: {event_type}")
