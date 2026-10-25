"""Native mutation leaves with independent property/revision and disk/reopen oracles."""
import argparse
from copy import deepcopy
from dataclasses import replace
import json
from pathlib import Path
import sys
import time
import uuid
import re
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from artifacts import sha256,verify_artifact
from contracts import load_release,load_runtimes,atomic_write,read_document
from controller import create_root
from processes import stop_owned
from scenarios.command_leaves import console_feedback
from scenarios.mutation_leaves import mutation_cases,resolve_generated,async_reload_feedback,fixture_point
from scenarios.storage_commands import make_dataset,canonical_saved,command_coverage
from worlds import install_server,clone_baseline,boot


def snapshot(probe):
    probe.send("storage.snapshot")
    return probe.wait("storage.snapshot")["data"]["dimensions"]


def execute(probe,command):
    probe.send("commands.execute",{"commands":[command]})
    return probe.wait("commands.executed")["data"]


def replace_list(dataset,name,value):
    lists=dataset["minecraft:overworld"]
    lists[:]=[item for item in lists if item["list_name"]!=name]
    if value is not None:
        lists.append(deepcopy(value))


def run(root,baseline,inventory):
    cases=mutation_cases(read_document(inventory))
    dataset=make_dataset("small")
    dataset["minecraft:overworld"].extend(deepcopy(case["before"]) for case in cases if case["before"] is not None)
    dataset["minecraft:overworld"].append({"list_name":"nonempty_guard","n":17,"waypoints":[fixture_point()]})
    expected=deepcopy(dataset)
    tools=Path(__file__).resolve().parents[1]
    runtime=next(r for r in load_runtimes(tools/"config/runtimes.json") if r.id=="paper-1.21.11")
    runtime=replace(runtime,probe={**runtime.probe,"sha256":sha256(runtime.probe["path"])})
    artifact=next(a for a in load_release(tools/"config/releases/4.0.0.json") if a.name==runtime.artifact)
    release=Path("/Volumes/ssd/sw-performance-m0-audit/artifacts")/artifact.name
    verify_artifact(artifact,release)
    root=create_root(root)
    server=create_root(root/"server")
    install_server(runtime,server,runtime.probe["path"],release)
    clone_baseline(baseline,server)
    directory=server/"plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    for dimension,lists in dataset.items():
        (directory/(dimension.replace(":","$")+".json")).write_text(json.dumps(lists,indent=4)+"\n")
    evidence={"schemaVersion":1,"artifactSha256":artifact.sha256,"probeSha256":runtime.probe["sha256"],"cases":cases,"steps":[],"scope":"console-storage-mutation-leaves"}
    atomic_write(root/"fixture.json",{"schemaVersion":1,"dimensions":dataset})
    process,probe=boot(runtime,server,False,artifact.sha256)
    try:
        probe.send("commands.tree")
        tree=probe.wait("commands.tree")["data"]
        if {c["path"] for c in mutation_cases(tree)}!={c["path"] for c in cases}:
            raise ValueError("native mutation tree changed from fixture recipe")
        if canonical_saved(snapshot(probe))!=canonical_saved(expected):
            raise ValueError("native mutation fixture values/revisions differ")
        reload_observation=None
        for case in cases:
            command=case["command"]
            if case["path"]=="wp reload":
                settings_file=server/"plugins/ServerWaypoint/config.json"
                settings=json.loads(settings_file.read_text())
                if settings.get("defaultPageLimit",0)<=1:
                    raise ValueError("reload fixture must change a declared nontrivial default page size")
                settings["defaultPageLimit"]=1
                settings_file.write_text(json.dumps(settings,indent=4)+"\n")
                reload_token="PERF_RELOAD_"+uuid.uuid4().hex
                language=server/"plugins/ServerWaypoint/lang/en_us.json"
                if language.exists():
                    raise ValueError("fresh reload language overlay already exists")
                language.write_text(json.dumps({"wp.result.reloaded":reload_token})+"\n")
            if case["removeBeforeRestore"]:
                probe.send("commands.restore-tokens")
                prior=set(probe.wait("commands.restore-tokens")["data"]["tokens"])
                execute(probe,f"wp remove minecraft:overworld {case['owner']} origin")
                probe.send("commands.restore-tokens")
                tokens=set(probe.wait("commands.restore-tokens")["data"]["tokens"])-prior
                if len(tokens)!=1:
                    raise ValueError("restore fixture did not produce exactly one real token")
                command=command.replace("{restoreToken}",tokens.pop())
            receipt=execute(probe,command)
            deadline=time.monotonic()+10
            last=None
            while True:
                actual=snapshot(probe)
                observed=next((v for v in actual["minecraft:overworld"] if case["after"] is not None and v["list_name"]==case["after"]["list_name"]),None)
                candidate=deepcopy(expected)
                try:
                    after=resolve_generated(case,observed)
                    candidate=deepcopy(expected)
                    replace_list(candidate,case["owner"],after)
                    if canonical_saved(actual)==canonical_saved(candidate):
                        expected=candidate
                        break
                except ValueError as error:
                    last=str(error)
                if time.monotonic()>=deadline:
                    atomic_write(root/"mismatch.json",{"schemaVersion":1,"case":case,"expected":candidate,"actual":actual,"error":last})
                    raise ValueError("native mutation fields/revision mismatch: "+case["path"])
                time.sleep(0.05)
            matched=[v for v in receipt["callbacks"] if v["path"]==case["path"] and v["input"]==command]
            if len(matched)!=1 or matched[0].get("exception") or receipt["droppedCallbacks"]:
                raise ValueError("mutation leaf native callback not correlated")
            if case["path"]=="wp reload":
                deadline=time.monotonic()+15
                while reload_observation is None:
                    reload_observation=async_reload_feedback((server/"logs/latest.log").read_text(),probe.stream.run_id,probe.stream.attempt_id,matched[0],reload_token)
                    if time.monotonic()>=deadline:
                        raise TimeoutError("actual asynchronous reload/language response deadline")
                    if reload_observation is None:
                        time.sleep(0.05)
                if canonical_saved(snapshot(probe))!=canonical_saved(expected):
                    raise ValueError("reload altered committed waypoint data")
                evidence["languageReloadApplied"]=True
            if case["removeBeforeRestore"]:
                execute(probe,command)
                if canonical_saved(snapshot(probe))!=canonical_saved(expected):
                    raise ValueError("restore token replay mutated state")
            evidence["steps"].append({"path":case["path"],"command":command,"callback":matched[0],"after":after})
        guarded=execute(probe,"wp remove minecraft:overworld nonempty_guard")
        if not any(value["path"]=="wp remove <dimension> <list identifier>" for value in guarded["callbacks"]):
            raise ValueError("nonempty-list removal denial lacks native callback")
        evidence["nonemptyListRemovalPreserved"]=canonical_saved(snapshot(probe))==canonical_saved(expected)
        if not evidence["nonemptyListRemovalPreserved"]:
            raise ValueError("nonempty-list removal altered protected contents/revision")
        page_command="wp list minecraft:overworld perf_00"
        execute(probe,page_command)
        probe.send("commands.feedback-barrier")
        probe.wait("commands.feedback.completed")
        deadline=time.monotonic()+10
        log=server/"logs/latest.log"
        while '"type":"commands.feedback.completed"' not in log.read_text():
            if time.monotonic()>=deadline:
                raise TimeoutError("native feedback file-log drain deadline")
            time.sleep(0.02)
        observations=console_feedback(log.read_text(),probe.stream.run_id,probe.stream.attempt_id)
        if reload_observation is not None:
            observations=[value for value in observations if value.get("commandObservationId")!=reload_observation["commandObservationId"]]+[reload_observation]
        page_output=next(value for value in observations if value["input"]==page_command)
        rows=[name for line in page_output["feedback"] for name in re.findall(r"^\[P\] (wp_\d{5}) ·",line)]
        evidence["configReloadApplied"]=rows==["wp_00000"]
        if not evidence["configReloadApplied"]:
            raise ValueError("reloaded default page limit did not change actual list output")
        decision=command_coverage([case["path"] for case in cases],observations,())
        evidence.update(observations=observations,coverageDecision=decision)
        if decision.status.value!="PASS":
            raise ValueError("mutation leaf delivered feedback missing")
    except BaseException as error:
        evidence["error"]=str(error)
        raise
    finally:
        stop_owned(process,30)
        atomic_write(root/"mutation-leaves.json",evidence)
    actual={p.stem.replace("$",":"):json.loads(p.read_text()) for p in directory.glob("*.json")}
    evidence["savedExactly"]=canonical_saved(actual)==canonical_saved(expected)
    atomic_write(root/"saved.json",{"schemaVersion":1,"expected":canonical_saved(expected),"actual":canonical_saved(actual)})
    if not evidence["savedExactly"]:
        evidence["error"]="exact saved mutation contents mismatch"
        atomic_write(root/"mutation-leaves.json",evidence)
        raise ValueError(evidence["error"])
    process,probe=boot(runtime,server,False,artifact.sha256)
    try:
        reopened=snapshot(probe)
        evidence["reopenedExactly"]=canonical_saved(reopened)==canonical_saved(expected)
        atomic_write(root/"reopened.json",{"schemaVersion":1,"dimensions":reopened})
        if not evidence["reopenedExactly"]:
            raise ValueError("exact reopened mutation contents mismatch")
    finally:
        stop_owned(process,30)
        atomic_write(root/"mutation-leaves.json",evidence)
    print(f"native {len(cases)} mutation leaves: exact properties/revisions, delivered feedback, saved/reopened contents")


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,required=True)
    parser.add_argument("--baseline",type=Path,required=True)
    parser.add_argument("--inventory",type=Path,required=True)
    args=parser.parse_args()
    run(args.root,args.baseline,args.inventory)
