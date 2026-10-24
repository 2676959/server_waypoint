"""Native generation/placement/save/reopen; immutable baselines are cloned per run."""
from pathlib import Path
import shutil
import subprocess
import time
import uuid

from artifacts import sha256
from contracts import atomic_write, read_document
from controller import create_root, owned_root, available_port
from models import Decision, Status
from probe_driver import ProbeDriver
from processes import start_owned, stop_owned

TOOLS = Path(__file__).resolve().parent


def validate_hello(hello, control, expected_hash):
    reasons = []
    if not {"fixture", "tick-processing", "zero-player-ticking"} <= set(hello.get("capabilities", ())):
        reasons.append("native fixture/timing/activity capability missing")
    if hello.get("tickMetric") != "native-processing-duration":
        reasons.append("tick interval cannot substitute for native processing duration")
    if control:
        if hello.get("auditedJar"):
            reasons.append("no-plugin control has an audited plugin")
    elif not expected_hash or hello.get("auditedJar") != expected_hash:
        reasons.append("audited plugin missing or hash mismatch")
    return Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))


def validate_fixture(value):
    missing = [key for key in ("verified", "reopened", "conserved") if not value.get(key)]
    return Decision(Status.BLOCKED if missing else Status.PASS, tuple(f"fixture missing proof: {key}" for key in missing))


def install_server(runtime, root, probe, artifact=None):
    root = Path(root)
    owned_root(root)
    source = Path(runtime.server)
    if sha256(source) != runtime.server_sha256 or sha256(runtime.java) != runtime.java_sha256:
        raise ValueError("runtime input changed")
    if sha256(probe) != runtime.probe.get("sha256"):
        raise ValueError("probe bytes differ from runtime lock")
    shutil.copyfile(source, root / "server.jar")
    for name in ("libraries", "cache"):
        if (source.parent / name).is_dir():
            shutil.copytree(source.parent / name, root / name)
    (root / "plugins").mkdir()
    shutil.copyfile(probe, root / "plugins/performance-probe.jar")
    if artifact:
        shutil.copyfile(artifact, root / "plugins/audited.jar")
    (root / "plugins/bStats").mkdir()
    (root / "plugins/bStats/config.yml").write_text("enabled: false\nlogFailedRequests: false\n")
    (root / "eula.txt").write_text("eula=true\n")
    (root / "bukkit.yml").write_text("settings:\n    allow-end: false\n")
    port = available_port()
    (root / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "level-seed=95920261024\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2}],"biome":"minecraft:plains"}\n'
        "generate-structures=false\nallow-nether=false\nview-distance=2\nsimulation-distance=2\n"
        "pause-when-empty-seconds=0\nspawn-protection=0\nenable-rcon=false\nenable-query=false\n")
    return port


def boot(runtime, root, control, artifact_hash="", attempt_id=None):
    root = Path(root)
    marker = owned_root(root)
    identifier = attempt_id or str(uuid.uuid4())
    argv = [runtime.java, "-Xms1G", "-Xmx2G", "-XX:+UseG1GC", f"-Xlog:gc*:file=gc-{identifier}.log:time,uptime,level,tags", "-jar", "server.jar", "--nogui"]
    atomic_write(root / f"argv-{identifier}.json", {"schemaVersion": 1, "argv": argv})
    process = start_owned(argv, root, root / f"server-{identifier}.log")
    try:
        deadline = time.monotonic() + 120
        while "Done (" not in process.log.read_text(errors="replace"):
            if process.process.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError(f"native server startup failed: {process.log}")
            time.sleep(0.1)
        driver = ProbeDriver(process, marker["runId"], identifier)
        driver.send("hello", {"control": control, "auditedPath": str(root / "plugins/audited.jar")})
        hello = driver.wait("hello")["data"]
        decision = validate_hello(hello, control, artifact_hash)
        if decision.status != Status.PASS:
            raise ValueError(decision.reasons)
        atomic_write(root / f"hello-{identifier}.json", {"schemaVersion": 1, **hello})
        return process, driver
    except BaseException:
        stop_owned(process, 15)
        raise


def prepare_world(runtime, recipe, output):
    root = create_root(output)
    fixture = root / "fixture"
    libraries = TOOLS / "worldgen/build/install/server-performance-worldgen/lib"
    subprocess.run([runtime.java, "-cp", str(libraries / "*"), "_959.server_waypoint.perf.worldgen.WorldgenMain", "generate", "--recipe", str(Path(recipe).resolve()), "--output", str(fixture)], check=True)
    install_server(runtime, root, Path(runtime.probe["path"]))
    process, driver = boot(runtime, root, True)
    try:
        driver.send("fixture.place", {"directory": str(fixture)})
        first = driver.wait("fixture.placed")["data"]
        driver.send("fixture.quiesce")
        driver.wait("fixture.quiesced")
        atomic_write(root / "placement.json", {"schemaVersion": 1, **first})
    finally:
        stop_owned(process, 30)
    process, driver = boot(runtime, root, True)
    try:
        driver.send("fixture.verify", {"directory": str(fixture)})
        reopened = driver.wait("fixture.verified")["data"]
        if validate_fixture(reopened).status != Status.PASS:
            raise ValueError("native reopen proof missing")
        atomic_write(root / "reopen.json", {"schemaVersion": 1, **reopened})
    finally:
        stop_owned(process, 30)
    baseline = root / "frozen"
    baseline.mkdir()
    for path in root.glob("world*"):
        if path.is_dir():
            shutil.copytree(path, baseline / path.name)
    identity = {str(path.relative_to(baseline)): sha256(path) for path in sorted(baseline.rglob("*")) if path.is_file()}
    atomic_write(root / "baseline.json", {"schemaVersion": 1, "files": identity, "recipeSha256": read_document(fixture / "manifest.json")["recipeSha256"]})
    return baseline


def clone_baseline(baseline, destination):
    baseline, destination = Path(baseline), Path(destination)
    manifest = read_document(baseline.parent / "baseline.json")
    actual = {str(path.relative_to(baseline)): sha256(path) for path in sorted(baseline.rglob("*")) if path.is_file()}
    if actual != manifest["files"]:
        raise ValueError("immutable baseline changed")
    for path in baseline.iterdir():
        shutil.copytree(path, destination / path.name)
