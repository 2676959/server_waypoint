import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from models import Status


def _server():
    return {
        "authority": "server",
        "kind": "full",
        "revision": 7,
        "baseRevision": 0,
        "dimension": "minecraft:overworld",
        "list": "perf",
        "waypoints": ["alpha", "origin"],
        "worldId": "world-a",
        "channel": "server_waypoint:message_chunk",
    }


def _client(**updates):
    value = {
        "authority": "client",
        "mode": "compatible",
        "kind": "full",
        "revision": 7,
        "baseRevision": 0,
        "dimension": "minecraft:overworld",
        "list": "perf",
        "waypoints": ["alpha", "origin"],
        "worldId": "world-a",
        "channel": "server_waypoint:message_chunk",
        "delivered": True,
    }
    value.update(updates)
    return value


def _store(revision=4, waypoints=None):
    return {"revision": revision, "waypoints": ["origin"] if waypoints is None else list(waypoints)}


class SyncUploadTest(unittest.TestCase):
    def test_download_matches_exact_revision(self):
        from scenarios.sync_upload import download_revision

        server = _server()
        client = _client()
        self.assertEqual(Status.PASS, download_revision(server, client).status)
        self.assertEqual(Status.PASS, download_revision(server, client, forced=True).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(revision=6)).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(waypoints=["alpha"])).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(delivered=False), forced=True).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(revision=8), forced=True).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(worldId="world-b"), forced=True).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(channel="minecraft:brand")).status)
        self.assertEqual(Status.BLOCKED, download_revision(None, client).status)
        self.assertEqual(Status.BLOCKED, download_revision({**server, "revision": None}, client).status)
        self.assertEqual(Status.FAIL, download_revision(server, _client(mode="incompatible")).status)
        denied = {
            "authority": "client",
            "mode": "incompatible",
            "kind": "full",
            "revision": None,
            "baseRevision": None,
            "dimension": "minecraft:overworld",
            "list": "perf",
            "waypoints": [],
            "worldId": "world-a",
            "channel": None,
            "delivered": False,
        }
        self.assertEqual(Status.PASS, download_revision(server, denied).status)
        self.assertEqual(Status.PASS, download_revision(server, {**denied, "mode": "vanilla"}).status)
        self.assertEqual(Status.FAIL, download_revision(server, {**denied, "mode": "vanilla", "delivered": True, "revision": 7, "waypoints": ["alpha", "origin"]}).status)
        incremental = {**server, "kind": "incremental", "baseRevision": 6}
        self.assertEqual(Status.PASS, download_revision(incremental, _client(kind="incremental", baseRevision=6)).status)
        self.assertEqual(Status.FAIL, download_revision(incremental, _client(kind="incremental", baseRevision=7)).status)
        self.assertEqual(Status.FAIL, download_revision(incremental, _client(kind="full", baseRevision=0)).status)
        self.assertEqual(Status.BLOCKED, download_revision({**server, "kind": None}, client).status)

    def test_invalid_upload_never_commits(self):
        from scenarios.sync_upload import upload_commit

        before = {**_store(), "leaseOwner": None, "retainedBytes": 0, "buffers": 0}
        for reason in ("checksum", "oversize", "malformed", "header", "sequence", "timeout", "cancel", "permission"):
            attempt = {"accepted": False, "reason": reason, "after": dict(before)}
            self.assertEqual(Status.PASS, upload_commit(before, attempt).status, reason)
            committed = {**before, "revision": 5, "waypoints": ["origin", "smuggled"]}
            self.assertEqual(Status.FAIL, upload_commit(before, {"accepted": False, "reason": reason, "after": committed}).status, reason)
        leaked = {**before, "leaseOwner": "alice", "retainedBytes": 64, "buffers": 1}
        self.assertEqual(Status.FAIL, upload_commit(before, {"accepted": False, "reason": "checksum", "after": leaked}).status)
        self.assertEqual(Status.FAIL, upload_commit(before, {"accepted": True, "reason": "checksum", "after": dict(before)}).status)
        self.assertEqual(Status.BLOCKED, upload_commit(before, {"accepted": False, "reason": "checksum"}).status)
        self.assertEqual(Status.FAIL, upload_commit(before, {"accepted": False, "reason": "other", "after": dict(before)}).status)

    def test_partial_disconnect_releases_lease(self):
        from scenarios.sync_upload import disconnect_lease

        committed = _store()
        during = {"leaseOwner": "alice", "retainedBytes": 1000, "buffers": 2, "partialWaypoints": ["origin", "partial"]}
        after = {
            "leaseOwner": None,
            "retainedBytes": 0,
            "buffers": 0,
            "revision": 4,
            "waypoints": ["origin"],
            "nextOwner": "bob",
            "nextAcquired": True,
        }
        self.assertEqual(Status.PASS, disconnect_lease(committed, during, after, "alice").status)
        smuggled = {**after, "waypoints": ["origin", "partial"], "revision": 5}
        self.assertEqual(Status.FAIL, disconnect_lease(committed, during, smuggled, "alice").status)
        held = {**after, "leaseOwner": "alice", "retainedBytes": 1000, "buffers": 2, "nextAcquired": False}
        self.assertEqual(Status.FAIL, disconnect_lease(committed, during, held, "alice").status)
        stranger = {**after, "leaseOwner": "alice", "retainedBytes": 1000, "buffers": 2, "nextOwner": None, "nextAcquired": False}
        self.assertEqual(Status.PASS, disconnect_lease(committed, during, stranger, "mallory").status)
        stolen = {**after, "leaseOwner": None, "nextAcquired": True}
        self.assertEqual(Status.FAIL, disconnect_lease(committed, during, stolen, "mallory").status)
        self.assertEqual(Status.BLOCKED, disconnect_lease(committed, {"leaseOwner": "alice"}, after, "alice").status)
        self.assertEqual(Status.FAIL, disconnect_lease(committed, during, {**after, "nextAcquired": False}, "alice").status)

    def test_backpressure_recovers(self):
        from scenarios.sync_upload import backpressure

        held = {"phase": "held", "owner": "alice", "accepted": True, "committed": False, "revision": 4, "leaseOwner": "alice", "retainedBytes": 128, "buffers": 1, "waypointCount": 1000}
        busy = {"phase": "rejected", "owner": "bob", "reason": "busy", "accepted": False, "committed": False, "revision": 4, "waypointCount": 1000}
        limited = {"phase": "rejected", "owner": "alice", "reason": "byte-limit", "accepted": False, "committed": False, "revision": 4, "waypointCount": 4097}
        released = {"phase": "released", "owner": "alice", "accepted": False, "committed": False, "revision": 4, "leaseOwner": None, "retainedBytes": 0, "buffers": 0, "cooldownElapsed": False}
        recovered = {"phase": "recovered", "owner": "bob", "reason": "fulfilled", "accepted": True, "committed": True, "revision": 5, "leaseOwner": None, "retainedBytes": 0, "buffers": 0, "cooldownElapsed": True, "waypointCount": 1000}
        trace = [held, busy, limited, released, recovered]
        self.assertEqual(Status.PASS, backpressure(trace).status)
        self.assertEqual(Status.FAIL, backpressure([held, {**busy, "committed": True}, limited, released, recovered]).status)
        self.assertEqual(Status.FAIL, backpressure([held, busy, {**limited, "accepted": True}, released, recovered]).status)
        self.assertEqual(Status.FAIL, backpressure([held, busy, limited, released, {**recovered, "revision": 4}]).status)
        self.assertEqual(Status.FAIL, backpressure([held, busy, limited, released, {**recovered, "cooldownElapsed": False}]).status)
        self.assertEqual(Status.FAIL, backpressure([held, busy, limited, recovered]).status)
        self.assertEqual(Status.FAIL, backpressure([held, busy, limited, released, {**recovered, "buffers": 1}]).status)
        self.assertEqual(Status.PASS, backpressure([held, busy, limited, released]).status)
        self.assertEqual(Status.BLOCKED, backpressure([held, {**busy, "reason": None}, limited, released, recovered]).status)

    def test_unrelated_chat_does_not_mutate(self):
        from scenarios.sync_upload import chat_effect

        before = _store()
        changed = {"revision": 5, "waypoints": ["origin", "from-chat"]}
        self.assertEqual(Status.PASS, chat_effect(before, {"kind": "unrelated", "text": "hello world"}, dict(before)).status)
        self.assertEqual(Status.FAIL, chat_effect(before, {"kind": "unrelated", "text": "hello world"}, changed).status)
        for kind in ("invalid", "long", "duplicate", "denied"):
            message = {"kind": kind, "text": "xaero-share", "prompt": False}
            self.assertEqual(Status.PASS, chat_effect(before, message, dict(before)).status, kind)
            self.assertEqual(Status.FAIL, chat_effect(before, message, changed).status, kind)
        valid = {"kind": "valid", "text": "xaero-share", "prompt": True, "permission": "add"}
        self.assertEqual(Status.PASS, chat_effect(before, valid, dict(before)).status)
        self.assertEqual(Status.FAIL, chat_effect(before, valid, changed).status)
        self.assertEqual(Status.FAIL, chat_effect(before, {"kind": "valid", "text": "xaero-share", "prompt": False, "permission": "add"}, dict(before)).status)
        self.assertEqual(Status.BLOCKED, chat_effect(before, {"kind": "unrelated"}, dict(before)).status)
        self.assertEqual(Status.BLOCKED, chat_effect(before, {"kind": "unrelated", "text": "hello"}, {}).status)

    def test_upload_schedule_stays_inside_one_request_limit(self):
        from scenarios.sync_upload import request_admission, upload_schedule

        self.assertEqual({"clients": 1, "waypoints": 1000, "requests": 1, "perRequest": 1000}, upload_schedule("small"))
        self.assertEqual({"clients": 2, "waypoints": 4096, "requests": 1, "perRequest": 4096}, upload_schedule("medium"))
        self.assertEqual({"clients": 8, "waypoints": 16384, "requests": 4, "perRequest": 4096}, upload_schedule("stress"))
        with self.assertRaises(ValueError):
            upload_schedule("huge")
        self.assertEqual({"outcome": "accept", "reason": "fulfilled", "waypoints": 4096}, request_admission(4096))
        self.assertEqual({"outcome": "reject", "reason": "oversize", "waypoints": 4097}, request_admission(4097))
        self.assertEqual({"outcome": "reject", "reason": "oversize", "waypoints": 16384}, request_admission(16384))

    def test_compression_flag_does_not_change_the_revision(self):
        from scenarios.sync_upload import compressed_download

        server = _server()
        self.assertEqual(Status.PASS, compressed_download(server, _client(compressed=False), False).status)
        self.assertEqual(Status.PASS, compressed_download(server, _client(compressed=True), True).status)
        self.assertEqual(Status.FAIL, compressed_download(server, _client(compressed=True, revision=8), True).status)
        self.assertEqual(Status.FAIL, compressed_download(server, _client(compressed=False), True).status)

    def test_merge_conflict_and_delete_follow_policy(self):
        from scenarios.sync_upload import merge_outcome

        before = {"revision": 4, "waypoints": {"origin": {"x": 0}, "kept": {"x": 1}}}
        uploaded = {"origin": {"x": 5}, "added": {"x": 2}}
        replaced = {"revision": 5, "waypoints": {"origin": {"x": 5}, "added": {"x": 2}}}
        kept = {"revision": 5, "waypoints": {"origin": {"x": 5}, "kept": {"x": 1}, "added": {"x": 2}}}
        server = {"revision": 5, "waypoints": {"origin": {"x": 0}, "kept": {"x": 1}, "added": {"x": 2}}}
        self.assertEqual(Status.PASS, merge_outcome(before, uploaded, "LOCAL", True, replaced, stale=False).status)
        self.assertEqual(Status.PASS, merge_outcome(before, uploaded, "LOCAL", False, kept, stale=False).status)
        self.assertEqual(Status.FAIL, merge_outcome(before, uploaded, "LOCAL", False, replaced, stale=False).status)
        self.assertEqual(Status.PASS, merge_outcome(before, uploaded, "SERVER", False, server, stale=False).status)
        self.assertEqual(Status.FAIL, merge_outcome(before, uploaded, "SERVER", True, replaced, stale=False).status)
        self.assertEqual(Status.PASS, merge_outcome(before, uploaded, "SERVER", True, before, stale=False).status)
        self.assertEqual(Status.PASS, merge_outcome(before, uploaded, "LOCAL", True, before, stale=True).status)
        self.assertEqual(Status.FAIL, merge_outcome(before, uploaded, "LOCAL", True, replaced, stale=True).status)

    def test_broadcast_reaches_only_the_same_world(self):
        from scenarios.sync_upload import broadcast

        update = {"revision": 8, "worldId": "world-a", "channel": "server_waypoint:message_chunk", "waypoints": ["alpha", "origin"]}
        same = {"mode": "compatible", "worldId": "world-a", "channel": "server_waypoint:message_chunk", "revision": 8, "waypoints": ["alpha", "origin"], "delivered": True}
        other = {"mode": "compatible", "worldId": "world-b", "channel": "server_waypoint:message_chunk", "revision": None, "waypoints": [], "delivered": False}
        denied = {"mode": "incompatible", "worldId": "world-a", "channel": None, "revision": None, "waypoints": [], "delivered": False}
        self.assertEqual(Status.PASS, broadcast(update, [same, other, denied]).status)
        self.assertEqual(Status.FAIL, broadcast(update, [same, {**other, "delivered": True, "revision": 8, "waypoints": ["alpha", "origin"]}]).status)
        self.assertEqual(Status.FAIL, broadcast(update, [{**same, "delivered": False}, other, denied]).status)
        self.assertEqual(Status.BLOCKED, broadcast(update, []).status)

    def test_reconnect_keeps_channel_and_drops_the_old_world(self):
        from scenarios.sync_upload import reconnect_identity

        same = {"worldId": "world-a", "channel": "server_waypoint:message_chunk", "revision": 7, "leaseOwner": None, "buffers": 0, "retainedBytes": 0, "delivered": True}
        self.assertEqual(Status.PASS, reconnect_identity(same, dict(same)).status)
        self.assertEqual(Status.FAIL, reconnect_identity(same, {**same, "buffers": 1}).status)
        self.assertEqual(Status.FAIL, reconnect_identity(same, {**same, "channel": "minecraft:brand"}).status)
        self.assertEqual(Status.FAIL, reconnect_identity(same, {**same, "worldId": "world-b"}).status)
        moved = {**same, "worldId": "world-b", "revision": None, "delivered": False}
        self.assertEqual(Status.PASS, reconnect_identity(same, moved).status)

    def test_sharing_messages_and_icons_follow_server_rules(self):
        from scenarios.sync_upload import chat_effect, classify_share, icon_registered

        before = _store()
        valid = "xaero-waypoint:Home:H:0:64:0:0:false:0:Internal-overworld-waypoints"
        first = classify_share(valid, permission="add", seen=())
        self.assertEqual({"kind": "valid", "text": valid, "prompt": True, "permission": "add", "name": "Home", "dimension": "minecraft:overworld"}, first)
        self.assertEqual(Status.PASS, chat_effect(before, first, before).status)
        duplicate = classify_share(valid, permission="add", seen=(("Home", "minecraft:overworld"),))
        self.assertEqual("duplicate", duplicate["kind"])
        self.assertIs(False, duplicate["prompt"])
        self.assertEqual(Status.PASS, chat_effect(before, duplicate, before).status)
        denied = classify_share(valid, permission="none", seen=())
        self.assertEqual("denied", denied["kind"])
        self.assertIs(False, denied["prompt"])
        self.assertEqual("invalid", classify_share("xaero-waypoint:Home:H", permission="add", seen=())["kind"])
        self.assertEqual("invalid", classify_share("xaero-waypoint:Home:H:x:64:0:0:false:0:Internal-overworld-waypoints", permission="add", seen=())["kind"])
        self.assertEqual("unrelated", classify_share("hello world", permission="add", seen=())["kind"])
        self.assertEqual("long", classify_share("y" * 257, permission="add", seen=())["kind"])
        nether = classify_share("xaero-waypoint:Fortress:F:0:64:0:0:false:0:Internal-the-nether-waypoints", permission="add", seen=())
        self.assertEqual("minecraft:the_nether", nether["dimension"])
        self.assertIs(True, icon_registered(None, ()))
        self.assertIs(True, icon_registered("voxelmap:star", ()))
        self.assertIs(True, icon_registered("voxelmap:waypoint", ()))
        self.assertIs(False, icon_registered("voxelmap:nope", ()))
        self.assertIs(True, icon_registered("minecraft:diamond", ("minecraft:diamond",)))
        self.assertIs(False, icon_registered("minecraft:diamond", ()))
        self.assertIs(False, icon_registered("minecraft:" + ("a" * 250), ("minecraft:" + ("a" * 250),)))
        self.assertIs(False, icon_registered("no-colon", ()))

    def test_live_case_does_not_use_the_inventory_adapter(self):
        from models import CaseResult, CaseSpec
        from scenarios import run_case

        for feature in ("sync", "upload-edit", "sharing-icons"):
            case = CaseSpec(
                f"paper-1.21.11:{feature}:pending:small",
                "paper-1.21.11",
                feature,
                "pending",
                "small",
                "audit",
                "navigation-terrain",
                ("action-latency",),
                ("backend-medium",),
                clients=1,
            )
            result = run_case(case, "/tmp/sw-perf-sync-not-prepared")
            self.assertIsInstance(result, CaseResult)
            self.assertEqual(Status.BLOCKED, result.decision.status)
            self.assertIn("sync/upload/sharing", result.decision.reasons[0])
