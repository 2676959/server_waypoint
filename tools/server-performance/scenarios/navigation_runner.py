"""Prepared navigation, terrain and protection cases. Command-leaf behavior stays in the storage runner."""
import json
from pathlib import Path
import uuid

from artifacts import sha256, verify_artifact
from bot_driver import BotDriver, validate_ready
from budgets import evaluate
from contracts import atomic_write, load_release, load_runtimes, read_document
from controller import Attempt, NativeFailure, create_root, owned_root
from models import CaseResult, Decision, Status
from preparation import verify_evidence
from processes import stop_owned
from reporting import combine_decisions, exit_code
from scenarios.inventory import retain_partial
from scenarios.inventory_container import exercise_inventory_container, finish_container_cleanup
from scenarios.navigation_behavior import VARIANT_CATALOG, exercise_variant
from scenarios.navigation_workload import collect_window, delivery_status, reject_stale_bytes, repetition_record, tick_p95_ns
from server_health import native_log_health
from worlds import boot, clone_baseline, install_server

PAPER = "paper-1.21.11"


def prepare_repetition(case, root, index):
    identity = case.id.replace(":", "_").replace("/", "_")
    world = Path(root) / identity / f"repeat-{index + 1}" / "world"
    return {"clients": case.clients, "world": str(world)}


def seed_navigation_fixture(server):
    """Write the named lists the Task 11 catalog addresses. This is fixture setup, not offered work."""
    directory = Path(server) / "plugins/ServerWaypoint/waypoints"
    directory.mkdir(parents=True, exist_ok=True)

    def point(name, label, pos, color, description):
        return {
            "name": name, "display_name": label, "initials": name[:1].upper(), "pos": pos,
            "color": color, "yaw": 0, "global": True, "keywords": [], "description": description,
        }

    overworld = [
        {"list_name": "nav_fixture", "n": 7, "waypoints": [
            point("a", "Target A", [320, -61, 320], "#112233", "Native navigation fixture"),
            point("b", "Target B", [-320, -61, -320], "#445566", "Native navigation fixture")]},
        {"list_name": "terrain_fixture", "n": 7, "waypoints": [
            point("a", "Terrain A", [320, -61, 320], "#112233", "Terrain fixture"),
            point("b", "Terrain B", [-320, -61, -320], "#112233", "Terrain fixture")]},
        {"list_name": "dimension_fixture", "n": 7, "waypoints": [
            point("overworld", "Overworld", [320, -61, 320], "#112233", "Dimension fixture"),
            point("overworld_b", "Overworld B", [360, -61, 360], "#224466", "Same-region retarget fixture")]},
        {"list_name": "protection_fixture", "n": 7, "waypoints": [
            point("target", "Protected", [320, -61, 320], "#112233", "Physical protection fixture")]},
    ]
    (directory / "minecraft$overworld.json").write_text(json.dumps(overworld, indent=4) + "\n")
    nether = [{"list_name": "dimension_fixture", "n": 7, "waypoints": [
        point("nether", "Nether", [0, 80, 0], "#332211", "Unexplored nether fixture")]}]
    (directory / "minecraft$the_nether.json").write_text(json.dumps(nether, indent=4) + "\n")
    properties = Path(server) / "server.properties"
    if properties.is_file():
        properties.write_text(properties.read_text().replace("allow-nether=false", "allow-nether=true"))


def continue_after_failure(failed, nxt):
    del failed
    return nxt


def retained_after_error(outcomes, error):
    status = Status.FAIL if isinstance(error, NativeFailure) else Status.BLOCKED
    reason = str(error) or type(error).__name__
    return combine_decisions((*outcomes, Decision(status, (reason,))))


def interrupted_result(case, prior, stop):
    stop()
    return CaseResult(case, combine_decisions((prior, Decision(Status.BLOCKED, ("interrupted",)))))


def run_case(case, root):
    partial = Path(root) / "partials" / (str(uuid.uuid4()) + ".json")
    try:
        return execute_case(case, Path(root), partial)
    except KeyboardInterrupt:
        if partial.exists():
            atomic_write(Path(root) / "results" / ("interrupted-" + partial.name), retain_partial(partial, case, KeyboardInterrupt("interrupted navigation case")))
        raise
    except Exception as error:
        return retain_partial(partial, case, error)


def execute_case(case, root, partial):
    if (case.feature, case.variant) not in VARIANT_CATALOG:
        return CaseResult(case, Decision(Status.BLOCKED, (f"unmapped variant: {case.feature}/{case.variant}",)))
    if case.mode != "audit" or case.runtime != PAPER:
        return CaseResult(case, Decision(Status.BLOCKED, ("navigation native adapter is verified only for original Paper 1.21.11",)))
    runtime = next(value for value in load_runtimes(root / "runtime-lock.json") if value.id == case.runtime)
    settings = read_document(root / "prepared.json")
    verify_evidence(root, settings)
    prepared = Path(settings["worlds"][f"{case.fixture}-{case.tier}"])
    artifact = next(value for value in load_release(root / "release.json") if value.name == runtime.artifact)
    release = root / "artifacts" / artifact.name
    matches = {
        "bot": _matches(runtime.bot.get("path"), runtime.bot.get("sha256")),
        "probe": _matches(runtime.probe.get("path"), runtime.probe.get("sha256")),
        "artifact": _matches(release, artifact.sha256),
    }
    stale = reject_stale_bytes(matches)
    if stale.status != Status.PASS:
        return CaseResult(case, stale)
    verify_artifact(artifact, release)
    repetitions, outcomes, evidence, behavior_paths = [], [], [], []
    assertions = {}

    def current():
        result = CaseResult(
            case, Decision(Status.INCONCLUSIVE), owned_root(root)["runId"],
            artifact={"name": artifact.name, "sha256": artifact.sha256, "revision": artifact.revision, "provenance": artifact.provenance},
            identities={
                "runtime": runtime.id, "serverSha256": runtime.server_sha256, "javaSha256": runtime.java_sha256,
                "probeSha256": runtime.probe["sha256"], "botSha256": runtime.bot["sha256"],
                "fixtureSha256": read_document(prepared / "baseline.json")["recipeSha256"],
            },
            observations={"repetitions": list(repetitions), "profiledDiagnostic": settings.get("profile", False)},
            assertions=dict(assertions), paths={"attempts": list(evidence), "behavior": list(behavior_paths)}, cleanup="see owned attempt records")
        return result

    try:
        return _run_repetitions(case, root, partial, current, repetitions, outcomes, evidence, behavior_paths, assertions, runtime, release, prepared, artifact)
    except (OSError, RuntimeError, TimeoutError, ValueError) as error:
        decision = retained_after_error(outcomes, error)
        decided = _with_decision(current(), decision)
        atomic_write(partial, decided)
        return decided


def _run_repetitions(case, root, partial, current, repetitions, outcomes, evidence, behavior_paths, assertions, runtime, release, prepared, artifact):
    for index in range(case.repeats):
        plan = prepare_repetition(case, root / "attempts", index)
        with Attempt(root, case.id + f":repeat-{index + 1}") as attempt:
            evidence.append(str(attempt.path))
            atomic_write(partial, current())
            server = create_root(plan["world"])
            port = install_server(runtime, server, runtime.probe["path"], release)
            clone_baseline(prepared / "frozen", server)
            seed_navigation_fixture(server)
            attempt.transition("STARTING")
            process, probe = boot(runtime, server, False, artifact.sha256, attempt.record.attempt_id)
            driver = None
            try:
                driver = BotDriver(runtime.bot["path"], create_root(attempt.directory / "bots"), attempt.record.run_id, attempt.record.attempt_id)
                driver.send("start", {"address": f"127.0.0.1:{port}", "count": plan["clients"], "mode": "vanilla"})
                ready = driver.wait("ready", 150)
                probe.send("players.snapshot")
                native = probe.wait("players.snapshot")["data"]
                readiness = validate_ready(ready, native["players"], plan["clients"], "vanilla")
                outcomes.append(readiness)
                if readiness.status != Status.PASS:
                    raise ValueError("; ".join(readiness.reasons))
                names = tuple(player["name"] for player in native["players"] if isinstance(player.get("name"), str))
                # Operators are exempt from vanilla command-spam admission. This is fixture
                # setup, matching the command throughput runner, and is not offered work.
                probe.send("commands.execute", {"commands": [f"op {name}" for name in names]})
                probe.wait("commands.executed")
                attempt.transition("READY")
                try:
                    if (case.feature, case.variant) == ("protection", "inventory-click-drag-open-close"):
                        behavior = exercise_inventory_container(probe, driver, native["players"], attempt.directory / "behavior")
                    else:
                        behavior = exercise_variant(case, probe, driver, attempt.directory / "behavior", names)
                except (OSError, RuntimeError, TimeoutError, ValueError) as error:
                    behavior = {
                        "decisions": (Decision(Status.BLOCKED, (f"native behavior observation unavailable: {error}",)),),
                        "assertions": {},
                        "paths": {},
                    }
                outcomes.extend(behavior["decisions"])
                if behavior["paths"].get("observation"):
                    behavior_paths.append(behavior["paths"]["observation"])
                statuses = [decision.status for decision in behavior["decisions"]]
                if Status.FAIL in statuses:
                    assertions[f"repeat-{index + 1}-behavior"] = False
                elif statuses and all(status == Status.PASS for status in statuses):
                    assertions[f"repeat-{index + 1}-behavior"] = True
                attempt.transition("WARMUP")
                warmup = collect_window(case, probe, driver, "WARMUP", attempt.directory / "warmup", names)
                attempt.transition("MEASURING")
                measured = collect_window(case, probe, driver, "MEASURING", attempt.directory / "measurement", names)
                ticks = measured["tickNs"]
                outcomes.append(delivery_status(measured["offeredActions"], measured["completedActions"], measured["driverPending"], tick_p95_ns(ticks)))
                repetitions.append(repetition_record(
                    case, warmup["seconds"], measured["seconds"], ticks, measured["offeredActions"],
                    measured["completedActions"], measured["actionLatencyNs"], "client-monotonic"))
                if behavior.get("chestsArmed"):
                    outcomes.extend(finish_container_cleanup(probe, names))
                attempt.transition("QUIESCING")
                probe.send("fixture.quiesce")
                probe.wait("fixture.quiesced")
                attempt.transition("STOPPED")
            finally:
                if driver is not None:
                    driver.abort()
                if process is not None:
                    stop_owned(process, 30)
                    outcomes.append(native_log_health(process.log))
                    process = None
            attempt.finish(exit_code(combine_decisions(outcomes)), "navigation repetition retained")
            atomic_write(partial, current())
    result = current()
    return _with_decision(result, combine_decisions((*outcomes, evaluate(result, root / "budgets.json"))))


def _with_decision(result, decision):
    from dataclasses import replace
    return replace(result, decision=decision, cleanup="all owned bot/server groups stopped")


def _matches(path, digest):
    try:
        return bool(path and digest) and sha256(path) == digest
    except OSError:
        return False
