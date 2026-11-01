import sys
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from artifacts import sha256
from contracts import atomic_write
from controller import create_root
from models import CaseSpec, Decision, Status
from scenarios.storage_commands import make_dataset
from scenarios.storage_runner import run_case


class StorageCheckpointTest(unittest.TestCase):
    def simulate_failure(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory).resolve()
            root = create_root(base / "run")
            prepared = base / "prepared"
            (prepared / "fixture").mkdir(parents=True)
            (prepared / "frozen").mkdir()
            atomic_write(prepared / "baseline.json", {"schemaVersion": 1, "recipeSha256": "a" * 64})
            atomic_write(root / "prepared.json", {"schemaVersion": 1, "profile": False,
                "worlds": {"navigation-terrain-small": str(prepared)}})
            binary = base / "bot"
            binary.write_bytes(b"locked bot")
            runtime = SimpleNamespace(id="paper-1.21.11", artifact="release.jar", bot={"path": str(binary), "sha256": sha256(binary)},
                probe={"path": "unused", "sha256": "b" * 64}, server_sha256="c" * 64, java_sha256="d" * 64)
            artifact = SimpleNamespace(name="release.jar", sha256="e" * 64, revision="original", provenance="published")
            full = make_dataset("small")
            value = full["minecraft:overworld"][0]
            value["waypoints"] = value["waypoints"][:1]
            dataset = {"minecraft:overworld": [value]}
            native_lists = [{"dimension": "minecraft:overworld", "name": value["list_name"],
                "revision": value["n"], "waypoints": [value["waypoints"][0]["name"]]}]
            state = {"boots": 0, "closed": False, "server": None}

            class Probe:
                def __init__(self, number):
                    self.number = number
                def send(self, operation, payload=None):
                    pass
                def wait(self, kind, timeout=60):
                    if kind == "players.snapshot":
                        if self.number == 2 and mode == "reopen-health":
                            raise ValueError("injected snapshot failure")
                        return {"data": {"players": [] if state["closed"] else [{"name": "reader"}],
                            "waypointLists": native_lists}}
                    if kind == "storage.snapshot":
                        return {"data": {"dimensions": {} if mode == "reopened-state" else dataset}}
                    return {"data": {"pending": 0} if kind == "commands.resources" else {"nodes": []}}

            def boot(runtime, server, *args):
                state["boots"] += 1
                number = state["boots"]
                state["server"] = server
                if number == 2 and mode in ("bootstrap-health", "saved-state"):
                    raise ValueError("injected reopen failure")
                log = server / ("native-" + str(number) + ".log")
                failed = (number == 1 and mode == "bootstrap-health") or (number == 2 and mode == "reopen-health")
                log.write_text("[00:00:00 ERROR]: The server has not responded for 10 seconds!\n" if failed
                    else "[00:00:00 INFO]: native server ready\n")
                return SimpleNamespace(log=log, number=number), Probe(number)

            class Driver:
                def __init__(self, *args):
                    pass
                def send(self, *args):
                    pass
                def wait(self, kind, timeout=60):
                    return {"bots": [{"name": "reader"}]} if kind == "ready" else {"success": True}
                def close(self):
                    state["closed"] = True
                    if mode == "saved-state":
                        (state["server"] / "plugins/ServerWaypoint/waypoints/minecraft$overworld.json").write_text("[]")
                def abort(self):
                    pass

            def stop(process, *args):
                if process.number == 2 and mode == "reopened-state":
                    binary.write_bytes(b"identity changed after known reopened-state failure")

            variant = "add-edit-remove-restore" if mode == "bootstrap-health" else "concurrent"
            case = CaseSpec("checkpoint", runtime.id, "storage", variant, "small", "audit",
                "navigation-terrain", (), (), clients=1, repeats=1)
            replacements = {
                "load_runtimes": lambda *args: [runtime], "load_release": lambda *args: [artifact],
                "verify_evidence": lambda *args: None, "verify_artifact": lambda *args: None,
                "install_server": lambda *args: 25565, "clone_baseline": lambda *args: None,
                "make_dataset": lambda *args, **kwargs: dataset, "boot": boot, "BotDriver": Driver,
                "validate_ready": lambda *args: Decision(Status.PASS), "validate_fixture": lambda *args: Decision(Status.PASS),
                "prepare_command_operator": lambda *args: None, "prepare_remaining_operators": lambda *args: None,
                "seed_mutations": lambda *args: ([], dataset), "stop_owned": stop,
                "collect_commands": lambda *args, **kwargs: {"actions": [], "seconds": 60},
                "summarize_window": lambda *args: {}, "evaluate": lambda *args: Decision(Status.PASS)
            }
            with ExitStack() as stack:
                for name, replacement in replacements.items():
                    stack.enter_context(patch("scenarios.storage_runner." + name, replacement))
                return run_case(case, root)

    def test_bootstrap_watchdog_survives_next_boot_failure(self):
        result = self.simulate_failure("bootstrap-health")
        self.assertIn("injected reopen failure", result.decision.reasons)
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertTrue(any("watchdog" in reason for reason in result.decision.reasons))

    def test_saved_state_failure_survives_reopen_boot_failure(self):
        result = self.simulate_failure("saved-state")
        self.assertIn("injected reopen failure", result.decision.reasons)
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertFalse(result.assertions["repeat-1-saved-exactly"])

    def test_reopen_watchdog_survives_snapshot_failure(self):
        result = self.simulate_failure("reopen-health")
        self.assertIn("injected snapshot failure", result.decision.reasons)
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertTrue(any("watchdog" in reason for reason in result.decision.reasons))

    def test_reopened_state_failure_survives_later_identity_failure(self):
        result = self.simulate_failure("reopened-state")
        self.assertIn("native bot bytes changed during repetition", result.decision.reasons)
        self.assertEqual(Status.FAIL, result.decision.status)
        self.assertFalse(result.assertions["repeat-1-reopened-exactly"])
