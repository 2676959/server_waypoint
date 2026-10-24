import json
import os
import signal
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from controller import create_root, Attempt, available_port
from processes import start_owned, stop_owned
from probe_driver import ProbeStream

CHILD = Path(__file__).parent / "fixtures/child_server.py"


class LifecycleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()

    def test_existing_or_symlinked_root_refused(self):
        with self.assertRaises(ValueError):
            create_root(self.base)
        alias = self.base / "alias"
        alias.symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(ValueError):
            create_root(alias / "new")
        from controller import REPOSITORY, reject_aliases
        with self.assertRaises(ValueError):
            reject_aliases(REPOSITORY.parent / ".." / REPOSITORY.parent.name / REPOSITORY.name / "new")

    def test_only_owned_process_group_stopped(self):
        sentinel = subprocess.Popen([sys.executable, str(CHILD)], stdin=subprocess.PIPE, stdout=subprocess.DEVNULL)
        self.addCleanup(lambda: (sentinel.terminate(), sentinel.wait(timeout=5), sentinel.stdin.close()) if sentinel.poll() is None else None)
        root = create_root(self.base / "owned")
        owned = start_owned([sys.executable, str(CHILD)], root, root / "server.log")
        stop_owned(owned, 1)
        self.assertIsNotNone(owned.process.poll())
        self.assertIsNone(sentinel.poll())
        self.assertTrue((root / "server.log").exists())

    def test_partial_report_survives_interrupt(self):
        for state in ("STARTING", "MEASURING"):
            root = create_root(self.base / state)
            attempt = Attempt(root, "case")
            with self.assertRaises(KeyboardInterrupt):
                with attempt:
                    for stage in ("STARTING", "READY", "WARMUP", "MEASURING"):
                        attempt.transition(stage)
                        if stage == state:
                            raise KeyboardInterrupt()
            document = json.loads(attempt.path.read_text())
            self.assertEqual("INTERRUPTED", document["state"])
            self.assertEqual(130, document["exit_code"])
            self.assertEqual(state, document["last_completed"])

    def test_stale_probe_reply_rejected(self):
        stream = ProbeStream("run", "attempt")
        message = {"schemaVersion": 1, "runId": "run", "attemptId": "attempt", "sequence": 1, "type": "hello"}
        stream.parse("SW_PERF " + json.dumps(message))
        with self.assertRaises(ValueError):
            stream.parse("SW_PERF " + json.dumps(message))
        message.update(attemptId="old", sequence=2)
        with self.assertRaises(ValueError):
            stream.parse("SW_PERF " + json.dumps(message))

    def test_port_collision_reported(self):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            with self.assertRaises(OSError):
                available_port(listener.getsockname()[1])

    def test_invalid_transition_rejected(self):
        attempt = Attempt(create_root(self.base / "owned"), "case")
        with self.assertRaises(ValueError):
            attempt.transition("MEASURING")

    def test_owned_descendant_cannot_survive_parent_stop(self):
        root = create_root(self.base / "owned")
        pid_file = root / "descendant.pid"
        process = start_owned([sys.executable, str(CHILD), str(pid_file)], root, root / "server.log")
        deadline = time.monotonic() + 3
        while not pid_file.exists() and time.monotonic() < deadline:
            time.sleep(0.01)
        pid = int(pid_file.read_text())
        time.sleep(0.1)
        try:
            stop_owned(process, 0.2)
            time.sleep(0.1)
            with self.assertRaises(ProcessLookupError):
                os.kill(pid, 0)
        finally:
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
