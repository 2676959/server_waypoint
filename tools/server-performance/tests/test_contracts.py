import dataclasses
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import contracts
from models import CaseResult, Decision, MetricSample, Status
from registry import expand_cases, validate_entrypoints, coverage_decision

CONFIG = Path(__file__).resolve().parents[1] / "config"


class ContractsTest(unittest.TestCase):
    def test_missing_applicable_case_blocks(self):
        self.assertEqual(Status.BLOCKED, coverage_decision(("a", "b"), {"a": Status.PASS}).status)

    def test_private_is_not_applicable_only_for_release(self):
        audit = expand_cases(CONFIG / "features.json", CONFIG / "scenarios.json", (), "audit", ("small",))
        candidate = expand_cases(CONFIG / "features.json", CONFIG / "scenarios.json", (), "candidate", ("small",))
        self.assertTrue(any(c.feature == "private-development" and c.applicability_reason for c in audit))
        self.assertTrue(all(not c.applicability_reason for c in candidate if c.feature == "private-development"))
        with self.assertRaises(ValueError):
            Decision(Status.NOT_APPLICABLE, ())

    def test_unknown_schema_rejected(self):
        with self.assertRaises(ValueError):
            contracts.validate_document({"schemaVersion": 2})

    def test_nonfinite_samples_rejected(self):
        for value in (math.nan, math.inf, -math.inf, -1):
            with self.subTest(value=value), self.assertRaises(ValueError):
                MetricSample("tick", value, "ns", 1)

    def test_unmapped_entrypoint_rejected(self):
        with self.assertRaises(ValueError):
            validate_entrypoints([{"id": "command:x", "feature": "missing"}], {"commands"})
        with self.assertRaises(ValueError):
            validate_entrypoints([{"id": "command:x"}], {"commands"})

    def test_inventory_is_deterministic_and_complete(self):
        a = expand_cases(CONFIG / "features.json", CONFIG / "scenarios.json", (), "audit", ("small", "medium"))
        b = expand_cases(CONFIG / "features.json", CONFIG / "scenarios.json", (), "audit", ("medium", "small"))
        self.assertEqual(a, b)
        self.assertEqual(16, len({c.feature for c in a}))
        self.assertEqual(len(a), len({c.id for c in a}))
        self.assertTrue({"container-stone", "container-meta", "container-shulker-books", "container-bundle"} <= {c.variant for c in a})

    def test_unsafe_path_and_nan_json_rejected(self):
        for value in ("../escape.jar", "/tmp/escape.jar", "sub/asset.jar"):
            with self.assertRaises(ValueError):
                contracts.safe_name(value)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text('{"schemaVersion":1,"value":NaN}')
            with self.assertRaises(ValueError):
                contracts.read_document(path)

    def test_serialization_has_schema_and_no_nonfinite(self):
        value = contracts.to_json(Decision(Status.BLOCKED, ("missing result",)))
        self.assertEqual(1, json.loads(value)["schemaVersion"])
        self.assertEqual("BLOCKED", json.loads(value)["status"])


if __name__ == "__main__":
    unittest.main()
