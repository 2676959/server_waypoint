"""Server-owned sync, upload and chat-sharing evidence.

The server snapshot is the authority. A client observation can confirm or
deny that snapshot, and a rejection or disconnect must not become a commit.
"""
from models import CaseResult, Decision, Status

_REJECTIONS = frozenset((
    "checksum", "oversize", "malformed", "header", "sequence", "timeout", "cancel", "permission",
))
_CHAT = frozenset(("unrelated", "invalid", "long", "duplicate", "denied", "valid"))
_REQUEST_WAYPOINT_LIMIT = 4096
_SHARE_LIMIT = 256
_SHARE_PREFIX = "xaero-waypoint"
_VOXEL_ICONS = frozenset((
    "apple", "axe", "boat", "camera", "carrot", "chicken", "cloud", "diamond",
    "fire", "fish", "flower", "gear", "heart", "hoe", "house", "key", "like",
    "minecart", "mushroom", "person", "pickaxe", "point", "record", "science",
    "shovel", "skull", "star", "steak", "temple", "tree", "wheat", "world", "waypoint",
))
_SCHEDULE = {
    "small": {"clients": 1, "waypoints": 1000, "requests": 1, "perRequest": 1000},
    "medium": {"clients": 2, "waypoints": 4096, "requests": 1, "perRequest": 4096},
    "stress": {"clients": 8, "waypoints": 16384, "requests": 4, "perRequest": 4096},
}


def download_revision(server, client, forced=False):
    if not isinstance(server, dict) or not isinstance(client, dict):
        return Decision(Status.BLOCKED, ("server snapshot missing",))
    if server.get("authority") != "server" or client.get("authority") != "client":
        return Decision(Status.BLOCKED, ("client/server ownership missing",))
    if type(server.get("revision")) is not int or server.get("kind") not in ("full", "incremental"):
        return Decision(Status.BLOCKED, ("server revision evidence missing",))
    if server["kind"] == "incremental" and type(server.get("baseRevision")) is not int:
        return Decision(Status.BLOCKED, ("incremental base revision missing",))
    mode = client.get("mode")
    if mode in ("incompatible", "vanilla"):
        denied = (
            client.get("delivered") is False
            and client.get("revision") is None
            and client.get("waypoints") == []
            and client.get("channel") is None
            and client.get("worldId") == server.get("worldId")
        )
        if denied:
            return Decision(Status.PASS)
        return Decision(Status.FAIL, ("unmodded or incompatible client received a server download",))
    if mode != "compatible":
        return Decision(Status.BLOCKED, ("client mode missing",))
    if client.get("delivered") is not True:
        reason = "forced refresh omitted the unchanged revision" if forced else "compatible download was not delivered"
        return Decision(Status.FAIL, (reason,))
    compared = ("revision", "kind", "baseRevision", "dimension", "list", "waypoints", "worldId", "channel")
    if any(client.get(key) != server.get(key) for key in compared):
        return Decision(Status.FAIL, ("client download differs from the server revision",))
    return Decision(Status.PASS)


def _resources(value):
    return (
        isinstance(value, dict)
        and type(value.get("revision")) is int
        and isinstance(value.get("waypoints"), list)
        and (value.get("leaseOwner") is None or isinstance(value.get("leaseOwner"), str))
        and type(value.get("retainedBytes")) is int
        and value["retainedBytes"] >= 0
        and type(value.get("buffers")) is int
        and value["buffers"] >= 0
    )


def upload_commit(before, attempt):
    if not _resources(before) or not isinstance(attempt, dict) or type(attempt.get("accepted")) is not bool:
        return Decision(Status.BLOCKED, ("upload evidence missing",))
    if attempt.get("reason") not in _REJECTIONS:
        return Decision(Status.FAIL, ("unknown upload rejection",))
    after = attempt.get("after")
    if not _resources(after):
        return Decision(Status.BLOCKED, ("committed state after rejection missing",))
    leaked = after["leaseOwner"] is not None or after["retainedBytes"] != 0 or after["buffers"] != 0
    changed = after["revision"] != before["revision"] or after["waypoints"] != before["waypoints"]
    if attempt["accepted"] or changed or leaked:
        return Decision(Status.FAIL, ("invalid upload changed committed state or retained the lease",))
    return Decision(Status.PASS)


def disconnect_lease(committed, during, after, disconnected):
    if type(committed) is not dict or type(committed.get("revision")) is not int or not isinstance(committed.get("waypoints"), list):
        return Decision(Status.BLOCKED, ("committed snapshot missing",))
    if not isinstance(during, dict) or any(key not in during for key in ("leaseOwner", "retainedBytes", "buffers", "partialWaypoints")):
        return Decision(Status.BLOCKED, ("in-flight lease evidence missing",))
    if type(during.get("retainedBytes")) is not int or type(during.get("buffers")) is not int or not isinstance(during.get("leaseOwner"), str):
        return Decision(Status.BLOCKED, ("in-flight lease evidence missing",))
    required = ("leaseOwner", "retainedBytes", "buffers", "revision", "waypoints", "nextOwner", "nextAcquired")
    if not isinstance(after, dict) or any(key not in after for key in required):
        return Decision(Status.BLOCKED, ("post-disconnect snapshot missing",))
    unchanged = after["revision"] == committed["revision"] and after["waypoints"] == committed["waypoints"]
    owner = during["leaseOwner"]
    if disconnected != owner:
        preserved = (
            unchanged
            and after["leaseOwner"] == owner
            and after["retainedBytes"] == during["retainedBytes"]
            and after["buffers"] == during["buffers"]
            and after["nextAcquired"] is False
        )
        if preserved:
            return Decision(Status.PASS)
        return Decision(Status.FAIL, ("disconnect of a non-owner released or committed the lease",))
    if not unchanged:
        return Decision(Status.FAIL, ("partial upload committed on disconnect",))
    if after["leaseOwner"] is not None or after["retainedBytes"] != 0 or after["buffers"] != 0:
        return Decision(Status.FAIL, ("owner disconnect left the lease or buffers",))
    if after["nextAcquired"] is not True or not isinstance(after.get("nextOwner"), str) or after["nextOwner"] == owner:
        return Decision(Status.FAIL, ("released lease was not acquirable by another client",))
    return Decision(Status.PASS)


def backpressure(samples):
    if not isinstance(samples, list) or not samples or not isinstance(samples[0], dict):
        return Decision(Status.BLOCKED, ("backpressure trace missing",))
    start = samples[0]
    if start.get("phase") != "held" or type(start.get("revision")) is not int or not isinstance(start.get("owner"), str):
        return Decision(Status.BLOCKED, ("lease hold missing",))
    revision = start["revision"]
    released = False
    for sample in samples:
        if not isinstance(sample, dict):
            return Decision(Status.BLOCKED, ("backpressure sample missing",))
        phase = sample.get("phase")
        if phase == "rejected" and sample.get("reason") not in ("busy", "byte-limit", "cooldown"):
            return Decision(Status.BLOCKED, ("rejection reason missing",))
        if phase == "rejected":
            count = sample.get("waypointCount")
            over_limit = type(count) is int and count > _REQUEST_WAYPOINT_LIMIT
            if sample.get("accepted") is not False or sample.get("committed") is not False or sample.get("revision") != revision:
                return Decision(Status.FAIL, ("rejected upload committed or was accepted",))
            if sample.get("reason") == "byte-limit" and not over_limit:
                return Decision(Status.FAIL, ("in-limit upload was rejected as over budget",))
        elif phase == "held":
            if released or sample.get("committed") is not False or sample.get("revision") != revision:
                return Decision(Status.FAIL, ("held lease committed or was reacquired early",))
        elif phase == "released":
            clean = (
                sample.get("committed") is False
                and sample.get("revision") == revision
                and sample.get("leaseOwner") is None
                and sample.get("retainedBytes") == 0
                and sample.get("buffers") == 0
                and sample.get("cooldownElapsed") is False
            )
            if not clean:
                return Decision(Status.FAIL, ("release left the lease, buffers, or skipped cooldown",))
            released = True
        elif phase == "recovered":
            count = sample.get("waypointCount")
            clean = (
                released
                and sample.get("reason") == "fulfilled"
                and sample.get("accepted") is True
                and sample.get("committed") is True
                and sample.get("cooldownElapsed") is True
                and sample.get("revision") == revision + 1
                and sample.get("leaseOwner") is None
                and sample.get("retainedBytes") == 0
                and sample.get("buffers") == 0
                and type(count) is int
                and 1 <= count <= _REQUEST_WAYPOINT_LIMIT
            )
            if not clean:
                return Decision(Status.FAIL, ("recovery overlapped the lease, ignored cooldown, or left buffers",))
        else:
            return Decision(Status.BLOCKED, ("unknown backpressure phase",))
    if not released:
        return Decision(Status.FAIL, ("backpressure never released the lease",))
    return Decision(Status.PASS)


def chat_effect(before, message, after):
    if type(before) is not dict or type(before.get("revision")) is not int or not isinstance(before.get("waypoints"), list):
        return Decision(Status.BLOCKED, ("chat baseline missing",))
    if not isinstance(message, dict) or message.get("kind") not in _CHAT or not isinstance(message.get("text"), str):
        return Decision(Status.BLOCKED, ("chat message evidence missing",))
    if not isinstance(after, dict) or type(after.get("revision")) is not int or not isinstance(after.get("waypoints"), list):
        return Decision(Status.BLOCKED, ("chat result missing",))
    unchanged = after["revision"] == before["revision"] and after["waypoints"] == before["waypoints"]
    if message["kind"] == "valid":
        if message.get("permission") != "add" or message.get("prompt") is not True or not unchanged:
            return Decision(Status.FAIL, ("chat share committed waypoints or missed the add prompt",))
        return Decision(Status.PASS)
    if not unchanged:
        return Decision(Status.FAIL, ("chat mutated stored waypoints",))
    return Decision(Status.PASS)


def run_case(case, root):
    return CaseResult(case, Decision(Status.BLOCKED, (
        f"native Paper sync/upload/sharing execution is not attached for {root}",
    )))


def upload_schedule(tier):
    if tier not in _SCHEDULE:
        raise ValueError("unknown upload tier")
    return dict(_SCHEDULE[tier])


def request_admission(count):
    if type(count) is not int or isinstance(count, bool) or count < 1:
        raise ValueError("waypoint count required")
    if count > _REQUEST_WAYPOINT_LIMIT:
        return {"outcome": "reject", "reason": "oversize", "waypoints": count}
    return {"outcome": "accept", "reason": "fulfilled", "waypoints": count}


def compressed_download(server, client, enabled):
    if type(enabled) is not bool or not isinstance(client, dict) or client.get("compressed") is not enabled:
        return Decision(Status.FAIL, ("compression flag does not match the download",))
    return download_revision(server, client, forced=True)


def merge_outcome(before, uploaded, policy, delete_missing, after, stale=False):
    if policy not in ("LOCAL", "SERVER") or type(delete_missing) is not bool or type(stale) is not bool:
        return Decision(Status.BLOCKED, ("merge policy evidence missing",))
    if not _revisioned(before) or not isinstance(uploaded, dict) or not _revisioned(after):
        return Decision(Status.BLOCKED, ("merge snapshots missing",))
    unchanged = after["revision"] == before["revision"] and after["waypoints"] == before["waypoints"]
    if stale or (delete_missing and policy != "LOCAL"):
        if unchanged:
            return Decision(Status.PASS)
        return Decision(Status.FAIL, ("stale or illegal delete changed the server store",))
    expected = {}
    for name, value in before["waypoints"].items():
        if name in uploaded:
            expected[name] = uploaded[name] if policy == "LOCAL" and uploaded[name] != value else value
        elif not delete_missing:
            expected[name] = value
    for name, value in uploaded.items():
        if name not in before["waypoints"]:
            expected[name] = value
    revision = before["revision"] + (1 if expected != before["waypoints"] else 0)
    if after["revision"] == revision and after["waypoints"] == expected:
        return Decision(Status.PASS)
    return Decision(Status.FAIL, ("merge result does not follow the conflict policy",))


def broadcast(update, recipients):
    if not isinstance(update, dict) or type(update.get("revision")) is not int or not isinstance(update.get("waypoints"), list):
        return Decision(Status.BLOCKED, ("broadcast update missing",))
    if not isinstance(recipients, list) or not recipients:
        return Decision(Status.BLOCKED, ("broadcast recipients missing",))
    for recipient in recipients:
        if not isinstance(recipient, dict):
            return Decision(Status.BLOCKED, ("broadcast recipient missing",))
        same_world = recipient.get("worldId") == update.get("worldId") and recipient.get("mode") == "compatible"
        if same_world:
            matched = (
                recipient.get("delivered") is True
                and recipient.get("revision") == update["revision"]
                and recipient.get("waypoints") == update["waypoints"]
                and recipient.get("channel") == update.get("channel")
            )
            if not matched:
                return Decision(Status.FAIL, ("compatible client on the updated world missed the broadcast",))
        elif recipient.get("delivered") is not False or recipient.get("revision") is not None or recipient.get("waypoints") != []:
            return Decision(Status.FAIL, ("broadcast reached another world or an incompatible client",))
    return Decision(Status.PASS)


def reconnect_identity(before, after):
    keys = ("worldId", "channel", "leaseOwner", "buffers", "retainedBytes", "delivered", "revision")
    if not isinstance(before, dict) or not isinstance(after, dict) or any(key not in before or key not in after for key in keys):
        return Decision(Status.BLOCKED, ("reconnect identity missing",))
    quiet = after["leaseOwner"] is None and after["buffers"] == 0 and after["retainedBytes"] == 0
    if after["channel"] != before["channel"] or not quiet:
        return Decision(Status.FAIL, ("reconnect changed the channel or left a lease",))
    if after["worldId"] == before["worldId"]:
        if after["delivered"] is True and after["revision"] == before["revision"]:
            return Decision(Status.PASS)
        return Decision(Status.FAIL, ("same-world reconnect did not keep the server revision",))
    if after["delivered"] is False and after["revision"] is None:
        return Decision(Status.PASS)
    return Decision(Status.FAIL, ("world change reused the previous revision",))


def classify_share(text, permission, seen):
    if not isinstance(text, str) or not isinstance(permission, str) or not isinstance(seen, tuple):
        raise ValueError("sharing evidence required")
    result = {"text": text, "permission": permission, "prompt": False, "name": None, "dimension": None}
    if len(text) > _SHARE_LIMIT:
        return {**result, "kind": "long"}
    if not text.startswith(_SHARE_PREFIX):
        return {**result, "kind": "unrelated"}
    parsed = _parse_share(text.split(":"))
    if parsed is None:
        return {**result, "kind": "invalid"}
    name, dimension = parsed
    result.update(name=name, dimension=dimension)
    if permission != "add":
        return {**result, "kind": "denied"}
    if (name, dimension) in seen:
        return {**result, "kind": "duplicate"}
    return {**result, "kind": "valid", "prompt": True}


def icon_registered(icon, registered):
    if icon is None:
        return True
    if not isinstance(icon, str) or len(icon) > _SHARE_LIMIT or not isinstance(registered, (tuple, list, set, frozenset)):
        return False
    namespace, separator, path = icon.partition(":")
    if separator != ":" or not namespace or not path or ":" in path:
        return False
    if namespace == "voxelmap":
        return path in _VOXEL_ICONS
    return icon in set(registered)


def _revisioned(value):
    return isinstance(value, dict) and type(value.get("revision")) is int and isinstance(value.get("waypoints"), dict)


def _parse_share(parts):
    if len(parts) != 10 or parts[0] != _SHARE_PREFIX or not parts[1]:
        return None
    if any(_integer(parts[index]) is None for index in (3, 4, 5, 6, 8)):
        return None
    dimension = _vanilla_dimension(parts[9])
    if dimension is None:
        return None
    return parts[1], dimension


def _integer(value):
    if not isinstance(value, str) or not value:
        return None
    try:
        return int(value, 10)
    except ValueError:
        return None


def _vanilla_dimension(token):
    dash = token.find("-")
    if dash < 0:
        return None
    name = token[dash + 1:]
    if name.endswith("-waypoints"):
        name = name[:-10]
    if name == "overworld":
        return "minecraft:overworld"
    if name == "the-nether":
        return "minecraft:the_nether"
    if name == "the-end":
        return "minecraft:the_end"
    if len(name) < 4:
        return None
    return name[4:].replace("$", ":").replace("%", "/").replace("-", "_")
