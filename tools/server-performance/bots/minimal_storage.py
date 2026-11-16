"""One explicit native permission denial, checked against exact committed state."""
import time
import uuid
from contracts import atomic_write
from scenarios.command_behavior import snapshot, players
from scenarios.storage_commands import canonical_saved


def denied_edit(probe, driver, actor, expected, case, output):
    probe.send("commands.permissions", {"actor":actor, "operator":True,
               "permissions":{"server_waypoint.command.edit":False}})
    permission = probe.wait("commands.permissions")["data"]
    if permission.get("branches", {}).get("edit") is not False:
        raise ValueError("native explicit edit denial differs")
    before = canonical_saved(snapshot(probe))
    events = next(p for p in players(probe) if p["name"] == actor)["commandEvents"]
    action_id = "denied-" + uuid.uuid4().hex
    driver.send("action", {"actionId":action_id, "bot":actor, "operation":"command",
                           "command":"/" + case["command"].replace("112233", "AABBCC"), "acknowledged":False})
    action = driver.wait("action_result", 30)
    if action.get("actionId") != action_id or action.get("error"):
        raise ValueError("denied action receipt missing")
    deadline = time.monotonic() + 10
    while next(p for p in players(probe) if p["name"] == actor)["commandEvents"] <= events:
        if time.monotonic() >= deadline:
            raise TimeoutError("native denied preprocess receipt deadline")
        time.sleep(0.05)
    after = canonical_saved(snapshot(probe))
    atomic_write(output, {"schemaVersion":1, "permission":permission, "action":action,
                          "before":before, "after":after})
    return {"status":"PASS" if before == after == canonical_saved(expected) else "FAIL",
            "reasons":[] if before == after == canonical_saved(expected) else ["denied edit mutated committed state"]}


def select_mutations(tree):
    from scenarios.mutation_leaves import mutation_cases
    paths = (
        "wp add <dimension> <list identifier> <position> <waypoint identifier> <initials> <color> <yaw> <global>",
        "wp edit waypoint <dimension> <list identifier> <waypoint identifier> set color <color>",
        "wp remove <dimension> <list identifier> <waypoint identifier>",
        "wp restore <token>",
    )
    nodes = []
    for path in paths:
        matches = [node for node in tree.get("nodes", []) if node.get("path") == path and node.get("executable")]
        if len(matches) != 1:
            raise ValueError("minimal storage command leaf missing or ambiguous: " + path)
        nodes.append(matches[0])
    return mutation_cases({**tree, "nodes": nodes})


def check_exact_state(name, expected, actual):
    from minimal_functional import NativeAssertion
    from models import Decision, Status
    if canonical_saved(expected) != canonical_saved(actual):
        raise NativeAssertion(name, Decision(Status.FAIL, ("exact committed fields/revisions differ",)))
