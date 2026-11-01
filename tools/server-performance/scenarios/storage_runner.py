"""Prepared native storage and command cases with separate behavior and timing evidence."""
from dataclasses import replace
from copy import deepcopy
from pathlib import Path
import json
import shutil
import uuid

from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready
from budgets import evaluate
from contracts import atomic_write, read_document, load_runtimes, load_release, load_result
from controller import Attempt, owned_root, create_root
from models import CaseResult, Decision, Status
from preparation import verify_evidence
from processes import stop_owned
from profiles import start_jfr, stop_jfr
from reporting import combine_decisions, exit_code
from server_health import native_log_health
from scenarios.command_workload import collect_commands, summarize_window, WorkloadIncomplete
from scenarios.storage_commands import make_dataset, canonical_saved, canonical_snapshot
from scenarios.command_behavior import semantic_fixture, exercise_semantics, exercise_reads, exercise_boundaries, seed_mutations, exercise_mutations, snapshot, ACK_RESULT_TIMEOUT, execute, prepare_command_operator, prepare_remaining_operators
from scenarios.inventory import retain_partial
from worlds import install_server, clone_baseline, boot, validate_fixture


def run_case(case, root):
    partial = Path(root) / "partials" / (str(uuid.uuid4()) + ".json")
    try:
        return execute_case(case, Path(root), partial)
    except KeyboardInterrupt:
        if partial.exists():
            atomic_write(Path(root) / "results" / ("interrupted-"+partial.name), retain_partial(partial, case, KeyboardInterrupt("interrupted native storage case")))
        raise
    except WorkloadIncomplete as error:
        return incomplete_result(partial,case,error)
    except Exception as error:
        return retain_partial(partial, case, error)


def incomplete_result(partial,case,error):
    previous = load_result(partial) if Path(partial).exists() else CaseResult(case,Decision(Status.INCONCLUSIVE))
    return replace(previous,decision=combine_decisions((previous.decision,Decision(Status.INCONCLUSIVE,(str(error),)))))


def record_health(log, directory, assertions, outcomes, key):
    health = native_log_health(log)
    outcomes.append(health)
    assertions[key] = health.status == Status.PASS
    atomic_write(Path(directory) / "server-health.json", {"schemaVersion": 1, "decision": health, "log": str(log)})
    return health


def execute_case(case, root, partial):
    supported = {"storage": {"add-edit-remove-restore", "revision-conflict", "dimensions", "concurrent", "save-reload-restart", "skewed-list"},
                 "commands": {"leaves-suggestions", "list-layout-filter-sort-page", "console-execute-as", "permissions", "local-tp", "config-language-reload", "action-log"}}
    if case.variant not in supported.get(case.feature, set()):
        return CaseResult(case, Decision(Status.BLOCKED, ("native per-leaf behavior adapter not yet implemented",)))
    if case.mode != "audit" or case.runtime != "paper-1.21.11":
        return CaseResult(case, Decision(Status.BLOCKED, ("storage native adapter is verified only for original Paper 1.21.11",)))
    runtime = next(value for value in load_runtimes(root / "runtime-lock.json") if value.id == case.runtime)
    settings = read_document(root / "prepared.json")
    verify_evidence(root, settings)
    prepared = Path(settings["worlds"][f"{case.fixture}-{case.tier}"])
    artifact = next(value for value in load_release(root / "release.json") if value.name == runtime.artifact)
    release = root / "artifacts" / artifact.name
    verify_artifact(artifact, release)
    if not runtime.bot.get("path") or sha256(runtime.bot["path"]) != runtime.bot.get("sha256"):
        raise ValueError("locked native bot bytes changed or missing")
    dataset = make_dataset(case.tier, skewed=case.variant == "skewed-list")
    if case.feature == "commands":
        dataset["minecraft:overworld"].append(semantic_fixture())
    repetitions, outcomes, evidence = [], [], []
    assertions = {}
    def result():
        current = CaseResult(case, Decision(Status.INCONCLUSIVE), owned_root(root)["runId"],
            artifact={"name":artifact.name,"sha256":artifact.sha256,"revision":artifact.revision,"provenance":artifact.provenance},
            identities={"runtime":runtime.id,"serverSha256":runtime.server_sha256,"javaSha256":runtime.java_sha256,
                        "probeSha256":runtime.probe["sha256"],"botSha256":runtime.bot["sha256"],
                        "fixtureSha256":read_document(prepared/"baseline.json")["recipeSha256"]},
            observations={"repetitions":list(repetitions),"profiledDiagnostic":settings.get("profile",False),
                          "timingGroup":"tier-sized list scans, edit/save, local TP, config/language reload and console execute-as; callback families reported separately; per-leaf behavior retained",
                          "timingSources":["common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java", "common/src/main/java/_959/server_waypoint/core/WaypointFileManager.java", "common/src/main/java/_959/server_waypoint/core/logging/PlayerActionLog.java"],
                          "actors":"operator; permission-denial and non-operator admission are separate requirements"},
            assertions=dict(assertions), paths={"attempts":list(evidence)}, cleanup="see owned attempt records")
        return replace(current, decision=combine_decisions((*outcomes,evaluate(current,root/"budgets.json"))))
    for index in range(case.repeats):
        with Attempt(root,case.id+f":repeat-{index+1}") as attempt:
            evidence.append(str(attempt.path))
            atomic_write(partial,result())
            server = create_root(attempt.directory/"server")
            port = install_server(runtime,server,runtime.probe["path"],release)
            clone_baseline(prepared/"frozen",server)
            shutil.copytree(prepared/"fixture",server/"fixture")
            directory = server/"plugins/ServerWaypoint/waypoints"
            directory.mkdir(parents=True)
            for dimension,lists in dataset.items():
                (directory/(dimension.replace(":","$")+".json")).write_text(json.dumps(lists,indent=4)+"\n")
            atomic_write(attempt.directory/"dataset.json", {"schemaVersion":1,"dimensions":dataset})
            attempt.transition("STARTING")
            process,probe = boot(runtime,server,False,artifact.sha256,attempt.record.attempt_id)
            driver = None
            try:
                execute(probe, "difficulty peaceful")
                probe.send("fixture.verify",{"directory":str(server/"fixture")})
                if validate_fixture(probe.wait("fixture.verified")["data"]).status != Status.PASS:
                    raise ValueError("prepared native fixture verification failed")
                probe.send("commands.tree")
                tree = probe.wait("commands.tree")["data"]
                atomic_write(attempt.directory/"command-tree.json", {"schemaVersion":1,**tree})
                loaded_dataset = deepcopy(dataset)
                mutation_fixtures = None
                if case.variant in ("add-edit-remove-restore", "dimensions", "save-reload-restart", "leaves-suggestions"):
                    mutation_fixtures,loaded_dataset = seed_mutations(loaded_dataset,tree)
                    bootstrap_log = process.log
                    stop_owned(process,30)
                    process = None
                    outcomes.append(native_log_health(bootstrap_log))
                    atomic_write(partial,result())
                    for dimension, lists in loaded_dataset.items():
                        (directory/(dimension.replace(":","$")+".json")).write_text(json.dumps(lists,indent=4)+"\n")
                    process,probe = boot(runtime,server,False,artifact.sha256)
                    probe.send("commands.tree")
                    if probe.wait("commands.tree")["data"] != tree:
                        raise ValueError("native command tree changed during fixture startup")
                bot_root = create_root(attempt.directory/"bots")
                if sha256(runtime.bot["path"]) != runtime.bot["sha256"]:
                    raise ValueError("native bot bytes changed before repetition")
                driver = BotDriver(runtime.bot["path"],bot_root,attempt.record.run_id,attempt.record.attempt_id)
                mode = "compatible" if case.variant in ("revision-conflict", "leaves-suggestions") else "vanilla"
                driver.send("start",{"address":f"127.0.0.1:{port}","count":case.clients,"mode":mode})
                ready = driver.wait("ready",150)
                probe.send("players.snapshot")
                native = probe.wait("players.snapshot")["data"]
                if validate_ready(ready,native["players"],case.clients,mode).status != Status.PASS:
                    raise ValueError("native storage actor count/UUID mismatch")
                if canonical_snapshot(native["waypointLists"]) != canonical_snapshot([
                    {"dimension":dimension,"name":value["list_name"],"revision":value["n"],"waypoints":[point["name"] for point in value["waypoints"]]}
                    for dimension,lists in loaded_dataset.items() for value in lists]):
                    raise ValueError("native dataset names/revisions differ from disk fixture")
                actors = [value["name"] for value in ready["bots"]]
                prepare_command_operator(probe, driver, actors[0])
                expected = deepcopy(loaded_dataset)
                if case.feature == "commands":
                    expected = exercise_semantics(probe,server,driver,actors[0],expected,attempt.directory/"command-behavior.json")
                    behavior_failures = read_document(attempt.directory/"command-behavior.json")["failures"]
                    assertions[f"repeat-{index+1}-permissions-feedback-layout-tp-log"] = not behavior_failures
                    if behavior_failures:
                        outcomes.append(Decision(Status.FAIL,tuple(behavior_failures)))
                    atomic_write(partial,result())
                if case.variant == "leaves-suggestions":
                    exercise_reads(probe,server,tree,attempt.directory/"leaf-behavior.json",actors[0])
                    exercise_boundaries(probe,server,tree,attempt.directory/"boundary-behavior.json",actors[0],driver)
                    assertions[f"repeat-{index+1}-individual-read-leaves-and-providers"] = True
                if mutation_fixtures is not None:
                    expected = exercise_mutations(probe,server,expected,mutation_fixtures,attempt.directory/"mutation-behavior.json")
                    assertions[f"repeat-{index+1}-individual-mutation-leaves-properties-revisions"] = True
                if case.variant == "leaves-suggestions":
                    from scenarios.command_behavior import local_leaf_report
                    local_decision, dispatch_decision = local_leaf_report(tree,attempt.directory/"leaf-behavior.json",attempt.directory/"mutation-behavior.json",attempt.directory/"command-behavior.json",attempt.directory/"local-leaf-coverage.json",attempt.directory/"boundary-behavior.json")
                    assertions[f"repeat-{index+1}-all-command-leaves-have-feedback"] = local_decision.status == dispatch_decision.status == Status.PASS
                    atomic_write(partial,result())
                if case.variant in ("revision-conflict", "leaves-suggestions"):
                    from scenarios.command_behavior import exercise_stale_edit
                    exercise_stale_edit(probe,driver,actors[0],attempt.directory/"stale-edit.json")
                    assertions[f"repeat-{index+1}-stale-wire-edit-rejected"] = True
                if case.variant in ("save-reload-restart", "leaves-suggestions"):
                    probe.send("storage.reload")
                    reloaded = probe.wait("storage.reloaded")["data"]
                    correct = canonical_saved(reloaded["dimensions"]) == canonical_saved(expected)
                    assertions[f"repeat-{index+1}-native-storage-reload-exact"] = correct
                    atomic_write(attempt.directory/"reloaded.json", {"schemaVersion":1,**reloaded,"correct":correct})
                    if not correct:
                        outcomes.append(Decision(Status.FAIL,("public native storage reload changed committed contents",)))
                    atomic_write(partial,result())
                prepare_remaining_operators(probe, driver, actors)
                prime = []
                for actor in actors:
                    driver.send("action",{"actionId":f"prime-{actor}","bot":actor,"operation":"command","command":"/wp help","acknowledged":True})
                for _ in actors:
                    prime.append(driver.wait("action_result", ACK_RESULT_TIMEOUT))
                atomic_write(attempt.directory/"priming.json", {"schemaVersion":1,"actions":prime,"native":native})
                attempt.transition("READY")
                profiled = settings.get("profile",False)
                profile_path = attempt.directory/"server.jfr"
                if profiled:
                    decision = start_jfr(process,runtime.java,profile_path)
                    outcomes.append(decision)
                    if decision.status != Status.PASS:
                        raise ValueError(decision.reasons)
                attempt.transition("WARMUP")
                suggestion = None
                reads = None
                if case.feature == "commands" or case.variant == "skewed-list":
                    provider = next(node for node in tree["nodes"] if node["path"] == "wp details waypoint <dimension> <list identifier> <waypoint identifier>")
                    suggestion = {"input":"wp details waypoint minecraft:overworld perf_00 wp_0000", "providerId":provider["providerId"]}
                if case.feature == "commands":
                    reads = ("/wp list sort name limit 2 view flat search perf",
                             "/wp list minecraft:overworld perf_00 search perf sort name limit 2",
                             "/wp list minecraft:overworld perf_00 sort name page 2 limit 2 view flat",
                             "/wp details waypoint minecraft:overworld perf_00 wp_00000", "/wp help list")
                mixed = case.feature == "commands"
                warmup = collect_commands(probe,driver,case.tier,actors,case.warmup_seconds,"WARMUP",f"{index}-warm",evidence_path=attempt.directory/"warmup.json",read_commands=reads,suggestion=suggestion,mutation_mode="mixed" if mixed else "color",console_reads=mixed)
                warm_mutations = sum(action["kind"] == "mutate" for action in warmup["actions"])
                attempt.transition("MEASURING")
                measured = collect_commands(probe,driver,case.tier,actors,case.measurement_seconds,"MEASURING",f"{index}-measure",warm_mutations,evidence_path=attempt.directory/"measurement.json",read_commands=reads,suggestion=suggestion,mutation_mode="mixed" if mixed else "color",console_reads=mixed)
                repetitions.append(summarize_window(measured,warmup["seconds"]))
                assertions[f"repeat-{index+1}-native-actors-and-contiguous-ticks"] = True
                assertions[f"repeat-{index+1}-acknowledged-completed-actions"] = True
                atomic_write(partial,result())
                attempt.transition("QUIESCING")
                if profiled:
                    outcomes.append(stop_jfr(process,runtime.java,profile_path))
                driver.close()
                driver = None
                probe.send("commands.resources")
                resources = probe.wait("commands.resources")["data"]
                clean = all(type(value) is int and value == 0 for value in resources.values())
                assertions[f"repeat-{index+1}-command-resources-released"] = clean
                atomic_write(attempt.directory/"command-resources.json", {"schemaVersion":1,**resources})
                if not clean:
                    outcomes.append(Decision(Status.FAIL,("pending command markers/permissions/callbacks remain after disconnect",)))
                probe.send("players.snapshot")
                final = probe.wait("players.snapshot")["data"]
                assertions[f"repeat-{index+1}-disconnected"] = not final["players"]
                if final["players"]:
                    outcomes.append(Decision(Status.FAIL,("storage actors remain after disconnect",)))
                color_writes = [action for window in (warmup,measured) for action in window["actions"] if " set color " in action["command"]]
                for value in expected["minecraft:overworld"]:
                    if value["list_name"] == "perf_00":
                        value["n"] += len(color_writes)
                        if color_writes:
                            next(point for point in value["waypoints"] if point["name"] == "wp_00000")["color"] = "#" + color_writes[-1]["command"].split()[-1]
            finally:
                try:
                    if driver:
                        driver.abort()
                finally:
                    if process is not None:
                        try:
                            stop_owned(process,30)
                        finally:
                            record_health(process.log, attempt.directory, assertions, outcomes, f"repeat-{index+1}-native-log-healthy")
                            atomic_write(partial,result())
            actual = {path.stem.replace("$",":"):json.loads(path.read_text()) for path in directory.glob("*.json")}
            correct = canonical_saved(expected) == canonical_saved(actual)
            assertions[f"repeat-{index+1}-saved-exactly"] = correct
            outcomes.append(Decision(Status.PASS if correct else Status.FAIL,() if correct else ("exact saved storage contents differ",)))
            atomic_write(partial,result())
            atomic_write(attempt.directory/"saved.json",{"schemaVersion":1,"expected":canonical_saved(expected),"actual":canonical_saved(actual),"correct":correct})
            process,probe = boot(runtime,server,False,artifact.sha256)
            try:
                probe.send("players.snapshot")
                reopened = probe.wait("players.snapshot")["data"]
                exact_reopened = snapshot(probe)
                correct = canonical_saved(exact_reopened) == canonical_saved(expected)
                assertions[f"repeat-{index+1}-reopened-exactly"] = correct
                outcomes.append(Decision(Status.PASS if correct else Status.FAIL,() if correct else ("reopened storage names/revisions differ",)))
                atomic_write(attempt.directory/"reopened.json",{"schemaVersion":1,**reopened,"dimensions":exact_reopened,"correct":correct})
            finally:
                try:
                    stop_owned(process,30)
                finally:
                    outcomes.append(native_log_health(process.log))
                    atomic_write(partial,result())
            if sha256(runtime.bot["path"]) != runtime.bot["sha256"]:
                raise ValueError("native bot bytes changed during repetition")
            verify_artifact(artifact,release)
            verify_artifact(artifact,server/"plugins/audited.jar")
            attempt.transition("STOPPED")
            attempt.finish(exit_code(result().decision),"native measurement and save/reopen retained")
            atomic_write(partial,result())
    return replace(result(),cleanup="all owned bot/server groups stopped")
