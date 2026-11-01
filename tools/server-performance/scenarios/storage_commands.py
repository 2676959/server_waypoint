"""Storage/command evidence assertions and declared aggregate demand schedules."""
import math
import re
from pathlib import Path
from contracts import read_document
from models import Decision, Status


def command_coverage(required, executions, suggestions):
    required = set(required)
    if not required:
        return Decision(Status.BLOCKED, ("native command tree is empty",))
    observed = {event.get("path") for event in executions if event.get("serverObserved") is True and event.get("feedbackObserved") is True}
    suggested = {event.get("path") for event in executions if event.get("suggestionsObserved") is True}
    missing = sorted(required - observed)
    missing_suggestions = sorted(set(suggestions) - suggested)
    failed_feedback = {event.get("path") for event in executions if event.get("serverObserved") is True and event.get("feedbackObserved") is False} & set(missing)
    reasons = tuple([f"missing native execution/feedback evidence: {path}" for path in missing] + [f"unobserved native suggestions: {path}" for path in missing_suggestions])
    return Decision(Status.FAIL if failed_feedback else Status.BLOCKED if reasons else Status.PASS, reasons)


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


def restore_rejection(token, remaining, event):
    if token in remaining:
        return Decision(Status.FAIL,("successful restore did not consume its token",))
    if not token or event.get("serverObserved") is not True or event.get("feedbackObserved") is not True or event.get("path") != "wp restore <token>" or event.get("input") != "wp restore " + token or event.get("actor") != "CONSOLE":
        return Decision(Status.BLOCKED,("native replay rejection receipt/feedback missing",))
    text = "\n".join(event.get("feedback",[])).lower()
    if "restore" not in text or "expired" not in text:
        return Decision(Status.FAIL,("restore replay did not report explicit invalid/expired token rejection",))
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
    if tier not in ("small","medium","stress") or type(seconds) not in (int, float) or not math.isfinite(seconds) or not 0 < seconds <= 1800:
        raise ValueError("invalid tier or bounded workload window")
    return tuple(sorted((index / rate, operation) for rate, operation in zip(declared_rates(tier)[:2], ("read", "mutate")) for index in range(math.ceil(rate * seconds))))


def declared_rates(tier, path=None):
    path = path or Path(__file__).resolve().parents[1] / "config/scenarios.json"
    rows = [row for row in read_document(path)["scenarios"] if row["feature"] in ("storage","commands")]
    if len(rows) != 2 or {row["feature"] for row in rows} != {"storage","commands"}:
        raise ValueError("both storage/command rate manifests required")
    rates = [tuple(row.get(key,{}).get(tier) for key in ("rates","mutationRates","suggestionRates")) for row in rows]
    if rates[0] != rates[1] or any(type(rate) is not int or not 0 < rate <= 100 for rate in rates[0]):
        raise ValueError("conflicting or unbounded manifest operation rates")
    return rates[0]


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


def planned_actions(tier, actors, seconds, prefix, mutation_offset=0, read_commands=None, mutation_mode="color", console_reads=False):
    if not actors or len(set(actors)) != len(actors) or not prefix:
        raise ValueError("distinct native actor identities and action prefix required")
    reads = read_commands or ("/wp list minecraft:overworld perf_00 limit 1", "/wp details list minecraft:overworld perf_00", "/wp help list")
    if any(not command.startswith("/wp ") or "\n" in command for command in reads):
        raise ValueError("bounded native read commands required")
    if mutation_mode not in ("color", "mixed"):
        raise ValueError("unmapped native mutation timing family")
    actions = []
    read_index, mutations = 0, mutation_offset
    for index, (at, operation) in enumerate(workload_schedule(tier, seconds)):
        transport = "bot"
        if operation == "read":
            actor = actors[read_index % len(actors)]
            command = reads[read_index % len(reads)]
            if console_reads and read_index % 10 == 9:
                transport = "console"
                if read_index % 20 == 9:
                    command = f"/execute as {actor} at {actor} run " + command[1:]
            read_index += 1
        else:
            actor = actors[0]
            mutations += 1
            if mutations >= 0xEFFFFF:
                raise ValueError("mutation fixture colour range exhausted")
            command = f"/wp edit waypoint minecraft:overworld perf_00 wp_00000 set color {0x100000 + mutations:06X}"
            if mutation_mode == "mixed" and mutations % 3 == 2:
                target = "alpha" if ((mutations - 2) // 3) % 2 == 0 else "charlie"
                command = f"/wp tp minecraft:overworld semantic {target}"
            elif mutation_mode == "mixed" and mutations % 3 == 0:
                command = "/wp reload"
        actions.append((at, {"actionId": f"{prefix}-{index}", "bot": actor, "operation": "command", "command": command,"kind":operation,"transport":transport}))
    return tuple(actions)


def acknowledged_latencies(actions, results):
    expected = {action["actionId"]: action for action in actions}
    if len(expected) != len(actions) or len(results) != len(actions):
        raise ValueError("one acknowledged result required for every distinct offered action")
    latencies = []
    seen = set()
    for event in results:
        identifier = event.get("actionId")
        action = expected.get(identifier)
        ack = event.get("nativeAck") or {}
        latency = event.get("clientElapsedNs")
        if action is None or identifier in seen or event.get("bot") != action["bot"] or event.get("clock") != "client-monotonic" or event.get("completionScope") != "client-roundtrip":
            raise ValueError("action lacks matching client roundtrip result")
        if ack.get("actionId") != identifier or ack.get("actor") != action["bot"] or ack.get("command") != action["command"][1:] or ack.get("serverObserved") is not True or ack.get("stage") != "command-completed" or ack.get("exception"):
            raise ValueError("action lacks successful exact native callback acknowledgement")
        if type(latency) is not int or latency <= 0:
            raise ValueError("invalid client monotonic roundtrip sample")
        seen.add(identifier)
        latencies.append(latency)
    return latencies


def process_allocation(before, after):
    if any(before.get(key) != after.get(key) for key in ("pid", "startedMs")) or before.get("pid") is None or before.get("startedMs") is None:
        raise ValueError("process allocation snapshots belong to different JVMs")
    if any(value.get("scope") != "whole-process" or type(value.get("allocatedBytes")) is not int or value["allocatedBytes"] < 0 for value in (before, after)) or after["allocatedBytes"] < before["allocatedBytes"]:
        raise ValueError("supported monotonic process allocation counters required")
    return after["allocatedBytes"] - before["allocatedBytes"]


def verify_rows(text, expected):
    rows = tuple(re.findall(r"^\[[^\]\n]+\] ([\w-]+) ·", text, re.MULTILINE))
    return Decision(Status.PASS if rows == tuple(expected) else Status.FAIL,
                    () if rows == tuple(expected) else (f"delivered rows {rows} differ from expected {tuple(expected)}",))


def leaf_ownership(tree):
    """Partition every release leaf without silently closing feature-owned coverage."""
    if tree.get("origin") != "native-minecraft-command-dispatcher":
        raise ValueError("native registered command tree required")
    owners = {key: [] for key in ("task10", "task11", "task12", "task15")}
    seen = set()
    for node in tree.get("nodes", []):
        if not node.get("executable"):
            continue
        path = node["path"]
        if path in seen:
            raise ValueError("duplicate native command leaf")
        seen.add(path)
        family = path.split()[1] if path.startswith("wp ") else path
        if family in ("wp", "help", "list", "details", "add", "edit", "remove", "restore", "reload", "tp"):
            owner = "task10"
        elif family == "navigate":
            owner = "task11"
        elif family in ("upload", "download"):
            owner = "task12"
        elif family in ("remote", "sw-cross-server-keygen"):
            owner = "task15"
        else:
            raise ValueError("unmapped native command family: " + path)
        owners[owner].append(path)
    if not seen:
        raise ValueError("native command tree is empty")
    return {key: sorted(paths) for key, paths in owners.items()}


def match_receipt(report, command, actor, path):
    matches = [event for event in report.get("callbacks", []) if event.get("path") == path
               and event.get("actor") == actor and event.get("input") == command
               and event.get("serverObserved") is True and not event.get("exception")]
    if report.get("droppedCallbacks") != 0 or len(matches) != 1:
        raise ValueError("native command leaf/actor/input callback missing or ambiguous")
    event = matches[0]
    if type(event.get("durationNs")) is not int or event["durationNs"] < 0 or type(event.get("allocatedBytes")) is not int or event["allocatedBytes"] < 0:
        raise ValueError("native callback timing/allocation unavailable")
    return event
