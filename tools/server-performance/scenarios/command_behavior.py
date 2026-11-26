"""Native Task 10 behavior oracles, kept outside the timed command windows."""
from copy import deepcopy
import json
import re
import sys
import time
import uuid

from bot_driver import effects_snapshot
from contracts import atomic_write
from models import Status
from scenarios.command_leaves import console_feedback, read_cases, suggestion_cases, verify_suggestions, boundary_cases
from scenarios.mutation_leaves import mutation_cases, resolve_generated, async_reload_feedback
from scenarios.storage_commands import canonical_saved, command_coverage, match_receipt, verify_rows, leaf_ownership, restore_rejection


def semantic_fixture():
    points = []
    for name, x, color, keywords, visible in (
            ("charlie", 12, "#FF0000", ["needle"], True),
            ("alpha", 4, "#00FF00", [], True),
            ("bravo", -4, "#0000FF", ["needle"], True),
            ("delta", 20, "#808080", [], False)):
        points.append({"name":name,"initials":"P","pos":[x,-61,0],"color":color,
                       "yaw":30,"global":visible,"keywords":keywords,"description":"semantic " + name})
    return {"list_name":"semantic","n":7,"waypoints":points}


def permission_matrix():
    return [{"branch":branch,"operator":operator,"override":override,"allowed":allowed}
            for branch in ("add","edit","remove","restore","tp","reload","navigate","upload","upload.delete","remote.list","remote.tp")
            for operator, override, allowed in ((False,None,False),(True,None,True),(False,True,True),(True,False,False))]


def snapshot(probe):
    probe.send("storage.snapshot")
    return probe.wait("storage.snapshot")["data"]["dimensions"]


def players(probe):
    probe.send("players.snapshot")
    return probe.wait("players.snapshot")["data"]["players"]


def execute(probe, command, actor="console"):
    probe.send("commands.execute", {"commands":[command] if command else [],"actor":actor})
    return probe.wait("commands.executed")["data"]


ACK_RESULT_TIMEOUT = 120


def wait_for_recovered_dispatch(probe, driver=None, actor=None, timeout=180, limit_ns=50_000_000, required=100):
    """Wait out join ticks, then retry until one acknowledged player command completes.

    Native ACK requires a prior `commands.tree` snapshot so Brigadier executors
    are instrumented. Preprocess receipts alone are not completion proof.
    """
    probe.send("scenario.start", {"phase":"SETTLE","eventsPerTick":0,"variant":"plain-0"})
    probe.wait("scenario.started")
    deadline = time.monotonic() + timeout
    recovered = 0
    sample = None
    try:
        while time.monotonic() < deadline:
            for event in probe.poll():
                if event.get("type") != "sample":
                    continue
                duration = event.get("data", {}).get("durationNs")
                if type(duration) is int and 0 <= duration < limit_ns:
                    recovered += 1
                    if recovered >= required:
                        sample = event["data"]
                        break
                else:
                    recovered = 0
            if sample is not None:
                break
            time.sleep(0.05)
        else:
            raise TimeoutError("native command dispatch did not recover after join")
    finally:
        probe.send("scenario.stop")
        probe.wait("scenario.stopped")
    if driver is None or actor is None:
        return sample
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        action_id = "settle-" + uuid.uuid4().hex
        driver.send("action", {"actionId": action_id, "bot": actor, "operation": "command",
                               "command": "/wp help", "acknowledged": True})
        action = driver.wait("action_result", ACK_RESULT_TIMEOUT)
        ack = action.get("nativeAck") or {}
        if ack.get("serverObserved"):
            return {**sample, "nativeAck": ack}
        if action.get("error") != "native command acknowledgement deadline":
            raise ValueError("settle acknowledgement failed: " + str(action.get("error")))
    raise TimeoutError("native command acknowledgement did not recover after join")


def prepare_command_operator(probe, driver, actor, timeout=180, required=100):
    execute(probe, "op " + actor)
    return wait_for_recovered_dispatch(probe, driver, actor, timeout=timeout, required=required)


def prepare_remaining_operators(probe, driver, actors, timeout=180, required=100):
    for actor in actors[1:]:
        prepare_command_operator(probe, driver, actor, timeout=timeout, required=required)


class NativeStateMismatch(ValueError):
    """A completed native mutation disagrees with independently expected state."""


def eventually(check, message, timeout=15, timeout_error=ValueError):
    deadline = time.monotonic() + timeout
    while True:
        value = check()
        if value:
            return value
        if time.monotonic() >= deadline:
            raise timeout_error(message)
        time.sleep(0.05)


def feedback(probe, server):
    probe.send("commands.feedback-barrier")
    probe.wait("commands.feedback.completed")
    # The pipe and file appenders are independent. Wait for this exact barrier.
    sequence = probe.stream.sequence
    def drained():
        transcript = (server / "logs/latest.log").read_text()
        for line in reversed(transcript.splitlines()):
            if "SW_PERF " not in line:
                continue
            event = json.loads(line.split("SW_PERF ",1)[1])
            if event.get("runId") == probe.stream.run_id and event.get("attemptId") == probe.stream.attempt_id and event.get("sequence",0) >= sequence:
                return transcript
        return None
    transcript = eventually(drained,"native feedback file appender did not drain")
    return console_feedback(transcript, probe.stream.run_id, probe.stream.attempt_id)


def exercise_reads(probe, server, tree, output, actor):
    cases = read_cases(tree)
    before = canonical_saved(snapshot(probe))
    callbacks = []
    for start in range(0,len(cases),100):
        batch = cases[start:start+100]
        probe.send("commands.execute", {"commands":[value["command"] for value in batch]})
        report = probe.wait("commands.executed")["data"]
        callbacks.extend(match_receipt(report, case["command"], "CONSOLE", case["path"]) for case in batch)
    observed = feedback(probe,server)
    decision = command_coverage([case["path"] for case in cases],observed,())
    if decision.status != Status.PASS or before != canonical_saved(snapshot(probe)):
        raise ValueError("read leaf feedback or unchanged-state evidence missing")
    suggestions = suggestion_cases(tree,include_remote=True)
    reports = []
    for case in suggestions:
        probe.send("commands.suggest", {"input":case["input"],"actor":actor})
        reports.append(probe.wait("commands.suggested")["data"])
    suggestion_decision = verify_suggestions(suggestions,reports)
    atomic_write(output,{"schemaVersion":1,"cases":cases,"callbacks":callbacks,"feedback":observed,
                         "decision":decision,"suggestionCases":suggestions,"suggestionReports":reports,
                         "suggestionDecision":suggestion_decision,"ownership":leaf_ownership(tree)})
    if suggestion_decision.status != Status.PASS:
        raise ValueError("custom provider receipt missing")
    return len(cases), len(suggestions)


def exercise_boundaries(probe, server, tree, output, actor, driver):
    """Command-layer outcomes only; successful feature effects retain their own requirements."""
    cases = boundary_cases(tree)
    console_cases = [case for case in cases if not case["path"].startswith("wp remote tp ")]
    console_paths = {case["path"] for case in console_cases}
    before = canonical_saved(snapshot(probe))
    steps = []
    observed = []
    try:
        for start in range(0,len(console_cases),100):
            batch = console_cases[start:start+100]
            probe.send("commands.execute",{"commands":[case["command"] for case in batch]})
            report = probe.wait("commands.executed")["data"]
            for case in batch:
                steps.append({**case,"callback":match_receipt(report,case["command"],"CONSOLE",case["path"])})
        observed = feedback(probe,server)
        for case in cases:
            if case["path"] in console_paths:
                continue
            driver.send("effects")
            prior = effects_snapshot(driver.wait("packet_summary"))[actor]["sequence"]
            driver.wait("rate_sample")
            action_id = "boundary-" + uuid.uuid4().hex
            driver.send("action",{"actionId":action_id,"bot":actor,"operation":"command","command":"/"+case["command"],"acknowledged":True})
            action = driver.wait("action_result", ACK_RESULT_TIMEOUT)
            if action.get("actionId") != action_id or not action.get("nativeAck",{}).get("serverObserved"):
                raise ValueError("native remote boundary completion missing")
            report = execute(probe,"")
            callback = match_receipt(report,case["command"],actor,case["path"])
            def messages():
                driver.send("effects")
                state = effects_snapshot(driver.wait("packet_summary"))[actor]
                driver.wait("rate_sample")
                return [value["text"] for value in state["chat"] if value["sequence"] > prior] or None
            delivered = eventually(messages,"unpaired remote TP boundary client feedback missing")
            observed.append({**callback,"feedback":delivered,"feedbackObserved":True})
            steps.append({**case,"callback":callback,"action":action})
        decision = command_coverage([case["path"] for case in cases],observed,())
        if decision.status != Status.PASS or canonical_saved(snapshot(probe)) != before:
            raise ValueError("feature-boundary callback/feedback/state evidence missing")
        for case in cases:
            event = next(value for value in observed if value["path"] == case["path"] and value["input"] == case["command"])
            text = "\n".join(event["feedback"]).lower()
            if case["behavior"] == "console-player-required" and not ("player" in text or "in-game" in text):
                raise ValueError("console-only feature boundary did not report a player requirement: " + case["path"])
            if case["behavior"] == "console-no-client" and not ("client" in text and "deliver" in text):
                raise ValueError("console download did not report its undeliverable client outcome")
            if case["behavior"] == "unpaired-remote" and not ("remote" in text or "catalog" in text or "server" in text):
                raise ValueError("unpaired remote command lacks its native unavailable outcome")
        return len(cases)
    finally:
        atomic_write(output,{"schemaVersion":1,"cases":cases,"steps":steps,"observations":observed,
                             "scope":"per-leaf console/unpaired command boundaries; Task 11/12/15 successful effects remain required"})


def seed_mutations(dataset, tree):
    """Build exact startup fixtures; wp reload intentionally reloads config/languages only."""
    cases = mutation_cases(tree)
    expected = deepcopy(dataset)
    expected["minecraft:overworld"].extend(deepcopy(case["before"]) for case in cases if case["before"] is not None)
    return cases, expected


def exercise_mutations(probe, server, expected, cases, output):
    steps = []
    observations = []
    observed = []
    try:
        for case in cases:
            command = case["command"]
            restore_evidence = None
            if case["removeBeforeRestore"]:
                probe.send("commands.restore-tokens")
                prior = set(probe.wait("commands.restore-tokens")["data"]["tokens"])
                execute(probe,f"wp remove minecraft:overworld {case['owner']} origin")
                probe.send("commands.restore-tokens")
                tokens = set(probe.wait("commands.restore-tokens")["data"]["tokens"]) - prior
                if len(tokens) != 1:
                    raise ValueError("restore fixture requires one actual new token")
                restore_token = tokens.pop()
                command = command.replace("{restoreToken}",restore_token)
            token = None
            if case["path"] == "wp reload":
                settings_path = server / "plugins/ServerWaypoint/config.json"
                settings = json.loads(settings_path.read_text())
                settings["defaultPageLimit"] = 1
                settings_path.write_text(json.dumps(settings,indent=4)+"\n")
                token = "PERF_RELOAD_" + uuid.uuid4().hex
                language = server / "plugins/ServerWaypoint/lang/en_us.json"
                language.write_text(json.dumps({"wp.result.reloaded":token})+"\n")
            report = execute(probe,command)
            callback = match_receipt(report,command,"CONSOLE",case["path"])
            def matches():
                actual = snapshot(probe)
                observed = next((v for v in actual["minecraft:overworld"] if case["after"] and v["list_name"] == case["after"]["list_name"]),None)
                try:
                    after = resolve_generated(case,observed)
                except ValueError:
                    return None
                candidate = deepcopy(expected)
                candidate["minecraft:overworld"] = [v for v in candidate["minecraft:overworld"] if v["list_name"] != case["owner"]]
                if after is not None:
                    candidate["minecraft:overworld"].append(after)
                return (candidate,after) if canonical_saved(actual) == canonical_saved(candidate) else None
            candidate, after = eventually(matches,"mutation fields/revisions differ: " + case["path"], timeout_error=NativeStateMismatch)
            expected = candidate
            if token:
                observation = eventually(lambda: async_reload_feedback((server/"logs/latest.log").read_text(),probe.stream.run_id,probe.stream.attempt_id,callback,token),"actual language reload response missing")
                observations.append(observation)
            if case["removeBeforeRestore"]:
                probe.send("commands.restore-tokens")
                remaining = probe.wait("commands.restore-tokens")["data"]["tokens"]
                replay = match_receipt(execute(probe,command),command,"CONSOLE",case["path"])
                replay_feedback = next(value for value in feedback(probe,server) if value["commandObservationId"] == replay["commandObservationId"])
                replay_decision = restore_rejection(restore_token,remaining,replay_feedback)
                restore_evidence = {"token":restore_token,"remainingAfterSuccess":remaining,"replay":replay_feedback,"decision":replay_decision}
                if replay_decision.status != Status.PASS:
                    raise ValueError(str(replay_decision.reasons))
                if canonical_saved(snapshot(probe)) != canonical_saved(expected):
                    raise NativeStateMismatch("restore token replay mutated exact state")
            steps.append({"path":case["path"],"command":command,"callback":callback,"after":after,"restoreEvidence":restore_evidence})
        observed = feedback(probe,server)
        overrides = {value["commandObservationId"]:value for value in observations}
        observed = [overrides.get(value["commandObservationId"],value) for value in observed]
        decision = command_coverage([case["path"] for case in cases],observed,())
        if decision.status != Status.PASS:
            raise ValueError("mutation feedback missing: " + str(decision.reasons))
        return expected
    finally:
        atomic_write(output,{"schemaVersion":1,"steps":steps,"reloadObservations":observations,"observations":observed})


def exercise_semantics(probe, server, driver, actor, expected, output):
    """Literal row oracles are derived from the four-point fixture, not production sorting."""
    rows = []
    permission_reports = []
    player_reports = []
    failures = []
    settings_path = server / "plugins/ServerWaypoint/config.json"
    original = json.loads(settings_path.read_text())
    settings = deepcopy(original)
    settings["CommandPermission"].update({key:2 for key in settings["CommandPermission"]})
    settings_path.write_text(json.dumps(settings,indent=4)+"\n")
    reload_token = "PERF_RELOAD_" + uuid.uuid4().hex
    language = server / "plugins/ServerWaypoint/lang/en_us.json"
    language.write_text(json.dumps({"wp.result.reloaded":reload_token})+"\n")
    call = match_receipt(execute(probe,"wp reload"),"wp reload","CONSOLE","wp reload")
    eventually(lambda: async_reload_feedback((server/"logs/latest.log").read_text(),probe.stream.run_id,probe.stream.attempt_id,call,reload_token),"configured permissions reload did not complete")
    settings_applied = deepcopy(settings)

    def permissions(operator, values):
        probe.send("commands.permissions",{"actor":actor,"operator":operator,"permissions":values})
        result = probe.wait("commands.permissions")["data"]
        if result.get("actor") != actor or result.get("operator") is not operator or result.get("explicit") != values or result.get("origin") != "native-command-requirements":
            raise ValueError("effective native permission configuration differs")
        return result

    def client_state():
        driver.send("effects")
        value = driver.wait("packet_summary")
        rate = driver.wait("rate_sample")
        if rate.get("pending") or rate.get("failed"):
            raise ValueError("player behavior barrier incomplete")
        return effects_snapshot(value)[actor]

    def player_command(command, wanted=None, denied=False):
        # Vanilla accumulates command spam even while OP. Let its counter
        # drain between these untimed non-OP permission/feedback requests.
        time.sleep(1.1)
        before = client_state()
        native_before = next(value for value in players(probe) if value["name"] == actor)["commandEvents"]
        action_id = "behavior-" + uuid.uuid4().hex
        driver.send("action",{"actionId":action_id,"bot":actor,"operation":"command","command":"/"+command,"acknowledged":not denied})
        action = driver.wait("action_result", ACK_RESULT_TIMEOUT)
        if action.get("actionId") != action_id or action.get("error") or action.get("success") is False:
            raise ValueError("player action result mismatched or failed")
        eventually(lambda: next(value for value in players(probe) if value["name"] == actor)["commandEvents"] == native_before + 1,"player command lacks native preprocess receipt")
        report = execute(probe,"")
        def response():
            state = client_state()
            messages = [value["text"] for value in state["chat"] if value["sequence"] > before["sequence"]]
            return messages if messages and (wanted is None or any(wanted in value for value in messages)) else None
        try:
            messages = eventually(response,"player command has no correlated delivered feedback: " + command)
        except ValueError as error:
            failures.append(str(error))
            messages = []
        player_reports.append({"command":command,"actor":actor,"action":action,"dispatch":report,"feedback":messages})
        return report, "\n".join(messages)

    try:
        permissions(True,{})
        player_command("wp tp")
        # All options have their own behavioral cases as well as exhaustive leaf receipts.
        for suffix, wanted in (
                ("sort name limit 4",("alpha","bravo","charlie","delta")),
                ("sort name order descending limit 4",("delta","charlie","bravo","alpha")),
                ("sort color limit 4",("charlie","alpha","bravo","delta")),
                ("sort name page 2 limit 2",("charlie","delta")),
                ("search needle sort name limit 4",("bravo","charlie")),
                ("search absent limit 4",()),
                ("sort name limit 4 view flat",("alpha","bravo","charlie","delta")),
                ("sort name limit 4 view tree",("alpha","bravo","charlie","delta")),
                ("sort name limit 4 view lists",("alpha","bravo","charlie","delta"))):
            command = "wp list minecraft:overworld semantic " + suffix
            report = execute(probe,command)
            callback = next((value for value in report["callbacks"] if value["input"] == command),None)
            if callback is None or callback.get("exception"):
                raise ValueError("semantic list command callback missing")
            observation = next(value for value in reversed(feedback(probe,server)) if value.get("commandObservationId") == callback["commandObservationId"])
            decision = verify_rows("\n".join(observation["feedback"]),wanted)
            rows.append({"command":command,"wanted":wanted,"observation":observation,"decision":decision})
            if decision.status != Status.PASS:
                raise ValueError(str(decision.reasons))
        _, details = player_command("wp details waypoint minecraft:overworld semantic alpha","Identifier: alpha")
        if not all(value in details for value in ("4, -61, 0","#00FF00","semantic alpha")):
            failures.append("player details fields differ from fixture")
        player_command("wp help list","list")
        execute(probe,f"tp {actor} 0 -61 0")
        _, distance = player_command("wp list minecraft:overworld semantic sort distance limit 4")
        distance_decision = verify_rows(distance,("alpha","bravo","charlie","delta"))
        if distance_decision.status != Status.PASS:
            raise ValueError(str(distance_decision.reasons))
        rows.append({"command":"player distance at 0,-61,0","decision":distance_decision,"feedback":distance})
        # Every gated local root: default level fallback, OP fallback, explicit
        # non-OP grant, and explicit OP denial. Feature effects stay with owners.
        commands = {"add":"wp add minecraft:overworld denied_fixture",
                    "edit":"wp edit waypoint minecraft:overworld semantic alpha set color 010203",
                    "remove":"wp remove minecraft:overworld semantic alpha",
                    "tp":"wp tp minecraft:overworld semantic alpha", "reload":"wp reload",
                    "navigate":"wp navigate minecraft:overworld semantic alpha",
                    "upload":"wp upload xaero", "upload.delete":"wp upload xaero force local delete",
                    "restore":"wp restore missing-token", "remote.list":"wp remote list",
                    "remote.tp":"wp remote tp missing-server minecraft:overworld missing-list missing-waypoint"}
        keys = {branch:"server_waypoint.command." + ("add" if branch == "restore" else branch) for branch in commands}
        for case in permission_matrix():
            branch = case["branch"]
            values = {} if case["override"] is None else {keys[branch]:case["override"]}
            if branch == "upload.delete":
                values["server_waypoint.command.upload"] = True
            observed = permissions(case["operator"],values)
            if observed["branches"][branch] is not case["allowed"]:
                raise ValueError("native requirement differs from configured permission: " + str(case))
            record = {**case,"effective":observed}
            if not case["allowed"]:
                before = canonical_saved(snapshot(probe))
                location = players(probe)
                report, messages = player_command(commands[branch],denied=True)
                after = canonical_saved(snapshot(probe))
                if after != before or report["callbacks"]:
                    raise ValueError("denied command executed or changed committed contents")
                if branch == "tp" and [(p["world"],p["x"],p["y"],p["z"]) for p in players(probe)] != [(p["world"],p["x"],p["y"],p["z"]) for p in location]:
                    raise ValueError("denied teleport moved player")
                record.update(before=before,after=after,feedback=messages,dispatch=report)
            permission_reports.append(record)
        permissions(False,{keys["edit"]:True})
        command = "wp edit waypoint minecraft:overworld semantic alpha set color 010203"
        report, _ = player_command(command)
        match_receipt(report,command,actor,"wp edit waypoint <dimension> <list identifier> <waypoint identifier> set color <color>")
        expected = deepcopy(expected)
        semantic = next(value for value in expected["minecraft:overworld"] if value["list_name"] == "semantic")
        semantic["n"] += 1
        next(point for point in semantic["waypoints"] if point["name"] == "alpha")["color"] = "#010203"
        eventually(lambda: canonical_saved(snapshot(probe)) == canonical_saved(expected),"explicit granted edit did not commit exactly once")
        permissions(True,{})
        # Teleport through both player and execute-as source stacks; confirm the
        # native world/coordinates, real client feedback and action-log identities.
        for command, x in (("wp tp minecraft:overworld semantic alpha",4),
                           (f"execute as {actor} at {actor} run wp tp minecraft:overworld semantic charlie",12)):
            if command.startswith("execute"):
                before = client_state()
                report = execute(probe,command)
                try:
                    eventually(lambda: any("teleport" in value["text"].lower() and value["sequence"] > before["sequence"] for value in client_state()["chat"]),"execute-as player feedback missing")
                except ValueError as error:
                    failures.append(str(error))
            else:
                report, _ = player_command(command)
            eventually(lambda: any(p["name"] == actor and p["world"] == "minecraft:overworld" and abs(p["x"] - (x + 0.5)) < 0.01 and abs(p["z"] - 0.5) < 0.01 for p in players(probe)),"local teleport final native position differs")
            player_reports.append({"command":command,"nativeAfter":players(probe),"dispatch":report})
        log = (server/"logs/latest.log").read_text()
        action_lines = [line for line in log.splitlines() if "action=" in line and f"player={actor} " in line]
        if not any("action=tp " in line and "sender=CONSOLE " in line for line in action_lines) or not any("action=edit" in line or "action=waypoint_edit" in line for line in action_lines):
            raise ValueError("real action log lacks player edit/execute-as identities")
        if canonical_saved(snapshot(probe)) != canonical_saved(expected):
            raise ValueError("command semantics changed unexpected committed properties")
        return expected
    finally:
        # Restore operator admission before high-rate traffic. The configured
        # defaults/overlay are deliberately retained in this owned test server.
        active_error = sys.exc_info()[1]
        cleanup_error = None
        try:
            permissions(True,{})
        except Exception as error:
            cleanup_error = str(error)
        atomic_write(output,{"schemaVersion":1,"rows":rows,"permissions":permission_reports,
                             "playerCommands":player_reports,"configured":settings_applied,
                             "failures":failures,
                             "cleanupError":cleanup_error,
                             "scope":"Task 10 local behavior; feature effects remain independently covered"})
        if cleanup_error and active_error is None:
            raise RuntimeError("permission fixture cleanup failed: " + cleanup_error)


def local_leaf_report(tree, read_path, mutation_path, semantic_path, output, boundary_path):
    from contracts import read_document
    reads = read_document(read_path)
    mutations = read_document(mutation_path)
    semantics = read_document(semantic_path)
    boundaries = read_document(boundary_path)
    events = reads["feedback"] + mutations["observations"]
    for record in semantics["playerCommands"]:
        for callback in record.get("dispatch",{}).get("callbacks",[]):
            events.append({**callback,"feedbackObserved":bool(record.get("feedback")),"feedback":record.get("feedback",[])})
    ownership = leaf_ownership(tree)
    decision = command_coverage(ownership["task10"],events,())
    required = {path for paths in ownership.values() for path in paths}
    all_events = events + boundaries["observations"]
    all_decision = command_coverage(required,all_events,())
    observed_paths = {event["path"] for event in all_events if event.get("serverObserved") is True and event.get("feedbackObserved") is True}
    failed_feedback = {callback["path"] for record in semantics["playerCommands"] if not record.get("feedback")
                       for callback in record.get("dispatch",{}).get("callbacks",[])
                       if callback.get("serverObserved") is True and not callback.get("exception")}
    atomic_write(output,{"schemaVersion":1,"localDecision":decision,"ownership":ownership,
                         "commandDispatchDecision":all_decision,
                         "localExecutedPaths":sorted({event["path"] for event in events if event.get("serverObserved") is True and event.get("feedbackObserved") is True}),
                         "featureOwnedCoverage":"Task 11/12/15 evidence required independently; no full-release coverage claim"})
    if (required - observed_paths) - failed_feedback or ((decision.status != Status.PASS or all_decision.status != Status.PASS) and not semantics.get("failures")):
        raise ValueError("per-leaf behavior incomplete: " + str(decision.reasons + all_decision.reasons))
    return decision, all_decision


def exercise_stale_edit(probe, driver, actor, output):
    before = canonical_saved(snapshot(probe))
    patch = [{"operation":0,"value":None} for _ in range(10)]
    patch[4] = {"operation":1,"value":1122867}
    driver.send("action",{"actionId":"stale-edit-"+uuid.uuid4().hex,"bot":actor,"operation":"edit",
                         "edit":{"requestId":959,"dimension":"minecraft:overworld","list":"perf_00",
                                 "waypoint":"wp_00000","revision":6,"patch":patch}})
    driver.wait("action_result")
    def reply():
        driver.send("barrier")
        report = driver.wait("packet_summary")
        driver.wait("rate_sample")
        replies = [packet for peer in report["peers"] if peer["name"] == actor for packet in peer["packets"] if packet.get("messageTypeId") == 3]
        return bytes.fromhex(replies[-1]["bodyHex"]) if replies else None
    body = eventually(reply,"native stale edit reply missing")
    after = canonical_saved(snapshot(probe))
    correct = len(body) >= 13 and int.from_bytes(body[:8],"big") == 959 and body[8] == 1 and int.from_bytes(body[-4:],"big",signed=True) == 7 and before == after
    atomic_write(output,{"schemaVersion":1,"replyHex":body.hex(),"before":before,"after":after,"correct":correct})
    if not correct:
        raise ValueError("wire stale revision edit was accepted or changed committed state")
