"""Storage/command evidence assertions and declared aggregate demand schedules."""
import math
from models import Decision, Status


def command_coverage(required, executions, suggestions):
    required = set(required)
    if not required:
        return Decision(Status.BLOCKED, ("native command tree is empty",))
    observed = {event.get("path") for event in executions if event.get("serverObserved") is True and event.get("feedbackObserved") is True}
    suggested = {event.get("path") for event in executions if event.get("suggestionsObserved") is True}
    missing = sorted(required - observed)
    missing_suggestions = sorted(set(suggestions) - suggested)
    reasons = tuple([f"unexecuted native command leaf: {path}" for path in missing] + [f"unobserved native suggestions: {path}" for path in missing_suggestions])
    return Decision(Status.BLOCKED if reasons else Status.PASS, reasons)


def denied_mutation(before, after, authorized):
    if authorized is not False:
        return Decision(Status.BLOCKED, ("permission-denial fixture was authorized",))
    return Decision(Status.PASS if before == after else Status.FAIL, () if before == after else ("denied operation mutated committed state/revision",))


def restore_once(events):
    groups = {}
    for event in events:
        token = event.get("token")
        if not token or type(event.get("success")) is not bool:
            return Decision(Status.BLOCKED, ("restore outcome/token missing",))
        groups.setdefault(token, []).append(event["success"])
    if any(outcomes.count(True) > 1 for outcomes in groups.values()):
        return Decision(Status.FAIL, ("restore token succeeded more than once",))
    if not groups or any(outcomes.count(True) != 1 or False not in outcomes for outcomes in groups.values()):
        return Decision(Status.BLOCKED, ("restore single-use positive/replay evidence missing",))
    return Decision(Status.PASS)


def stale_edit(expected_revision, before, after, accepted):
    if expected_revision == before.get("revision"):
        return Decision(Status.BLOCKED, ("edit fixture revision is not stale",))
    if accepted is not False or before != after:
        return Decision(Status.FAIL, ("stale edit accepted or changed state",))
    return Decision(Status.PASS)


def persisted_state(expected, actual):
    if expected != actual:
        return Decision(Status.FAIL, ("saved/reopened state differs from committed operation ledger",))
    return Decision(Status.PASS)


def workload_schedule(tier, seconds):
    rates = {"small": (10, 1), "medium": (50, 5), "stress": (100, 10)}
    if tier not in rates or type(seconds) not in (int, float) or not math.isfinite(seconds) or not 0 < seconds <= 1800:
        raise ValueError("invalid tier or bounded workload window")
    return tuple(sorted((index / rate, operation) for rate, operation in zip(rates[tier], ("read", "mutate")) for index in range(math.ceil(rate * seconds))))


def make_dataset(tier, skewed=False):
    """Declarative disk fixture, independently counted and checked after native load."""
    counts = {"small": (1000, 10), "medium": (10000, 100), "stress": (50000, 500)}
    if tier not in counts:
        raise ValueError("unknown dataset tier")
    count, list_count = counts[tier]
    dimensions = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
    dataset = {dimension: [] for dimension in dimensions}
    lists = []
    for index in range(list_count):
        value = {"list_name": f"perf_{index:02}", "n": 7, "waypoints": []}
        dataset[dimensions[index % 3]].append(value)
        lists.append(value)
    for index in range(count):
        if skewed:
            target = lists[0] if index < count * 9 // 10 else lists[1 + (index - count * 9 // 10) % (list_count - 1)]
        else:
            dimension = dimensions[index % 3]
            candidates = dataset[dimension]
            target = candidates[(index // 3) % len(candidates)]
        target["waypoints"].append({"name": f"wp_{index:05}", "initials": "P", "pos": [(index % 100) * 16, -61, -(index // 100) * 16], "color": "#123456", "yaw": 0, "global": True, "keywords": ["perf"], "description": "owned performance fixture"})
    return dataset


def canonical_saved(dimensions):
    """Normalize map iteration order while retaining every saved field and value."""
    result = {}
    for dimension, lists in dimensions.items():
        if len({value["list_name"] for value in lists}) != len(lists):
            raise ValueError("duplicate saved list identifier")
        canonical = []
        for value in lists:
            waypoints = value["waypoints"]
            if len({waypoint["name"] for waypoint in waypoints}) != len(waypoints):
                raise ValueError("duplicate saved waypoint identifier")
            canonical.append({**value, "waypoints": sorted(waypoints, key=lambda waypoint: waypoint["name"])})
        result[dimension] = sorted(canonical, key=lambda value: value["list_name"])
    return result


def canonical_snapshot(lists):
    keys = [(value["dimension"], value["name"]) for value in lists]
    if len(set(keys)) != len(keys):
        raise ValueError("duplicate native list identity")
    for value in lists:
        if len(set(value["waypoints"])) != len(value["waypoints"]):
            raise ValueError("duplicate native waypoint identity")
    return sorted(({**value, "waypoints": sorted(value["waypoints"])} for value in lists), key=lambda value: (value["dimension"], value["name"]))
