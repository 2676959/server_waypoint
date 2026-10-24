"""CLI preparation and evidence input identity. No production builds are substituted."""
from pathlib import Path
import platform
import shutil
import subprocess

from artifacts import sha256, stage_artifacts
from contracts import atomic_write, load_release, load_runtimes
from controller import create_root, preparation_transition
from models import Status
from preflight import check_runtime
from selection import select_cases
from worlds import prepare_world

TOOLS = Path(__file__).resolve().parent
REPOSITORY = TOOLS.parents[1]


def seal_evidence(root, worlds):
    root = Path(root)
    paths = [root / name for name in ("runtime-lock.json", "release.json", "budgets.json", "selection.json")]
    for value in worlds.values():
        world = Path(value)
        if root not in world.parents:
            raise ValueError("prepared fixture must belong to this root")
        paths.append(world / "baseline.json")
        paths.extend(path for path in (world / "fixture").rglob("*") if path.is_file())
    return {str(path.relative_to(root)): sha256(path) for path in sorted(paths)}


def verify_evidence(root, settings):
    root = Path(root)
    if not settings.get("evidence"):
        raise ValueError("prepared evidence seal missing")
    for name, expected in settings["evidence"].items():
        path = root / name
        if path.is_symlink() or not path.is_file() or root not in path.resolve().parents or sha256(path) != expected:
            raise ValueError(f"prepared evidence changed: {name}")


def input_hashes():
    folders = [TOOLS, REPOSITORY / "paper/src/performanceProbe", REPOSITORY / "cross-server/src/performanceProbe"]
    result = {}
    for folder in folders:
        for path in sorted(folder.rglob("*")):
            if path.suffix not in (".py", ".java", ".json", ".kts", ".yml") or any(part in ("build", ".gradle", "__pycache__") for part in path.relative_to(folder).parts):
                continue
            result[str(path.relative_to(REPOSITORY))] = sha256(path)
    for path in [REPOSITORY / "paper/build.gradle.kts", REPOSITORY / "cross-server/build.gradle.kts"]:
        result[str(path.relative_to(REPOSITORY))] = sha256(path)
    return result


def prepare(args):
    root = create_root(args.output)
    runtimes = load_runtimes(args.runtime_lock)
    cases = select_cases(TOOLS / "config/features.json", TOOLS / "config/scenarios.json", runtimes, args.suite, tuple(args.tiers.split(",")), tuple(filter(None, args.targets.split(","))), tuple(filter(None, args.scenarios.split(","))), tuple(filter(None, args.variants.split(","))))
    selected = [r for r in runtimes if r.id in {case.runtime for case in cases}]
    blockers = []
    for runtime in selected:
        decision = check_runtime(runtime, root / "doctor", frozenset({"fixture", "tick-processing", "profile"}))
        blockers.extend(decision.reasons)
        if runtime.id != "paper-1.21.11":
            blockers.append(f"adapter pending: {runtime.id}")
    if blockers:
        atomic_write(root / "blockers.json", {"schemaVersion": 1, "reasons": blockers})
        raise ValueError("; ".join(blockers))
    artifacts = tuple(a for a in load_release(args.release_manifest) if a.name in {r.artifact for r in selected})
    if len(artifacts) != len({r.artifact for r in selected}):
        raise ValueError("selected runtime has no immutable artifact")
    stage_artifacts(artifacts, args.cache, root / "artifacts")
    for name, source in (("runtime-lock.json", args.runtime_lock), ("release.json", args.release_manifest), ("budgets.json", TOOLS / "config/budgets.json")):
        shutil.copyfile(source, root / name)
    atomic_write(root / "selection.json", {"schemaVersion": 1, "partial": args.suite != "all" or bool(args.targets or args.scenarios or args.variants), "cases": cases})
    preparation_transition(root, "ARTIFACTS_VERIFIED")
    worlds = {}
    for runtime in selected:
        for case in cases:
            if case.runtime != runtime.id or case.clients or case.feature not in ("idle", "world-container", "inventory-regression"):
                continue
            key = f"{case.fixture}-{case.tier}"
            if key in worlds:
                continue
            destination = root / "fixtures" / key
            print(f"Preparing native fixture {key}", flush=True)
            prepare_world(runtime, TOOLS / "config/worlds" / f"{key}.json", destination)
            worlds[key] = str(destination)
    preparation_transition(root, "FIXTURES_VALIDATED")
    atomic_write(root / "prepared.json", {"schemaVersion": 1, "worlds": worlds, "inputs": input_hashes(), "evidence": seal_evidence(root, worlds), "profile": args.profile})
    atomic_write(root / "environment.json", {"schemaVersion": 1, "platform": platform.platform(), "machine": platform.machine(), "cpu": subprocess.run(["sysctl", "-n", "machdep.cpu.brand_string"], capture_output=True, text=True).stdout.strip() if platform.system() == "Darwin" else platform.processor(), "memory": subprocess.run(["sysctl", "-n", "hw.memsize"], capture_output=True, text=True).stdout.strip() if platform.system() == "Darwin" else "unrecorded", "javaFlags": ["-Xms1G", "-Xmx2G", "-XX:+UseG1GC"], "loadGenerator": "same host; zero clients", "profiledDiagnostic": args.profile})
    preparation_transition(root, "PREPARED")
    return root
