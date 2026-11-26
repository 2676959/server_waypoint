"""Local profiles and native post-disconnect counts, separate from timed acceptance."""
from contextlib import contextmanager
from pathlib import Path
import subprocess
import time

from contracts import atomic_write
from models import Decision, Status
from profiles import start_jfr, stop_jfr
from scenarios.navigation import cleanup_counts

RELEASED_RESOURCES = ("sessions", "tasks", "entities", "bossbars", "items", "mapTargets",
    "tickCounters", "targetIndexEntries")
QUIESCENT_TICKS = 10


@contextmanager
def capture_profile(process, java, output, enabled, outcomes):
    """Profiler errors retain a blocker alongside any independent product failure."""
    if not enabled:
        yield
        return
    path = Path(output)
    errors = []

    def capture(operation, label):
        try:
            return operation(process, java, path)
        except (OSError, RuntimeError, TimeoutError, ValueError, subprocess.SubprocessError) as error:
            reason = f"navigation profile {label}: {error}"
            errors.append(reason)
            atomic_write(path.with_suffix(".error.json"), {"schemaVersion": 1, "errors": errors})
            return Decision(Status.BLOCKED, (reason,))

    started = capture(start_jfr, "start")
    outcomes.append(started)
    try:
        yield
    finally:
        if started.status == Status.PASS:
            outcomes.append(capture(stop_jfr, "stop"))


def quiescence_decision(before, after, disconnected_tick):
    """Active state must clear; the existing per-owner map cache is deliberately retained."""
    if not isinstance(before, dict) or not isinstance(after, dict):
        return Decision(Status.BLOCKED, ("native quiescence snapshots missing",))
    if type(disconnected_tick) is not int or type(after.get("tick")) is not int or after["tick"] - disconnected_tick < QUIESCENT_TICKS:
        return Decision(Status.BLOCKED, ("ten native post-disconnect ticks were not observed",))
    if not isinstance(after.get("players"), list) or after["players"]:
        return Decision(Status.BLOCKED, ("native navigation owners have not disconnected",))
    resources = before.get("resources")
    retained = resources.get("mapCacheEntries") if isinstance(resources, dict) else None
    if type(retained) is not int or retained < 0 or not isinstance(after.get("resources"), dict):
        return Decision(Status.BLOCKED, ("native retained-cache/resource observation missing",))
    expected = {key: 0 for key in RELEASED_RESOURCES}
    expected["mapCacheEntries"] = retained
    return cleanup_counts(expected, after["resources"])


def disconnect_and_observe(probe, disconnect, output, timeout=30):
    """Disconnect without disabling navigation first, so stop/quit leaks remain visible."""
    path = Path(output) / "quiescence.json"
    before = after = None
    disconnected_tick = None
    decision = Decision(Status.BLOCKED, ("post-disconnect observation incomplete",))
    # NavigationAssertions counts inventory items only for online players. Its zero
    # after quit cannot prove that saved inventories or world drops were purged.
    item_decision = Decision(Status.BLOCKED, ("disconnected-owner inventories and world-item cleanup not observed",))
    try:
        probe.send("navigation.snapshot")
        before = probe.wait("navigation.snapshot")["data"]
        disconnect()
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            probe.send("navigation.snapshot")
            after = probe.wait("navigation.snapshot")["data"]
            if after.get("players") == [] and type(after.get("tick")) is int:
                if disconnected_tick is None:
                    disconnected_tick = after["tick"]
                if after["tick"] - disconnected_tick >= QUIESCENT_TICKS:
                    decision = quiescence_decision(before, after, disconnected_tick)
                    break
            else:
                disconnected_tick = None
            time.sleep(0.05)
    except (OSError, RuntimeError, TimeoutError, ValueError) as error:
        decision = Decision(Status.BLOCKED, (f"post-disconnect native observation unavailable: {error}",))
    finally:
        atomic_write(path, {"schemaVersion": 1, "before": before, "after": after,
            "disconnectedTick": disconnected_tick, "requiredNativeTicks": QUIESCENT_TICKS,
            "resourceDecision": decision, "itemDecision": item_decision,
            "unobserved": ["disconnected-owner inventories", "world items"]})
    return {"decisions": (decision, item_decision), "before": before, "after": after,
        "paths": {"quiescence": str(path)}}
