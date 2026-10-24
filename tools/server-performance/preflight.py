"""Capability checks are scoped to selected work, and retain missing evidence."""
from pathlib import Path
import subprocess

from artifacts import sha256
from contracts import atomic_write
from models import Decision, Status


def required_capabilities(suite):
    base = {"fixture", "tick-processing", "zero-player-ticking", "profile"}
    if suite == "all":
        base |= {"bots", "wire-protocol"}
    elif suite != "zero-player":
        raise ValueError("unknown suite")
    return frozenset(base)


def check_runtime(runtime, output, required_capabilities):
    reasons = []
    facts = {"schemaVersion": 1, "runtime": runtime.id, "evidence": runtime.evidence}
    for label, value, digest in (("java", runtime.java, runtime.java_sha256), ("server", runtime.server, runtime.server_sha256)):
        path = Path(value)
        if not value or not path.is_file() or not digest or sha256(path) != digest:
            reasons.append(f"{label}: missing or hash mismatch")
    if runtime.java and Path(runtime.java).is_file() and not any(r.startswith("java:") for r in reasons):
        process = subprocess.run([runtime.java, "-version"], capture_output=True, text=True, timeout=15)
        facts["javaVersion"] = process.stderr + process.stdout
        if process.returncode:
            reasons.append("java: version invocation failed")
    for capability in sorted(required_capabilities):
        if capability == "bots":
            bot = runtime.bot
            if not bot.get("verifiedNativeActions") or not bot.get("path") or not Path(bot["path"]).is_file() or sha256(bot["path"]) != bot.get("sha256"):
                reasons.append("bots: native connection/action mapping unverified or binary missing")
        elif capability not in runtime.capabilities:
            reasons.append(f"{capability}: native capability unverified")
    if {"fixture", "tick-processing"} & required_capabilities:
        probe = runtime.probe
        if not probe.get("path") or not Path(probe["path"]).is_file() or sha256(probe["path"]) != probe.get("sha256"):
            reasons.append("probe: missing or hash mismatch")
    for dependency in runtime.dependencies:
        if dependency.get("required", True) and (not dependency.get("path") or not Path(dependency["path"]).is_file() or sha256(dependency["path"]) != dependency.get("sha256")):
            reasons.append(f"dependency unverified: {dependency['id']}")
    decision = Decision(Status.BLOCKED if reasons else Status.PASS, tuple(reasons))
    atomic_write(Path(output) / f"{runtime.id}.json", {**facts, "decision": decision})
    return decision
