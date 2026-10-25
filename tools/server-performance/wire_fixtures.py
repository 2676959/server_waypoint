"""Bind exported codec vectors to actual immutable production class bytes."""
from hashlib import sha256
from zipfile import ZipFile
from artifacts import sha256 as file_sha256
from contracts import validate_document


def verify_wire_fixture(document, artifact, published_sha256):
    document = validate_document(document)
    if document.get("protocolVersion") != 2 or document.get("artifactSha256") != published_sha256 or file_sha256(artifact) != published_sha256:
        raise ValueError("wire fixture is not bound to the published protocol/artifact")
    classes = document.get("productionClasses", {})
    if len(classes) < 8 or not document.get("vectors"):
        raise ValueError("codec source fingerprints/vectors missing")
    with ZipFile(artifact) as jar:
        for name, digest in classes.items():
            if sha256(jar.read(name.replace(".", "/") + ".class")).hexdigest() != digest:
                raise ValueError("wrong production class hash")
    return document
