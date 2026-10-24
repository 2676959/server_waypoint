import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from worlds import validate_hello, validate_fixture
from models import Status
from probe_driver import ProbeStream


class ProbeContractTest(unittest.TestCase):
    def hello(self):
        return {"capabilities": ["fixture", "tick-processing", "zero-player-ticking"], "tickMetric": "native-processing-duration", "auditedJar": "a" * 64}

    def test_wrong_run_or_sequence_rejected(self):
        import json
        value = {"schemaVersion": 1, "runId": "wrong", "attemptId": "a", "sequence": 1, "type": "hello"}
        with self.assertRaises(ValueError):
            ProbeStream("r", "a").parse("SW_PERF " + json.dumps(value))

    def test_readiness_requires_native_fixture_verification(self):
        self.assertEqual(Status.BLOCKED, validate_fixture({"placed": True, "verified": False}).status)
        self.assertEqual(Status.PASS, validate_fixture({"verified": True, "reopened": True, "conserved": True}).status)

    def test_callback_interval_is_not_tick_duration(self):
        value = self.hello()
        value["tickMetric"] = "callback-spacing"
        self.assertEqual(Status.BLOCKED, validate_hello(value, False, "a" * 64).status)

    def test_missing_timing_capability_blocks(self):
        value = self.hello()
        value["capabilities"].remove("tick-processing")
        self.assertEqual(Status.BLOCKED, validate_hello(value, False, "a" * 64).status)

    def test_probe_boots_without_sw_in_declared_control(self):
        value = self.hello()
        value["auditedJar"] = None
        self.assertEqual(Status.PASS, validate_hello(value, True, "").status)

    def test_candidate_cannot_omit_sw(self):
        value = self.hello()
        value["auditedJar"] = None
        self.assertEqual(Status.BLOCKED, validate_hello(value, False, "a" * 64).status)
