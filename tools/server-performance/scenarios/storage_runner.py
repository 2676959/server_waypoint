"""Prepared native storage concurrency cases; other command behavior stays explicit."""
from dataclasses import replace
from copy import deepcopy
from pathlib import Path
import json
import shutil
import uuid

from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready
from budgets import evaluate
from contracts import atomic_write, read_document, load_runtimes, load_release
from controller import Attempt, owned_root, create_root
from models import CaseResult, Decision, Status
from preparation import verify_evidence
from processes import stop_owned
from profiles import start_jfr, stop_jfr
from reporting import combine_decisions, exit_code
from scenarios.command_workload import collect_commands, summarize_window
from scenarios.storage_commands import make_dataset, canonical_saved, canonical_snapshot
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
    except Exception as error:
        return retain_partial(partial, case, error)


def execute_case(case, root, partial):
    if case.feature != "storage" or case.variant != "concurrent":
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
    dataset = make_dataset(case.tier)
    repetitions, outcomes, evidence = [], [], []
    def result():
        current = CaseResult(case, Decision(Status.INCONCLUSIVE), owned_root(root)["runId"],
            artifact={"name":artifact.name,"sha256":artifact.sha256,"revision":artifact.revision,"provenance":artifact.provenance},
            identities={"runtime":runtime.id,"serverSha256":runtime.server_sha256,"javaSha256":runtime.java_sha256,
                        "probeSha256":runtime.probe["sha256"],"botSha256":runtime.bot["sha256"],
                        "fixtureSha256":read_document(prepared/"baseline.json")["recipeSha256"]},
            observations={"repetitions":list(repetitions),"profiledDiagnostic":settings.get("profile",False),
                          "actors":"operator; permission-denial and non-operator admission are separate requirements"},
            paths={"attempts":list(evidence)}, cleanup="see owned attempt records")
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
                probe.send("fixture.verify",{"directory":str(server/"fixture")})
                if validate_fixture(probe.wait("fixture.verified")["data"]).status != Status.PASS:
                    raise ValueError("prepared native fixture verification failed")
                probe.send("commands.tree")
                tree = probe.wait("commands.tree")["data"]
                atomic_write(attempt.directory/"command-tree.json", {"schemaVersion":1,**tree})
                bot_root = create_root(attempt.directory/"bots")
                driver = BotDriver(runtime.bot["path"],bot_root,attempt.record.run_id,attempt.record.attempt_id)
                driver.send("start",{"address":f"127.0.0.1:{port}","count":case.clients,"mode":"vanilla"})
                ready = driver.wait("ready",150)
                probe.send("players.snapshot")
                native = probe.wait("players.snapshot")["data"]
                if validate_ready(ready,native["players"],case.clients,"vanilla").status != Status.PASS:
                    raise ValueError("native storage actor count/UUID mismatch")
                if canonical_snapshot(native["waypointLists"]) != canonical_snapshot([
                    {"dimension":dimension,"name":value["list_name"],"revision":value["n"],"waypoints":[point["name"] for point in value["waypoints"]]}
                    for dimension,lists in dataset.items() for value in lists]):
                    raise ValueError("native dataset names/revisions differ from disk fixture")
                actors = [value["name"] for value in ready["bots"]]
                probe.send("commands.execute",{"commands":[f"op {actor}" for actor in actors]})
                probe.wait("commands.executed")
                prime = []
                for actor in actors:
                    driver.send("action",{"actionId":f"prime-{actor}","bot":actor,"operation":"command","command":"/wp help","acknowledged":True})
                for _ in actors:
                    prime.append(driver.wait("action_result",30))
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
                warmup = collect_commands(probe,driver,case.tier,actors,case.warmup_seconds,"WARMUP",f"{index}-warm",evidence_path=attempt.directory/"warmup.json")
                atomic_write(attempt.directory/"warmup.json",warmup)
                warm_mutations = sum(" set color " in action["command"] for action in warmup["actions"])
                attempt.transition("MEASURING")
                measured = collect_commands(probe,driver,case.tier,actors,case.measurement_seconds,"MEASURING",f"{index}-measure",warm_mutations,evidence_path=attempt.directory/"measurement.json")
                atomic_write(attempt.directory/"measurement.json",measured)
                repetitions.append(summarize_window(measured,warmup["seconds"]))
                atomic_write(partial,result())
                attempt.transition("QUIESCING")
                if profiled:
                    outcomes.append(stop_jfr(process,runtime.java,profile_path))
                driver.close()
                driver = None
                probe.send("players.snapshot")
                final = probe.wait("players.snapshot")["data"]
                if final["players"]:
                    outcomes.append(Decision(Status.FAIL,("storage actors remain after disconnect",)))
                expected = deepcopy(dataset)
                mutations = warm_mutations + sum(" set color " in action["command"] for action in measured["actions"])
                for value in expected["minecraft:overworld"]:
                    if value["list_name"] == "perf_00":
                        value["n"] += mutations
                        next(point for point in value["waypoints"] if point["name"] == "wp_00000")["color"] = f"#{0x100000+mutations:06X}"
            finally:
                if driver:
                    driver.abort()
                stop_owned(process,30)
            actual = {path.stem.replace("$",":"):json.loads(path.read_text()) for path in directory.glob("*.json")}
            correct = canonical_saved(expected) == canonical_saved(actual)
            outcomes.append(Decision(Status.PASS if correct else Status.FAIL,() if correct else ("exact saved storage contents differ",)))
            atomic_write(attempt.directory/"saved.json",{"schemaVersion":1,"expected":canonical_saved(expected),"actual":canonical_saved(actual),"correct":correct})
            process,probe = boot(runtime,server,False,artifact.sha256)
            try:
                probe.send("players.snapshot")
                reopened = probe.wait("players.snapshot")["data"]
                correct = canonical_snapshot(reopened["waypointLists"]) == canonical_snapshot(final["waypointLists"])
                outcomes.append(Decision(Status.PASS if correct else Status.FAIL,() if correct else ("reopened storage names/revisions differ",)))
                atomic_write(attempt.directory/"reopened.json",{"schemaVersion":1,**reopened,"correct":correct})
            finally:
                stop_owned(process,30)
            verify_artifact(artifact,release)
            verify_artifact(artifact,server/"plugins/audited.jar")
            attempt.transition("STOPPED")
            attempt.finish(exit_code(result().decision),"native measurement and save/reopen retained")
            atomic_write(partial,result())
    return replace(result(),cleanup="all owned bot/server groups stopped")
