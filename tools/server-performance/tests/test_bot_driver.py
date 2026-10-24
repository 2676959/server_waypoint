import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bot_driver import BotStream, validate_ready, validate_rates, validate_actions
from models import Status


class BotDriverTest(unittest.TestCase):
    def ready(self):
        return {"bots": [{"name": "swp_reader_00", "uuid": "00000000-0000-0000-0000-000000000001", "mode": "vanilla", "connected": True}], "autoReconnect": False}

    def test_ready_requires_server_online_count(self):
        ready = self.ready()
        self.assertEqual(Status.BLOCKED, validate_ready(ready, [], 1, "vanilla").status)
        self.assertEqual(Status.PASS, validate_ready(ready, ready["bots"], 1, "vanilla").status)

    def test_stalled_driver_cannot_pass(self):
        self.assertEqual(Status.INCONCLUSIVE, validate_rates({"offered": 50, "completed": 0, "pending": 50, "elapsedSeconds": 1, "maxLagSeconds": 60}, 5).status)

    def test_reconnect_does_not_hide_disconnect(self):
        stream = BotStream("run", "attempt")
        stream.parse({"schemaVersion": 1, "runId": "run", "attemptId": "attempt", "sequence": 1, "type": "error", "data": {"reason": "disconnected"}})
        stream.parse({"schemaVersion": 1, "runId": "run", "attemptId": "attempt", "sequence": 2, "type": "ready", "data": self.ready()})
        self.assertEqual(["disconnected"], stream.failures)

    def test_duplicate_and_wrong_identity_rejected(self):
        stream = BotStream("run", "attempt")
        value = {"schemaVersion": 1, "runId": "run", "attemptId": "attempt", "sequence": 1, "type": "stopped", "data": {}}
        stream.parse(value)
        with self.assertRaises(ValueError):
            stream.parse(value)
        with self.assertRaises(ValueError):
            BotStream("other", "attempt").parse(value)

    def test_readiness_requires_exact_unique_identities(self):
        ready = self.ready()
        self.assertEqual(Status.BLOCKED, validate_ready(ready, ready["bots"] * 2, 2, "vanilla").status)
        ready["autoReconnect"] = True
        self.assertEqual(Status.BLOCKED, validate_ready(ready, ready["bots"], 1, "vanilla").status)

    def test_client_issued_action_requires_native_observation(self):
        expected = [{"actionId": "move", "bot": "swp_reader_00", "operation": "walk"}]
        self.assertEqual(Status.BLOCKED, validate_actions(expected, [{"actionId": "move", "completionScope": "client-issued"}], {}).status)
        evidence = {"swp_reader_00": {"movementEvents": 12, "inventoryEvents": 0, "commandEvents": 0}}
        self.assertEqual(Status.PASS, validate_actions(expected, [{"actionId": "move", "bot": "swp_reader_00"}], evidence).status)

    def test_owned_bot_stderr_does_not_corrupt_json_stream(self):
        import tempfile
        from controller import create_root
        from processes import start_owned, stop_owned
        with tempfile.TemporaryDirectory() as directory:
            root = create_root(Path(directory).resolve() / "owned")
            process = start_owned([sys.executable, "-c", "import sys; print('diagnostic', file=sys.stderr); print('{}')"], root, root / "bot.jsonl", stderr_log=root / "bot.stderr")
            try:
                process.process.wait(timeout=5)
                self.assertEqual("{}\n", process.log.read_text())
                self.assertEqual("diagnostic\n", (root / "bot.stderr").read_text())
            finally:
                stop_owned(process, 1)

    def test_successful_zero_demand_cannot_prove_driver_capacity(self):
        self.assertEqual(Status.INCONCLUSIVE, validate_rates({"offered": 0, "completed": 0, "pending": 0, "elapsedSeconds": 1, "maxLagSeconds": 0}, 5).status)
