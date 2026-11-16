"""One fresh-process command-tree session, with sequential roles and local clocks."""
import sys
import time
import uuid
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bot_driver import BotDriver, validate_ready
from contracts import atomic_write, read_document
from controller import create_root
from models import Status
from processes import stop_owned
from server_health import native_log_health
from worlds import install_server, boot, clone_baseline


def empty_catalog(snapshot):
    dimensions = snapshot.get("dimensions")
    return isinstance(dimensions, dict) and all(not value.get("waypoints", [None])
        for lists in dimensions.values() for value in lists)


def poll_native(probe, count, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        probe.send("command-tree.snapshot")
        value = probe.wait("command-tree.snapshot")["data"]
        if value["errors"]:
            raise ValueError("native tree observation errors: " + str(value["errors"]))
        if len(value["samples"]) >= count:
            return value
        time.sleep(0.05)
    raise TimeoutError("native command-tree handoff absent")


def client_trees(driver, count, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        driver.send("barrier")
        value = driver.wait("packet_summary")
        driver.wait("rate_sample")
        trees = value.get("commandTrees", [])
        if len(trees) >= count:
            return trees
        time.sleep(0.05)
    raise TimeoutError("decoded/installed command-tree receipt absent")


def run_session(root, binding, baseline=None):
    runtime, artifact, release = binding
    root = create_root(root)
    server = create_root(root / "server")
    port = install_server(runtime, server, runtime.probe["path"], release)
    if baseline is not None:
        clone_baseline(baseline, server)
    with (server / "server.properties").open("a") as stream:
        stream.write("network-compression-threshold=-1\ndifficulty=peaceful\n")
    fixture = read_document(Path(__file__).resolve().parents[1] / "config/command-tree.json")
    config = server / "plugins/ServerWaypoint/config.json"
    config.parent.mkdir(parents=True)
    import json
    config.write_text(json.dumps(fixture["config"], indent=4) + "\n")
    process, probe = boot(runtime, server, False, artifact.sha256)
    evidence = {"schemaVersion": 1, "pid": process.pid, "server": [], "client": [], "ticks": [], "shutdown": False,
                "roles": [], "errors": [], "fixture":fixture, "configuration":json.loads(config.read_text()), "identities": {"candidate":artifact.sha256,
                "probe":runtime.probe["sha256"], "bot":runtime.bot["sha256"]}}
    driver = None
    interrupted = None
    try:
        if evidence["configuration"]["CommandPermission"] != fixture["config"]["CommandPermission"]:
            raise ValueError("native permission configuration differs from fixture")
        for role in ("ordinary", "operator"):
            if role == "operator":
                process.process.stdin.write(b"op swp_reader_00\n")
                process.process.stdin.flush()
            connection = str(uuid.uuid4())
            probe.send("command-tree.arm", {"connectionId":connection, "role":role})
            armed = probe.wait("command-tree.armed")["data"]
            if not empty_catalog(armed["storage"]):
                raise ValueError("command-tree requires an empty waypoint catalog")
            bot_root = create_root(root / role)
            driver = BotDriver(runtime.bot["path"], bot_root, str(uuid.uuid4()), str(uuid.uuid4()))
            driver.send("start", {"address":f"127.0.0.1:{port}", "count":1, "mode":"vanilla",
                                  "treeConnection":connection, "treeRole":role})
            ready = driver.wait("ready", 150)
            probe.send("players.snapshot")
            players = probe.wait("players.snapshot")["data"]
            decision = validate_ready(ready, players["players"], 1, "vanilla")
            if decision.status != Status.PASS:
                raise ValueError(decision.reasons)
            client_start, native_start = len(evidence["client"]), len(evidence["server"])
            initial = client_trees(driver, 1)
            evidence["client"][client_start:] = initial
            native_initial = poll_native(probe, 1)
            evidence["server"][native_start:] = native_initial["samples"]
            # Retain the login before offering the only outstanding refresh.
            atomic_write(bot_root / "login.json", {"schemaVersion":1, "client":initial, "native":native_initial, "ready":ready, "players":players})
            driver.send("action", {"actionId":connection, "bot":"swp_reader_00", "operation":"tree-refresh"})
            action = driver.wait("action_result", 40)
            if isinstance(action.get("treeReceipt"), dict):
                evidence["client"][client_start:] = initial + [action["treeReceipt"]]
            received = client_trees(driver, 2)
            evidence["client"][client_start:] = received
            native = poll_native(probe, 2)
            evidence["server"][native_start:] = native["samples"]
            evidence["ticks"].extend(native["ticks"])
            evidence["roles"].append({"role":role,"connectionId":connection,"armed":armed,"native":native,"action":action})
            atomic_write(bot_root / "delivery.json", {"schemaVersion":1, "client":received,"native":native,"action":action})
            closing, driver = driver, None
            closing.close()
            deadline = time.monotonic() + 15
            while True:
                probe.send("players.snapshot")
                disconnected = probe.wait("players.snapshot")["data"]
                if not disconnected["players"]:
                    break
                if time.monotonic() >= deadline:
                    raise TimeoutError("command-tree player survived disconnect")
                time.sleep(0.05)
            atomic_write(bot_root / "disconnected.json", {"schemaVersion":1, **disconnected})
    except BaseException as error:
        evidence["errors"].append(f"{type(error).__name__}: {error}")
        if isinstance(error, (KeyboardInterrupt, SystemExit)):
            interrupted = error
    finally:
        try:
            if driver is not None:
                driver.abort()
        except Exception as error:
            evidence["errors"].append(f"bot cleanup: {type(error).__name__}: {error}")
        try:
            stop_owned(process, 30)
        except Exception as error:
            evidence["errors"].append(f"server cleanup: {type(error).__name__}: {error}")
        evidence["shutdown"] = "All dimensions are saved" in process.log.read_text(errors="replace")
        evidence["health"] = native_log_health(process.log)
        evidence["serverLog"] = str(process.log)
        atomic_write(root / "session.json", evidence)
    if interrupted is not None:
        raise interrupted
    return evidence
