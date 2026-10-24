"""Stage immutable bytes. Filenames/version strings are never provenance."""
import hashlib
from pathlib import Path
import shutil
import urllib.request

from contracts import safe_name
from models import Decision, Status


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify_artifact(artifact, path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size != artifact.size or sha256(path) != artifact.sha256:
        raise ValueError(f"artifact bytes do not match published identity: {artifact.name}")


def stage_artifacts(artifacts, cache, output):
    cache, output = Path(cache), Path(output)
    if cache.is_symlink() or output.is_symlink() or output.exists():
        raise ValueError("staging requires a new output and non-symlink cache")
    cache.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True)
    result = {}
    for artifact in artifacts:
        safe_name(artifact.name)
        source = cache / artifact.name
        if not source.exists() and not source.is_symlink():
            if not artifact.url.startswith("https://"):
                raise ValueError("downloads require an explicit HTTPS asset URL")
            temporary = cache / (artifact.name + ".part")
            if temporary.exists() or temporary.is_symlink():
                raise ValueError("stale/unsafe partial download")
            try:
                with urllib.request.urlopen(artifact.url, timeout=60) as response, temporary.open("xb") as stream:
                    shutil.copyfileobj(response, stream)
                verify_artifact(artifact, temporary)
                temporary.replace(source)
            finally:
                temporary.unlink(missing_ok=True)
        verify_artifact(artifact, source)
        destination = output / artifact.name
        shutil.copyfile(source, destination)
        verify_artifact(artifact, destination)
        result[artifact.name] = destination
    return result


def validate_matrix(artifacts, runtimes):
    names = {artifact.name for artifact in artifacts}
    mapped = {runtime.artifact for runtime in runtimes}
    reasons = [f"no native runtime anchor: {name}" for name in sorted(names - mapped)]
    reasons += [f"runtime references unknown artifact: {name}" for name in sorted(mapped - names)]
    return Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))
