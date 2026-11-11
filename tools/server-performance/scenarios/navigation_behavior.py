"""Reusable Task 11 behavior. This module does not launch servers or bot processes."""
import json
from pathlib import Path

from models import Decision, Status


def accepted_receipt(expected_id, receipt):
    if not isinstance(receipt, dict) or not receipt.get("actionId"):
        return Decision(Status.BLOCKED, ("native receipt missing",))
    if receipt["actionId"] != expected_id:
        return Decision(Status.FAIL, ("stale or unrelated receipt",))
    return Decision(Status.PASS, ())


def same_region_viewers(before, after):
    """Simultaneous viewers of one terrain region. Distinct map IDs alone are owner separation."""
    before_rows = _viewer_rows(before)
    if isinstance(before_rows, Decision):
        return before_rows
    if after is None:
        return Decision(Status.BLOCKED, ("independent retarget observation missing",))
    after_rows = _viewer_rows(after)
    if isinstance(after_rows, Decision):
        return after_rows
    before_by = {row["uuid"]: row for row in before_rows}
    after_by = {row["uuid"]: row for row in after_rows}
    if set(before_by) != set(after_by):
        return Decision(Status.FAIL, ("retarget changed the owner set",))
    changed = []
    for identifier, row in before_by.items():
        later = after_by[identifier]
        if later["target"] != row["target"]:
            changed.append(identifier)
            continue
        if later["mapId"] != row["mapId"] or later["pixelsCrc32"] != row["pixelsCrc32"] or later["region"] != row["region"]:
            return Decision(Status.FAIL, ("other owner pixels, map id, or region changed",))
    if len(changed) != 1:
        return Decision(Status.FAIL, ("retarget did not change exactly one owner",))
    return Decision(Status.PASS, ())


def persisted_evicted_terrain(saved, evicted, reopened):
    if not isinstance(saved, dict) or not isinstance(reopened, dict):
        return Decision(Status.BLOCKED, ("persisted terrain evidence missing",))
    if saved.get("evicted") is not True or evicted is not True:
        return Decision(Status.BLOCKED, ("forced terrain eviction was not observed",))
    if type(saved.get("mapId")) is not int or type(saved.get("pixelsCrc32")) is not int or not isinstance(saved.get("region"), dict):
        return Decision(Status.BLOCKED, ("persisted terrain identity missing",))
    for key in ("mapId", "region", "pixelsCrc32"):
        if saved.get(key) != reopened.get(key):
            return Decision(Status.FAIL, ("reopened terrain does not match the persisted bytes",))
    return Decision(Status.PASS, ())


def boundary_transition(origin, destination):
    if not isinstance(origin, dict) or not isinstance(destination, dict) or not origin.get("dimension") or not destination.get("dimension"):
        return Decision(Status.BLOCKED, ("boundary coordinates missing",))
    if origin["dimension"] != destination["dimension"]:
        return Decision(Status.FAIL, ("dimension change is not a chunk or region boundary",))
    coordinates = (origin.get("x"), origin.get("z"), destination.get("x"), destination.get("z"))
    if any(type(value) is not int for value in coordinates):
        return Decision(Status.BLOCKED, ("boundary coordinates missing",))
    origin_x, origin_z, destination_x, destination_z = coordinates
    chunk = (origin_x >> 4) != (destination_x >> 4) or (origin_z >> 4) != (destination_z >> 4)
    if not chunk:
        return Decision(Status.FAIL, ("movement stayed inside one chunk",))
    return Decision(Status.PASS, ())


def viewer_from_player(player):
    maps = [item for item in player.get("items", []) if item.get("method") == "map"]
    if len(maps) != 1 or not isinstance(maps[0].get("mapState"), dict):
        return None
    state = maps[0]["mapState"]
    target = player.get("target") or {}
    if type(state.get("id")) is not int or type(state.get("colorsCrc32")) is not int or not isinstance(target.get("waypoint"), str):
        return None
    return {
        "uuid": player.get("uuid"),
        "mapId": state["id"],
        "target": target["waypoint"],
        "pixelsCrc32": state["colorsCrc32"],
        "region": {key: state.get(key) for key in ("dimension", "centerX", "centerZ", "scale")},
    }


def exercise_variant(case, probe, driver, output, owners=()):
    spec = VARIANT_CATALOG.get((case.feature, case.variant))
    if spec is None:
        return {"decisions": (Decision(Status.BLOCKED, (f"unmapped variant: {case.feature}/{case.variant}",)),), "assertions": {}, "paths": {}}
    if probe is None or driver is None:
        return {
            "decisions": (Decision(Status.BLOCKED, (f"{spec['operation']} native session not attached",)),),
            "assertions": {},
            "paths": {},
        }
    return _exercise_attached(case, spec, probe, driver, Path(output), owners)


def _exercise_attached(case, spec, probe, driver, output, owners=()):
    action_id = f"{case.feature}-{case.variant}-1"
    names = tuple(owners) or (spec["bot"],)
    driver.send("action", {"actionId": action_id, "bot": names[0], **spec["action"]})
    receipt = driver.wait("action_result")
    receipt_decision = accepted_receipt(action_id, receipt if isinstance(receipt, dict) else {})
    probe.send(spec["probe"])
    observed = probe.wait(spec["probe"])["data"]
    behavior = spec["judge"](observed)
    output.mkdir(parents=True, exist_ok=True)
    path = output / f"{case.variant}.json"
    recorded = observed if isinstance(observed, dict) else {"unrecorded": True}
    path.write_text(json.dumps(recorded, default=str) + "\n")
    return {
        "decisions": (receipt_decision, behavior),
        "assertions": {"receipt": receipt_decision.status == Status.PASS, "behavior": behavior.status == Status.PASS},
        "paths": {"observation": str(path)},
    }


def _judge_viewers(observed):
    return same_region_viewers(observed.get("before"), observed.get("after"))


def _judge_persist(observed):
    if not isinstance(observed, dict):
        return Decision(Status.BLOCKED, ("persisted terrain evidence missing",))
    reopened = observed.get("reopened")
    return persisted_evicted_terrain(observed.get("saved"), observed.get("evicted") is True, reopened if isinstance(reopened, dict) else None)


def _judge_boundary(observed):
    return boundary_transition(observed.get("origin"), observed.get("destination"))


def _judge_blocked(observed):
    del observed
    return Decision(Status.BLOCKED, ("native behavior observation is not yet judged by this variant",))


def _entry(operation, receipt, before, after, handler, probe, action, judge):
    return {
        "operation": operation,
        "receipt": receipt,
        "before": before,
        "after": after,
        "handler": handler,
        "probe": probe,
        "bot": "owner",
        "action": action,
        "judge": judge,
    }


VARIANT_CATALOG = {
    ("navigation", "methods"): _entry(
        "select every navigation method combination",
        "navigation.snapshot method items and packet_summary effects",
        "ordinary stone and diamond counts",
        "requested methods present and unrequested methods absent",
        "scenarios.navigation.method_coverage",
        "navigation.snapshot",
        {"operation": "command", "command": "/wp navigate minecraft:overworld nav_fixture a compass"},
        _judge_blocked),
    ("navigation", "lifecycle"): _entry(
        "move, arrive, disconnect and confirm quiescent navigation counts",
        "navigation.snapshot position and resource counts",
        "active session with a live target",
        "cleanup_counts after disconnect",
        "scenarios.navigation.cleanup_counts",
        "navigation.snapshot",
        {"operation": "walk", "milliseconds": 500},
        _judge_blocked),
    ("navigation", "retarget-edit-delete"): _entry(
        "retarget, edit and delete one waypoint",
        "navigation.snapshot target plus deleted_navigation owners",
        "two owners on distinct targets",
        "only the removed target's session stops",
        "scenarios.navigation.deleted_navigation",
        "navigation.snapshot",
        {"operation": "command", "command": "/wp navigate minecraft:overworld nav_fixture b"},
        _judge_blocked),
    ("navigation", "death-respawn-world-change"): _entry(
        "die, respawn and change worlds",
        "lifecycle.snapshot drops and world items",
        "ordinary drops and protected items before death",
        "ordinary drops conserved and protected items absent",
        "scenarios.navigation.death_protection",
        "lifecycle.snapshot",
        {"operation": "command", "command": "/kill"},
        _judge_blocked),
    ("navigation", "rejoin-restore"): _entry(
        "disconnect and rejoin the same navigation session",
        "navigation.snapshot restored methods, map id and target",
        "saved session identity",
        "restored_session matches the persisted target",
        "scenarios.navigation.restored_session",
        "navigation.snapshot",
        {"operation": "command", "command": "/wp navigate minecraft:overworld nav_fixture a all"},
        _judge_blocked),
    ("map-terrain", "warm-cold"): _entry(
        "render a warm region, switch cold, then restore the warm pixels",
        "navigation.snapshot map colors and client patch checksum",
        "independent 16384-pixel expectation",
        "terrain_roundtrip exact native and client pixels",
        "scenarios.navigation.terrain_roundtrip",
        "navigation.snapshot",
        {"operation": "command", "command": "/wp navigate minecraft:overworld terrain_fixture a map"},
        _judge_blocked),
    ("map-terrain", "multiple-viewers"): _entry(
        "two owners view one terrain region and one retargets",
        "navigation.snapshot map ids, pixels and targets",
        "same region, distinct owners and map ids",
        "the other owner's pixels and map id stay unchanged",
        "scenarios.navigation_behavior.same_region_viewers",
        "navigation.viewers",
        {"operation": "command", "command": "/wp navigate minecraft:overworld dimension_fixture overworld_b map"},
        _judge_viewers),
    ("map-terrain", "boundary-dimension"): _entry(
        "cross a chunk or region boundary and, separately, switch dimensions",
        "player positions and switched_dimension map identity",
        "position inside the origin chunk",
        "chunk or region coordinates changed without treating a dimension switch as that boundary",
        "scenarios.navigation_behavior.boundary_transition",
        "navigation.boundary",
        {"operation": "command", "command": "/tp @s -300 -61 -300"},
        _judge_boundary),
    ("map-terrain", "persist-evict-restart"): _entry(
        "force terrain eviction, save, restart and reopen",
        "saved terrain bytes and reopened map identity",
        "rendered region bytes",
        "evicted cache and byte-identical reopened terrain",
        "scenarios.navigation_behavior.persisted_evicted_terrain",
        "navigation.persisted",
        {"operation": "command", "command": "/wp navigate minecraft:overworld terrain_fixture b"},
        _judge_persist),
    ("protection", "inventory-click-drag-open-close"): _entry(
        "click, drag, open and close inventories",
        "protection receipts and container_cleanup graphs",
        "dirty protected fixture and ordinary control items",
        "protected items removed and ordinary items conserved",
        "scenarios.navigation.container_cleanup",
        "protection.container.snapshot",
        {"operation": "open-container", "x": 0, "y": -60, "z": 0},
        _judge_blocked),
    ("protection", "craft-result-cartography"): _entry(
        "prepare and take crafting and cartography results",
        "station prepare and click receipts",
        "protected or ordinary station matrix",
        "protected results stay unusable and ordinary results can be taken",
        "scenarios.navigation.station_outcome",
        "protection.station.snapshot",
        {"operation": "inventory", "from": 36, "to": 1},
        _judge_blocked),
    ("protection", "drop-pickup-spawn-move"): _entry(
        "drop, pick up, spawn and move items",
        "drop, item-spawn and world-entity receipts",
        "ordinary stack in hand",
        "correlated world item present, or blocked when the snapshot is missing",
        "scenarios.navigation.physical_drop_spawned",
        "protection.fixture.snapshot",
        {"operation": "drop", "all": True},
        _judge_blocked),
    ("protection", "entity-frame-equipment"): _entry(
        "pick up, frame and equip item entities",
        "entity interaction receipts and carrier snapshots",
        "protected item offered to a carrier",
        "protected items stay off the carrier",
        "scenarios.navigation.entity_outcome",
        "protection.entity.snapshot",
        {"operation": "entity-interact", "entityId": 1},
        _judge_blocked),
    ("protection", "death-reconcile"): _entry(
        "die with ordinary and protected items",
        "death drops and world-item snapshot",
        "ordinary inventory and protected navigation items",
        "ordinary drops conserved and protected items absent",
        "scenarios.navigation.death_protection",
        "lifecycle.snapshot",
        {"operation": "command", "command": "/kill"},
        _judge_blocked),
}


def _viewer_rows(viewers):
    rows = tuple(viewers or ())
    if len(rows) < 2:
        return Decision(Status.BLOCKED, ("same-region viewers require two simultaneous owners",))
    parsed = []
    regions = []
    for viewer in rows:
        region = viewer.get("region") if isinstance(viewer, dict) else None
        if not isinstance(viewer, dict) or not isinstance(viewer.get("uuid"), str) or type(viewer.get("mapId")) is not int or not isinstance(region, dict):
            return Decision(Status.BLOCKED, ("same-region viewer identity missing",))
        if any(region.get(key) is None for key in ("dimension", "centerX", "centerZ", "scale")) or type(viewer.get("pixelsCrc32")) is not int or not isinstance(viewer.get("target"), str):
            return Decision(Status.BLOCKED, ("same-region terrain identity missing",))
        parsed.append({
            "uuid": viewer["uuid"],
            "mapId": viewer["mapId"],
            "target": viewer["target"],
            "pixelsCrc32": viewer["pixelsCrc32"],
            "region": {key: region[key] for key in ("dimension", "centerX", "centerZ", "scale")},
        })
        regions.append(tuple(parsed[-1]["region"][key] for key in ("dimension", "centerX", "centerZ", "scale")))
    if len({row["uuid"] for row in parsed}) != len(parsed) or len({row["mapId"] for row in parsed}) != len(parsed):
        return Decision(Status.FAIL, ("owners share one navigation map",))
    if len(set(regions)) != 1:
        return Decision(Status.FAIL, ("viewers are not on the same terrain region",))
    return parsed
