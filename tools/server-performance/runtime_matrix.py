"""Native mod-loader anchor decisions. Missing setup is blocked, never excluded."""
from pathlib import Path
import zipfile

from artifacts import sha256
from models import Decision, Status

VERIFIED_AZALEA = {
    "minecraft": "1.21.11",
    "candidate": "0.15.1+mc1.21.11",
    "revision": "675e56fd9d9da084b8f923fdab6feda3ebd19568",
}
PROBE_PREFIX = "_959/server_waypoint/perf/"
BASE_CAPABILITIES = ("fixture", "tick-processing", "zero-player-ticking", "bots")


def mod_platform(name):
    for platform in ("neoforge", "forge", "fabric"):
        if f"-{platform}-" in name:
            return platform
    return None


def anchor_decision(runtime, output):
    del output
    reasons = []
    for label, path, digest in (
        ("java", runtime.java, runtime.java_sha256),
        ("server", runtime.server, runtime.server_sha256),
    ):
        if not _matches(path, digest):
            reasons.append(f"{label}: missing or hash mismatch")
    probe = runtime.probe or {}
    if not _matches(probe.get("path", ""), probe.get("sha256", "")):
        reasons.append("probe: missing or hash mismatch")
    required = BASE_CAPABILITIES
    if runtime.platform in {"forge", "neoforge"}:
        required = required + ("channel-envelope",)
    for capability in required:
        if capability not in runtime.capabilities:
            reasons.append(f"{capability}: native capability unverified")
    reasons.extend(_azalea_reasons(runtime))
    return Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))


def published_mod_anchors(artifacts, runtimes, output):
    grouped = {}
    for runtime in runtimes:
        grouped.setdefault(runtime.artifact, []).append(runtime)
    reasons = []
    for artifact in artifacts:
        platform = mod_platform(artifact.name)
        if platform is None:
            continue
        matches = [runtime for runtime in grouped.get(artifact.name, []) if runtime.platform == platform]
        if len(matches) != 1:
            reasons.append(f"no native runtime anchor: {artifact.name}")
            continue
        decision = anchor_decision(matches[0], Path(output) / matches[0].id)
        if decision.status != Status.PASS:
            reasons.extend(f"{artifact.name}: {reason}" for reason in decision.reasons)
    return Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))


def probe_class_decision(production_jar, probe_jar):
    production = Path(production_jar)
    probe = Path(probe_jar)
    if not production.is_file() or production.is_symlink() or not probe.is_file() or probe.is_symlink():
        return Decision(Status.BLOCKED, ("probe or production jar missing",))
    if _probe_entries(production):
        return Decision(Status.FAIL, ("production jar contains probe classes",))
    if not _probe_entries(probe):
        return Decision(Status.BLOCKED, ("probe jar has no probe classes",))
    return Decision(Status.PASS, ())


def _azalea_reasons(runtime):
    bot = runtime.bot or {}
    identity = (
        runtime.minecraft == VERIFIED_AZALEA["minecraft"]
        and bot.get("candidate") == VERIFIED_AZALEA["candidate"]
        and bot.get("revision") == VERIFIED_AZALEA["revision"]
        and bot.get("verifiedNativeActions") is True
    )
    if not identity:
        return ("bots: unverified azalea native mapping",)
    if not _matches(bot.get("path", ""), bot.get("sha256", "")):
        return ("bots: native connection/action mapping unverified or binary missing",)
    return ()


def _matches(path, digest):
    file = Path(path) if path else None
    return bool(path and digest and file.is_file() and not file.is_symlink() and sha256(file) == digest)


def _probe_entries(path):
    with zipfile.ZipFile(path) as archive:
        return any(name.startswith(PROBE_PREFIX) for name in archive.namelist())
