"""Timed Task 11 windows. Fixture setup is not counted as offered work."""
import json
import time
from pathlib import Path

from metrics import summarize
from models import Decision, Status
from scenarios.inventory_container import chest_position
from scenarios.navigation_behavior import VARIANT_CATALOG, accepted_receipt


def reject_stale_bytes(matches):
    stale = [name for name in ("bot", "probe", "artifact") if not matches.get(name)]
    if stale:
        return Decision(Status.BLOCKED, tuple(f"stale {name} bytes" for name in stale))
    return Decision(Status.PASS, ())


def action_schedule(case, seconds, owners=(), phase=""):
    operation = dict(VARIANT_CATALOG[(case.feature, case.variant)]["action"])
    names = tuple(owners) or ("owner",)
    prefix = f"{phase.lower()}-" if phase else ""
    count = int(case.offered_rate * seconds)
    timed_container = (case.feature, case.variant) == ("protection", "inventory-click-drag-open-close")
    actions = []
    for index in range(count):
        bot = names[index % len(names)]
        action = {"actionId": f"{prefix}workload-{index}", "bot": bot, **operation}
        if timed_container:
            action.update(chest_position(names.index(bot)))
            action["hold"] = False
        actions.append(action)
    return actions


def repetition_record(case, warmup_seconds, measurement_seconds, tick_ns, offered, completed, latencies, clock):
    del case
    return {
        "tickNs": list(tick_ns),
        "tickMetric": "native-processing-duration",
        "warmupSeconds": warmup_seconds,
        "measurementSeconds": measurement_seconds,
        "offeredActions": offered,
        "completedActions": completed,
        "actionLatencyNs": list(latencies),
        "actionLatencyClock": clock,
        "actionLatencyScope": "client-roundtrip",
    }


def inflight_limit(owners):
    """One open action per bot. A deeper queue wedges container menus."""
    return max(1, len(tuple(owners)))


def drain_allowance(pending):
    """Finish actions already offered by this phase. One second each, bounded."""
    return max(5.0, min(120.0, pending * 1.0))


def tick_p95_ns(samples):
    """Nearest-rank p95, the same index budgets.evaluate uses."""
    if not samples:
        return 0
    return summarize(samples, "ns")["p95"]


def delivery_status(offered, completed, driver_pending, tick_p95_ns):
    if completed == offered and driver_pending == 0:
        return Decision(Status.PASS, ())
    if driver_pending or tick_p95_ns < 40_000_000:
        return Decision(Status.INCONCLUSIVE, ("deficient bot did not deliver the offered workload",))
    if completed < offered:
        return Decision(Status.FAIL, ("server saturation missed offered work",))
    return Decision(Status.INCONCLUSIVE, ("workload delivery unclassified",))


def collect_window(case, probe, driver, phase, output, owners=()):
    """Pace the variant's own operation and retain native tick samples for the whole phase."""
    seconds = case.warmup_seconds if phase == "WARMUP" else case.measurement_seconds
    actions = action_schedule(case, seconds, owners, phase)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    probe.send("scenario.start", {"phase": phase, "operation": actions[0]["operation"] if actions else ""})
    probe.wait("scenario.started")
    started = time.monotonic()
    deadline = started + seconds
    cursor = 0
    receipts = []
    ticks = []
    while time.monotonic() < deadline:
        elapsed = time.monotonic() - started
        for event in probe.poll():
            if event.get("type") == "sample" and event.get("data", {}).get("phase") == phase:
                duration = event["data"].get("durationNs")
                if type(duration) is int and duration >= 0:
                    ticks.append(duration)
        for event in driver.poll():
            data = event.get("data") or {}
            if event.get("type") == "action_result" or (event.get("type") == "error" and data.get("actionId")):
                receipts.append(data)
        while cursor < len(actions) and len(driver.stream.pending) < inflight_limit(owners) and elapsed >= (cursor / case.offered_rate if case.offered_rate else seconds):
            driver.send("action", actions[cursor])
            cursor += 1
            elapsed = time.monotonic() - started
        time.sleep(0.01)
    drain_deadline = time.monotonic() + drain_allowance(len(driver.stream.pending))
    while driver.stream.pending and time.monotonic() < drain_deadline:
        for event in driver.poll():
            data = event.get("data") or {}
            if event.get("type") == "action_result" or (event.get("type") == "error" and data.get("actionId")):
                receipts.append(data)
        time.sleep(0.01)
    probe.send("scenario.stop")
    stopped = probe.wait("scenario.stopped")["data"]
    matched = []
    latencies = []
    for action in actions:
        receipt = next((item for item in receipts if item.get("actionId") == action["actionId"]), None)
        if receipt and accepted_receipt(action["actionId"], receipt).status == Status.PASS and receipt.get("clock") == "client-monotonic":
            latency = receipt.get("clientElapsedNs")
            if type(latency) is int and latency >= 0:
                matched.append(receipt)
                latencies.append(latency)
    path = output / f"{phase.lower()}-receipts.json"
    path.write_text(json.dumps({"schemaVersion": 1, "receipts": receipts}, indent=2) + "\n")
    return {
        "seconds": float(stopped.get("phaseElapsedSeconds", time.monotonic() - started)),
        "tickNs": ticks,
        "offeredActions": len(actions),
        "completedActions": len(matched),
        "driverPending": (len(actions) - cursor) + len(driver.stream.pending),
        "actionLatencyNs": latencies,
        "paths": {"receipts": str(path)},
    }
