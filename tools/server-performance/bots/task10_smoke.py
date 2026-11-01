"""Isolated Task 10 functional checks; timings are produced by the prepared runner."""
import argparse
from dataclasses import replace
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready
from contracts import load_release, load_runtimes, atomic_write, read_document
from controller import create_root
from models import Status
from processes import stop_owned
from scenarios.command_behavior import semantic_fixture, exercise_semantics, exercise_reads, exercise_boundaries, seed_mutations, exercise_mutations, snapshot, players, local_leaf_report, execute, wait_for_recovered_dispatch
from scenarios.storage_commands import make_dataset, canonical_saved, process_allocation
from worlds import install_server, clone_baseline, boot


def run(root, baseline, tier, exhaustive, boundaries_only=False, release_manifest=None, runtime_lock=None):
    tools = Path(__file__).resolve().parents[1]
    runtime_lock = Path(runtime_lock) if runtime_lock else tools/"config/runtimes.json"
    release_manifest = Path(release_manifest) if release_manifest else tools/"config/releases/4.0.0.json"
    runtime = next(r for r in load_runtimes(runtime_lock) if r.id == "paper-1.21.11")
    runtime = replace(runtime,probe={**runtime.probe,"sha256":sha256(runtime.probe["path"])})
    artifact = next(a for a in load_release(release_manifest) if a.name == runtime.artifact)
    release = Path("/Volumes/ssd/sw-performance-cache") / artifact.name
    verify_artifact(artifact,release)
    root = create_root(root)
    server = create_root(root/"server")
    port = install_server(runtime,server,runtime.probe["path"],release)
    clone_baseline(baseline,server)
    dataset = make_dataset(tier,skewed=True)
    dataset["minecraft:overworld"].append(semantic_fixture())
    directory = server/"plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True)
    for dimension,lists in dataset.items():
        (directory/(dimension.replace(":","$")+".json")).write_text(json.dumps(lists,indent=4)+"\n")
    process,probe = boot(runtime,server,False,artifact.sha256)
    driver = None
    evidence = {"schemaVersion":1,"artifactSha256":artifact.sha256,"probeSha256":runtime.probe["sha256"],"botSha256":sha256(runtime.bot["path"]),"tier":tier}
    try:
        execute(probe, "difficulty peaceful")
        probe.send("commands.tree")
        tree = probe.wait("commands.tree")["data"]
        atomic_write(root/"command-tree.json",{"schemaVersion":1,**tree})
        probe.send("process.snapshot")
        allocation_before = probe.wait("process.snapshot")["data"]
        if canonical_saved(snapshot(probe)) != canonical_saved(dataset):
            raise ValueError("native initial exact fixture mismatch")
        if exhaustive:
            cases,dataset = seed_mutations(dataset,tree)
            stop_owned(process,30)
            process = None
            for dimension, lists in dataset.items():
                (directory/(dimension.replace(":","$")+".json")).write_text(json.dumps(lists,indent=4)+"\n")
            process,probe = boot(runtime,server,False,artifact.sha256)
            probe.send("commands.tree")
            fresh_tree = probe.wait("commands.tree")["data"]
            if tree != fresh_tree or canonical_saved(snapshot(probe)) != canonical_saved(dataset):
                raise ValueError("restarted exact mutation fixture/native tree differs")
            dataset = exercise_mutations(probe,server,dataset,cases,root/"mutations.json")
        bot_root = create_root(root/"bots")
        driver = BotDriver(runtime.bot["path"],bot_root,probe.stream.run_id,probe.stream.attempt_id)
        driver.send("start",{"address":f"127.0.0.1:{port}","count":1,"mode":"vanilla"})
        ready = driver.wait("ready",150)
        probe.send("players.snapshot")
        native = probe.wait("players.snapshot")["data"]
        if validate_ready(ready,native["players"],1,"vanilla").status != Status.PASS:
            raise ValueError("native actor readiness mismatch")
        actor = ready["bots"][0]["name"]
        wait_for_recovered_dispatch(probe, driver, actor)
        if not boundaries_only:
            dataset = exercise_semantics(probe,server,driver,actor,dataset,root/"semantics.json")
            evidence["failures"] = read_document(root/"semantics.json")["failures"]
        else:
            execute(probe,"op " + actor)
        if exhaustive or boundaries_only:
            evidence["boundaryLeaves"] = exercise_boundaries(probe,server,tree,root/"boundaries.json",actor,driver)
        if exhaustive:
            evidence["readsAndProviders"] = exercise_reads(probe,server,tree,root/"reads.json",ready["bots"][0]["name"])
            local_leaf_report(tree,root/"reads.json",root/"mutations.json",root/"semantics.json",root/"coverage.json",root/"boundaries.json")
        driver.close()
        driver = None
        evidence["disconnected"] = not players(probe)
        probe.send("process.snapshot")
        allocation_after = probe.wait("process.snapshot")["data"]
        # Exhaustive setup restarts its JVM, so those counters have separate identities.
        if not exhaustive:
            evidence["wholeProcessAllocatedBytes"] = process_allocation(allocation_before,allocation_after)
    except BaseException as error:
        evidence["error"] = str(error)
        raise
    finally:
        try:
            if driver:
                driver.abort()
        finally:
            if process is not None:
                stop_owned(process,30)
            atomic_write(root/"behavior.json",evidence)
    actual = {p.stem.replace("$",":"):json.loads(p.read_text()) for p in directory.glob("*.json")}
    atomic_write(root/"saved.json",{"schemaVersion":1,"expected":canonical_saved(dataset),"actual":canonical_saved(actual)})
    if canonical_saved(dataset) != canonical_saved(actual):
        raise ValueError("exact persisted behavior contents differ")
    process,probe = boot(runtime,server,False,artifact.sha256)
    try:
        reopened = snapshot(probe)
        if canonical_saved(reopened) != canonical_saved(dataset):
            raise ValueError("native reopened exact properties/revisions differ")
        atomic_write(root/"reopened.json",{"schemaVersion":1,"dimensions":reopened})
        evidence.update(savedExactly=True,reopenedExactly=True)
    finally:
        stop_owned(process,30)
        atomic_write(root/"behavior.json",evidence)
    failed = bool(evidence.get("failures"))
    print(f"Task 10 native behavior {'FAIL' if failed else 'PASS'}; exact save/reopen PASS",flush=True)
    return 1 if failed else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,required=True)
    parser.add_argument("--baseline",type=Path,required=True)
    parser.add_argument("--tier",choices=("small","medium"),default="small")
    parser.add_argument("--exhaustive",action="store_true")
    parser.add_argument("--boundaries-only",action="store_true")
    parser.add_argument("--release-manifest",type=Path)
    parser.add_argument("--runtime-lock",type=Path)
    args = parser.parse_args()
    raise SystemExit(run(args.root,args.baseline,args.tier,args.exhaustive,args.boundaries_only,args.release_manifest,args.runtime_lock))
