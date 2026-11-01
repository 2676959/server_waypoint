import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bot_driver import BotStream, validate_ready, validate_rates, validate_actions
from models import Status


class BotDriverTest(unittest.TestCase):
    def test_effects_scope_cannot_silently_include_full_wire_history(self):
        from bot_driver import effects_snapshot
        effects={"alice":{"chat":[{"sequence":1,"text":"delivered"}]}}
        self.assertEqual(effects,effects_snapshot({"scope":"effects","peers":[],"effects":effects}))
        for invalid in ({"scope":"full","peers":[],"effects":effects},{"scope":"effects","peers":[{}],"effects":effects},{"scope":"effects","peers":[],"effects":[]}):
            with self.assertRaises(ValueError):effects_snapshot(invalid)

    def test_recent_history_is_bounded_and_replaces_old_packet_snapshots(self):
        import tempfile
        from bot_driver import BotDriver
        from controller import create_root
        from processes import stop_owned
        base={"schemaVersion":1,"runId":"run","attemptId":"attempt"}
        messages=[{**base,"sequence":i+1,"type":"rate_sample","data":{"offered":i,"completed":i,"failed":0,"pending":0,"elapsedSeconds":i+1,"maxLagSeconds":0,"barrierAcknowledged":True,"completionScope":"client-issued"}} for i in range(1400)]
        messages.append({**base,"sequence":1401,"type":"packet_summary","data":{"scope":"full","peers":[{"packets":[{"bodyHex":"old-wire-body"}]}],"effects":{}}})
        messages.append({**base,"sequence":1402,"type":"packet_summary","data":{"scope":"effects","peers":[],"effects":{}}})
        with tempfile.TemporaryDirectory() as directory:
            root=create_root(Path(directory).resolve()/"owned")
            driver=BotDriver(self.child(root,messages),root,"run","attempt")
            try:
                driver.process.process.wait(timeout=5)
                self.assertEqual(1402,len(driver.poll()))
                self.assertLessEqual(len(driver.events),1024)
                summaries=[e for e in driver.events if e["type"]=="packet_summary"]
                self.assertEqual(1,len(summaries))
                self.assertEqual("effects",summaries[0]["data"]["scope"])
                self.assertEqual(1402,len(driver.process.log.read_text().splitlines()))
            finally: stop_owned(driver.process,1)

    def test_full_queue_is_distinct_from_duplicate_action_identity(self):
        from bot_driver import BotCapacityError
        stream=BotStream("run","attempt")
        for index in range(256): stream.offer(str(index))
        with self.assertRaises(BotCapacityError): stream.offer("next")
        with self.assertRaises(ValueError) as failure: stream.offer("0")
        self.assertNotIsInstance(failure.exception,BotCapacityError)

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

    def test_server_handshake_reply_alone_does_not_prove_compatibility(self):
        ready = self.ready()
        ready["bots"][0].update(mode="compatible", protocolAccepted=True)
        server = [{**ready["bots"][0], "protocolAccepted": False}]
        self.assertEqual(Status.BLOCKED, validate_ready(ready, server, 1, "compatible").status)

    def child(self, root, messages, sleeping=False):
        import json
        script = root / "child"
        script.write_text("#!" + sys.executable + "\nimport sys,time\nsys.stdout.write(" + repr("".join(json.dumps(message) + "\n" for message in messages)) + ")\nsys.stdout.flush()\n" + ("time.sleep(30)\n" if sleeping else ""))
        script.chmod(0o700)
        return script

    def test_error_after_matching_event_is_not_lost(self):
        import tempfile
        from bot_driver import BotDriver
        from controller import create_root
        from processes import stop_owned
        with tempfile.TemporaryDirectory() as directory:
            root = create_root(Path(directory).resolve() / "owned")
            base = {"schemaVersion": 1, "runId": "run", "attemptId": "attempt"}
            messages = [{**base, "sequence": 1, "type": "ready", "data": {}}, {**base, "sequence": 2, "type": "error", "data": {"reason": "disconnected"}}]
            driver = BotDriver(self.child(root, messages), root, "run", "attempt")
            driver.process.process.wait(timeout=5)
            try:
                with self.assertRaisesRegex(RuntimeError, "disconnected"):
                    driver.wait("ready")
            finally:
                stop_owned(driver.process, 1)

    def test_unmatched_replies_remain_available(self):
        import tempfile
        from bot_driver import BotDriver
        from controller import create_root
        from processes import stop_owned
        with tempfile.TemporaryDirectory() as directory:
            root = create_root(Path(directory).resolve() / "owned")
            base = {"schemaVersion": 1, "runId": "run", "attemptId": "attempt"}
            messages = [{**base, "sequence": 1, "type": "ready", "data": {}}, {**base, "sequence": 2, "type": "rate_sample", "data": {"completed": 5}}]
            driver = BotDriver(self.child(root, messages), root, "run", "attempt")
            driver.process.process.wait(timeout=5)
            try:
                driver.wait("ready")
                self.assertEqual(5, driver.wait("rate_sample")["completed"])
            finally:
                stop_owned(driver.process, 1)

    def test_stalled_stdin_has_deadline_and_owned_cleanup(self):
        import tempfile
        import time
        from bot_driver import BotDriver
        from controller import create_root
        with tempfile.TemporaryDirectory() as directory:
            root = create_root(Path(directory).resolve() / "owned")
            driver = BotDriver(self.child(root, [], sleeping=True), root, "run", "attempt")
            started = time.monotonic()
            try:
                with self.assertRaises(TimeoutError):
                    for _ in range(100):
                        driver.send("start", {"padding": "x" * 60000}, timeout=0.1)
            finally:
                driver.abort()
            self.assertLess(time.monotonic() - started, 5)
            self.assertIsNotNone(driver.process.process.poll())

    def test_failed_actions_cannot_hide_in_completed_totals(self):
        self.assertEqual(Status.INCONCLUSIVE, validate_rates({"offered": 10, "completed": 10, "failed": 1, "pending": 0, "elapsedSeconds": 1, "maxLagSeconds": 0}, 5).status)
