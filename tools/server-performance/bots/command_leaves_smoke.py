"""Actual native read leaves and console feedback; no timed or whole-family gate claim."""
import argparse
from dataclasses import replace
import json
from pathlib import Path
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from artifacts import sha256,verify_artifact
from contracts import load_release,load_runtimes,atomic_write
from controller import create_root
from processes import stop_owned
from scenarios.command_leaves import read_cases,console_feedback,suggestion_cases,verify_suggestions
from scenarios.storage_commands import make_dataset,canonical_saved,canonical_snapshot,command_coverage
from worlds import install_server,clone_baseline,boot


def run(root,baseline,limit,check_suggestions=False):
    tools = Path(__file__).resolve().parents[1]
    runtime = next(r for r in load_runtimes(tools/"config/runtimes.json") if r.id == "paper-1.21.11")
    runtime = replace(runtime,probe={**runtime.probe,"sha256":sha256(runtime.probe["path"])})
    artifact = next(a for a in load_release(tools/"config/releases/4.0.0.json") if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-m0-audit/artifacts")/artifact.name
    verify_artifact(artifact,release)
    root = create_root(root)
    server = create_root(root/"server")
    install_server(runtime,server,runtime.probe["path"],release)
    clone_baseline(baseline,server)
    dataset = make_dataset("small")
    directory = server/"plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    for dimension,lists in dataset.items():
        (directory/(dimension.replace(":","$")+".json")).write_text(json.dumps(lists,indent=4)+"\n")
    process,probe = boot(runtime,server,False,artifact.sha256)
    evidence = {"schemaVersion":1,"artifactSha256":artifact.sha256,"probeSha256":runtime.probe["sha256"],"scope":"read-leaf-functional-smoke"}
    try:
        probe.send("commands.tree")
        tree = probe.wait("commands.tree")["data"]
        atomic_write(root/"command-tree.json",{"schemaVersion":1,**tree})
        cases = read_cases(tree)
        evidence.update(totalReadLeaves=len(cases),fullReadScope=not limit,totalNativeExecutablePaths=sum(bool(node["executable"]) for node in tree["nodes"]))
        if limit:
            cases = cases[:limit]
        evidence["selected"] = cases
        probe.send("players.snapshot")
        before = probe.wait("players.snapshot")["data"]
        receipts = []
        for start in range(0,len(cases),100):
            batch = cases[start:start+100]
            probe.send("commands.execute",{"commands":[value["command"] for value in batch]})
            data = probe.wait("commands.executed",60)["data"]
            if data["droppedCallbacks"]:
                raise ValueError("bounded callback evidence overflow")
            receipts.extend(data["callbacks"])
        if check_suggestions:
            suggestions = suggestion_cases(tree)
            reports = []
            for case in suggestions:
                probe.send("commands.suggest",{"input":case["input"]})
                reports.append(probe.wait("commands.suggested")["data"])
            suggestion_decision = verify_suggestions(suggestions,reports)
            evidence.update(suggestionCases=suggestions,suggestionReports=reports,suggestionDecision=suggestion_decision)
            if suggestion_decision.status.value != "PASS":
                raise ValueError("native custom suggestion provider evidence missing")
        probe.send("players.snapshot")
        after = probe.wait("players.snapshot")["data"]
        probe.send("commands.feedback-barrier")
        barrier = probe.wait("commands.feedback.completed")
        log = server/"logs/latest.log"
        deadline = time.monotonic()+10
        while True:
            transcript = log.read_text()
            if any('"type":"commands.feedback.completed"' in line and probe.stream.run_id in line and probe.stream.attempt_id in line for line in transcript.splitlines()):
                break
            if time.monotonic() >= deadline:
                raise TimeoutError("native console file-log drain deadline")
            time.sleep(0.02)
        observed = console_feedback(transcript,probe.stream.run_id,probe.stream.attempt_id)
        expected = {value["path"]:value["command"] for value in cases}
        if len(receipts) != len(cases) or any(value.get("exception") or value["input"] != expected.get(value["path"]) for value in receipts):
            raise ValueError("native callback did not match each requested leaf/input")
        decision = command_coverage(expected,observed,())
        evidence.update(decision=decision,observations=observed,callbacks=receipts,stateUnchanged=canonical_snapshot(before["waypointLists"])==canonical_snapshot(after["waypointLists"]))
        if decision.status.value != "PASS" or not evidence["stateUnchanged"]:
            raise ValueError("native read leaf feedback/state proof missing")
    except BaseException as error:
        evidence["error"] = str(error)
        raise
    finally:
        stop_owned(process,30)
        atomic_write(root/"read-leaves.json",evidence)
    actual = {path.stem.replace("$",":"):json.loads(path.read_text()) for path in directory.glob("*.json")}
    evidence["savedUnchanged"] = canonical_saved(actual) == canonical_saved(dataset)
    if not evidence["savedUnchanged"]:
        from models import Decision,Status
        evidence["decision"] = Decision(Status.FAIL,("read leaves changed exact saved data",))
        atomic_write(root/"read-leaves.json",evidence)
        raise ValueError("read leaves changed exact saved data")
    atomic_write(root/"read-leaves.json",evidence)
    print(f"native read leaves: {len(cases)}/{evidence['totalReadLeaves']} with actual console feedback; exact saved state unchanged")


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,required=True)
    parser.add_argument("--baseline",type=Path,required=True)
    parser.add_argument("--limit",type=int,default=0)
    parser.add_argument("--suggestions",action="store_true")
    args=parser.parse_args()
    if args.limit < 0:
        parser.error("limit must be nonnegative")
    run(args.root,args.baseline,args.limit,args.suggestions)
