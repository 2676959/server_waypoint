import sys
import unittest
import tempfile
from pathlib import Path
from zipfile import ZipFile
from hashlib import sha256
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from wire_fixtures import verify_wire_fixture


class WireFixturesTest(unittest.TestCase):
    def test_exporter_rejects_wrong_production_class_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "release.jar"
            with ZipFile(artifact, "w") as jar:
                for index in range(8):
                    jar.writestr(f"codec{index}.class", b"release-codec")
            doc = {"schemaVersion": 1, "protocolVersion": 2, "artifactSha256": sha256(artifact.read_bytes()).hexdigest(), "productionClasses": {f"codec{index}": "0" * 64 for index in range(8)}, "vectors": [{"name": "handshake"}]}
            with self.assertRaisesRegex(ValueError, "wrong production class hash"):
                verify_wire_fixture(doc, artifact, doc["artifactSha256"])
            doc["productionClasses"] = {f"codec{index}": sha256(b"release-codec").hexdigest() for index in range(8)}
            self.assertIs(doc, verify_wire_fixture(doc, artifact, doc["artifactSha256"]))

    def test_source_built_artifact_cannot_claim_published_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "source.jar"
            artifact.write_bytes(b"source-build")
            with self.assertRaises(ValueError):
                verify_wire_fixture({"schemaVersion": 1, "protocolVersion": 2, "artifactSha256": sha256(artifact.read_bytes()).hexdigest()}, artifact, "0" * 64)

    def test_rust_source_change_invalidates_prepared_inputs(self):
        from preparation import input_hashes
        directory = Path(__file__).resolve().parents[1] / "bots"
        with tempfile.NamedTemporaryFile(mode="w", suffix=".rs", dir=directory) as source:
            source.write("fn action() { }\n")
            source.flush()
            before = input_hashes()
            source.write("// changed native action\n")
            source.flush()
            self.assertFalse(before == input_hashes(), "Rust source mutations must invalidate prepared inputs")
