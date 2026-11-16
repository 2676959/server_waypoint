import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from models import Status


class CommandTreeDeliveryTest(unittest.TestCase):
    def samples(self):
        server, client = [], []
        for role in ("ordinary", "operator"):
            for sequence, trigger in ((1, "login"), (2, "refresh")):
                allowed = (role == "operator") == (trigger == "login")
                identity = dict(connectionId=role + "-connection", playerUuid=role + "-uuid", role=role,
                                sequence=sequence, trigger=trigger)
                server.append(dict(**identity, clock="server-monotonic", sendDurationNs=1000,
                    dispatchNs=10, asyncWorkNs=500, syncWorkNs=100, packetBodyBytes=200,
                    packetCrc32=123, nodeCount=10, permission=allowed, instrumented=True))
                client.append(dict(**identity, clock="client-monotonic", clientElapsedNs=2000,
                    timingScope="request-to-installed-tree" if sequence == 2 else "connect-to-installed-tree",
                    packetBodyBytes=200, packetCrc32=123, nodeCount=10, installed=True,
                    branches=["help", "add", "edit", "details", "restore", "remove", "download", "remote", "list", "navigate", "private"]
                        + (["upload", "reload"] if role == "operator" else []) + (["tp"] if allowed else [])))
        return server, client

    def judge(self, server, client):
        from scenarios.command_tree import judge_session
        return judge_session(server, client)

    def test_requires_both_roles_and_triggers_with_matching_packet_and_local_clocks(self):
        server, client = self.samples()
        self.assertEqual(Status.PASS, self.judge(server, client).status)
        for side, field, value in (("server", "instrumented", False), ("server", "sendDurationNs", 0),
                ("client", "installed", False), ("client", "clock", "server-monotonic"),
                ("client", "connectionId", "stale"), ("client", "sequence", 4),
                ("client", "packetBodyBytes", 199), ("client", "packetCrc32", 124),
                ("client", "nodeCount", 9), ("client", "clientElapsedNs", -1),
                ("client", "timingScope", "server-to-client-subtraction")):
            with self.subTest(side=side, field=field):
                a, b = copy.deepcopy((server, client))
                (a if side == "server" else b)[0][field] = value
                self.assertEqual(Status.BLOCKED, self.judge(a, b).status)
        self.assertEqual(Status.BLOCKED, self.judge(server, client[:-1]).status)
        self.assertEqual(Status.BLOCKED, self.judge(server, client + [client[0]]).status)

    def test_missing_or_unexpected_waypoint_paths_fail_even_when_tp_is_correct(self):
        for branches in ([], ["help", "unexpected"]):
            server, client = self.samples()
            client[0]["branches"] = branches
            self.assertEqual(Status.FAIL, self.judge(server, client).status)

    def test_observed_permission_failure_survives_another_missing_receipt(self):
        server, client = self.samples()
        client[0]["branches"].append("tp")
        self.assertEqual(Status.FAIL, self.judge(server, client[:-1]).status)

    def test_unchanged_permission_view_fails_grant_and_revocation(self):
        for index in (1, 3):
            server, client = self.samples()
            client[index]["branches"] = client[index-1]["branches"]
            self.assertEqual(Status.FAIL, self.judge(server, client).status)

    def test_summary_requires_three_distinct_completed_processes_and_uses_median_max(self):
        from scenarios.command_tree import summarize_sessions
        server, client = self.samples()
        sessions = [dict(pid=i, server=server, client=client, shutdown=True, ticks=[dict(durationNs=100)]) for i in (11, 12, 13)]
        value = summarize_sessions(sessions)
        self.assertEqual("PASS", value["decision"].status.value)
        self.assertEqual(4, len(value["metrics"]))
        self.assertEqual(1000, value["metrics"][0]["sendDurationNs"]["median"])
        self.assertEqual(1000, value["metrics"][0]["sendDurationNs"]["maximum"])
        sessions[2]["pid"] = 12
        self.assertEqual(Status.BLOCKED, summarize_sessions(sessions)["decision"].status)


class CommandTreeRoutingTest(unittest.TestCase):
    def test_candidate_routing_uses_sealed_binding_instead_of_pending_adapter(self):
        from scenarios import run_case
        from test_minimal_selection import MinimalSelectionTest
        case = next(c for c in MinimalSelectionTest().select() if c.variant == "minimal-command-tree")
        result = run_case(case, Path("/nonexistent/minimal-root"))
        self.assertEqual(Status.BLOCKED, result.decision.status)
        self.assertNotIn("adapter pending", " ".join(result.decision.reasons))


class EmptyCatalogTest(unittest.TestCase):
    def test_empty_check_uses_storage_dimensions_and_waypoints_not_metadata(self):
        from bots.command_tree_smoke import empty_catalog
        self.assertTrue(empty_catalog({"dimensions":{},"origin":"installed-release-property-snapshot"}))
        self.assertTrue(empty_catalog({"dimensions":{"minecraft:overworld":[{"waypoints":[]}]}}))
        self.assertFalse(empty_catalog({"dimensions":{"minecraft:overworld":[{"waypoints":[{"name":"A"}]}]}}))
        self.assertFalse(empty_catalog({"origin":"missing observations"}))


class FailureRetentionTest(unittest.TestCase):
    samples = CommandTreeDeliveryTest.samples
    judge = CommandTreeDeliveryTest.judge
    def test_missing_permission_fields_block_without_inventing_a_product_failure(self):
        server, client = self.samples()
        del server[1]["permission"]
        self.assertEqual(Status.BLOCKED, self.judge(server, client).status)

    def test_duplicate_receipt_does_not_hide_a_permission_failure(self):
        server, client = self.samples()
        client[1]["branches"].remove("tp")
        self.assertEqual(Status.FAIL, self.judge(server, client + [client[1]]).status)

    def test_permission_failure_is_judged_independently_of_the_other_receipt(self):
        server, client = self.samples()
        client[1]["branches"].remove("tp")
        self.assertEqual(Status.FAIL, self.judge(server[:1] + server[2:], client).status)
        server, client = self.samples()
        server[1]["permission"] = False
        self.assertEqual(Status.FAIL, self.judge(server, client[:1] + client[2:]).status)

    def exercise_session_failure(self, failure):
        import io
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import patch
        from contracts import read_document
        from bots import command_tree_smoke as smoke
        native, received = self.samples()
        stage = {"value":1, "serverStopped":False}
        class Probe:
            def send(self, operation, data=None):
                self.operation = operation
            def wait(self, kind, timeout=60):
                if kind == "command-tree.armed":
                    data = {"storage":{"dimensions":{}}}
                elif kind == "players.snapshot":
                    data = {"players":[{"name":"swp_reader_00","uuid":"00000000-0000-0000-0000-000000000001"}]}
                else:
                    if failure == "native" and stage["value"] == 2:
                        raise TimeoutError("native snapshot absent")
                    data = {"samples":native[:stage["value"]],"ticks":[{"durationNs":100}],"errors":[]}
                return {"data":data}
        class Driver:
            def __init__(self, *args):
                self.closed = False
            def send(self, kind, data=None):
                if kind == "action":
                    stage["value"] = 2
            def wait(self, kind, timeout=60):
                if kind == "ready":
                    return {"autoReconnect":False,"bots":[{"name":"swp_reader_00","uuid":"00000000-0000-0000-0000-000000000001","mode":"vanilla","connected":True}]}
                if kind == "action_result" and failure in ("interrupt", "abort"):
                    if failure == "interrupt":
                        raise KeyboardInterrupt()
                    raise TimeoutError("action result absent")
                if kind == "packet_summary":
                    if failure == "native" and stage["value"] == 2:
                        received[1]["branches"].remove("tp")
                    return {"commandTrees":received[:stage["value"]]}
                return {}
            def close(self):
                self.closed = True
                if failure == "close":
                    raise TimeoutError("stop receipt deadline")
            def abort(self):
                if self.closed or failure == "abort":
                    raise ValueError("refusing to stop an unowned process")
        runtime = SimpleNamespace(probe={"path":"probe","sha256":"probe"},bot={"path":"bot","sha256":"bot"})
        artifact = SimpleNamespace(sha256="candidate")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve() / "session"
            process = SimpleNamespace(pid=1,process=SimpleNamespace(stdin=io.BytesIO()),log=root / "server/server.log")
            def stop(*args):
                stage["serverStopped"] = True
                process.log.write_text("All dimensions are saved")
            with patch.object(smoke,"install_server",return_value=12345), patch.object(smoke,"boot",return_value=(process,Probe())), patch.object(smoke,"BotDriver",Driver), patch.object(smoke,"stop_owned",side_effect=stop):
                if failure == "interrupt":
                    with self.assertRaises(KeyboardInterrupt):
                        smoke.run_session(root,(runtime,artifact,Path("candidate")))
                    value = read_document(root / "session.json")
                else:
                    value = smoke.run_session(root,(runtime,artifact,Path("candidate")))
            self.assertTrue(stage["serverStopped"])
            self.assertTrue(value["shutdown"])
            self.assertTrue((root / "session.json").is_file())
            return value

    def test_a_close_timeout_preserves_receipts_and_still_stops_the_owned_server(self):
        value = self.exercise_session_failure("close")
        self.assertEqual(2,len(value["client"]))
        self.assertIn("stop receipt deadline", " ".join(value["errors"]))

    def test_native_timeout_keeps_the_already_received_wrong_refresh_tree(self):
        value = self.exercise_session_failure("native")
        self.assertEqual(2,len(value["client"]))
        self.assertEqual(Status.FAIL, self.judge(value["server"],value["client"]).status)
        self.assertIn("native snapshot absent", " ".join(value["errors"]))

    def test_interrupt_retains_partial_evidence_then_propagates_after_shutdown(self):
        value = self.exercise_session_failure("interrupt")
        self.assertEqual(1,len(value["client"]))
        self.assertTrue(any("KeyboardInterrupt" in reason for reason in value["errors"]))

    def test_bot_abort_failure_still_stops_server_and_saves_evidence(self):
        value = self.exercise_session_failure("abort")
        self.assertTrue(any("bot cleanup" in reason for reason in value["errors"]))
