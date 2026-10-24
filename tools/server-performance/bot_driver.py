"""Strict JSON bot stream; server observations independently establish readiness."""
import json
import math
import uuid
from contracts import validate_document
from models import Decision, Status


class BotStream:
    TYPES = {"ready", "action_result", "packet_summary", "rate_sample", "stopped", "error"}

    def __init__(self, run_id, attempt_id):
        self.run_id, self.attempt_id = run_id, attempt_id
        self.sequence = 0
        self.failures = []
        self.pending = set()
        self.completed = set()

    def offer(self, action_id):
        if not action_id or action_id in self.pending | self.completed or len(self.pending) >= 256:
            raise ValueError("duplicate action or bounded queue full")
        self.pending.add(action_id)

    def parse(self, value):
        value = validate_document(json.loads(value) if isinstance(value, str) else value)
        if value.get("runId") != self.run_id or value.get("attemptId") != self.attempt_id:
            raise ValueError("stale/wrong bot identity")
        sequence = value.get("sequence")
        if type(sequence) is not int or sequence <= self.sequence or value.get("type") not in self.TYPES:
            raise ValueError("duplicate/out-of-order/unknown bot event")
        data = value.get("data")
        if not isinstance(data, dict):
            raise ValueError("bot data must be an object")
        if value["type"] == "action_result":
            action_id = data.get("actionId")
            if action_id not in self.pending:
                raise ValueError("stale/unoffered bot action result")
            self.pending.remove(action_id)
            self.completed.add(action_id)
        if value["type"] == "error":
            self.failures.append(data.get("reason", "bot error"))
        self.sequence = sequence
        return value


def validate_ready(ready, server_players, count, mode):
    reasons = []
    bots = ready.get("bots", [])
    names = [b.get("name") for b in bots]
    identities = [b.get("uuid") for b in bots]
    observed = {(b.get("name"), b.get("uuid")) for b in server_players}
    if len(bots) != count or len(server_players) != count or len(set(names)) != count or len(set(identities)) != count:
        reasons.append("server/bot online count or unique identities mismatch")
    for bot in bots:
        try:
            uuid.UUID(bot["uuid"])
        except (ValueError, TypeError, KeyError):
            reasons.append("missing server UUID")
        if (bot.get("name"), bot.get("uuid")) not in observed or bot.get("connected") is not True or bot.get("mode") != mode:
            reasons.append("server-observed identity or negotiated mode missing")
        if mode != "vanilla" and bot.get("protocolAccepted") is not (mode == "compatible"):
            reasons.append("native protocol acceptance not proved")
    if ready.get("autoReconnect") is not False:
        reasons.append("automatic reconnect must be disabled")
    return Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))


def validate_rates(sample, max_lag_seconds):
    keys = ("offered", "completed", "pending", "elapsedSeconds", "maxLagSeconds")
    if any(type(sample.get(k)) not in (int, float) or not math.isfinite(sample[k]) or sample[k] < 0 for k in keys) or sample["elapsedSeconds"] <= 0 or sample["offered"] <= 0:
        return Decision(Status.INCONCLUSIVE, ("missing/invalid driver rate evidence",))
    if sample["completed"] > sample["offered"] or sample["completed"] + sample["pending"] != sample["offered"]:
        return Decision(Status.INCONCLUSIVE, ("driver action accounting mismatch",))
    if sample["pending"] or sample["completed"] != sample["offered"] or sample["maxLagSeconds"] > max_lag_seconds:
        return Decision(Status.INCONCLUSIVE, ("driver stalled/overloaded or offered actions incomplete",))
    return Decision(Status.PASS)


def validate_actions(expected, results, observations):
    """One native observation per requested operation, independently of client claims."""
    required = {}
    received = {(r.get("actionId"), r.get("bot")) for r in results}
    reasons = []
    for action in expected:
        name, operation = action["bot"], action["operation"]
        if (action["actionId"], name) not in received:
            reasons.append("missing correlated action result")
        counter = {"walk": "movementEvents", "inventory": "inventoryEvents", "command": "commandEvents"}.get(operation)
        if counter is None:
            reasons.append("unsupported native action assertion")
        else:
            required[(name, counter)] = required.get((name, counter), 0) + 1
    for (name, counter), count in required.items():
        if observations.get(name, {}).get(counter, 0) < count:
            reasons.append(f"{name}: server-observed {counter} below completed demand")
    return Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))


class BotDriver:
    def __init__(self, binary, root, run_id, attempt_id):
        from processes import start_owned
        from pathlib import Path
        self.process = start_owned([str(binary)], root, Path(root) / "bots.jsonl", stderr_log=Path(root) / "bots.stderr")
        self.stream = BotStream(run_id, attempt_id)
        self.sequence = 0
        self.offset = 0
        self.events = []

    def send(self, kind, data=None):
        if kind not in {"start", "action", "barrier", "quiesce", "stop"}:
            raise ValueError("unsupported bot command")
        if kind == "action":
            self.stream.offer(data["actionId"])
        self.sequence += 1
        value = {"schemaVersion": 1, "runId": self.stream.run_id, "attemptId": self.stream.attempt_id,
                 "sequence": self.sequence, "type": kind, "data": data or {}}
        self.process.process.stdin.write((json.dumps(value) + "\n").encode())
        self.process.process.stdin.flush()

    def poll(self):
        with self.process.log.open() as source:
            source.seek(self.offset)
            values = []
            while True:
                start = source.tell()
                line = source.readline()
                if not line or not line.endswith("\n"):
                    source.seek(start)
                    break
                value = self.stream.parse(line)
                values.append(value)
                self.events.append(value)
            self.offset = source.tell()
        return values

    def wait(self, kind, timeout=30):
        import time
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for event in self.poll():
                if event["type"] == "error":
                    raise RuntimeError(event["data"])
                if event["type"] == kind:
                    return event["data"]
            if self.process.process.poll() is not None:
                raise RuntimeError("bot process exited before expected event")
            time.sleep(0.02)
        raise TimeoutError(f"bot deadline: {kind}")

    def close(self):
        from processes import stop_owned
        try:
            if self.process.process.poll() is None:
                self.send("stop")
                self.wait("stopped", 10)
        finally:
            stop_owned(self.process, 2)
