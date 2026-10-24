import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from contracts import atomic_write
from preparation import seal_evidence, verify_evidence


class PreparationTest(unittest.TestCase):
    def test_changed_fixture_or_copied_config_is_rejected(self):
        for changed in ("fixtures/test/fixture/manifest.json", "budgets.json", "runtime-lock.json", "selection.json"):
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as directory:
                root = Path(directory).resolve()
                for name in ("budgets.json", "runtime-lock.json", "selection.json", "release.json", "fixtures/test/baseline.json", "fixtures/test/fixture/manifest.json"):
                    atomic_write(root / name, {"schemaVersion": 1, "value": "original"})
                worlds = {"test": str(root / "fixtures/test")}
                evidence = seal_evidence(root, worlds)
                verify_evidence(root, {"evidence": evidence})
                atomic_write(root / changed, {"schemaVersion": 1, "value": "changed"})
                with self.assertRaises(ValueError):
                    verify_evidence(root, {"evidence": evidence})
