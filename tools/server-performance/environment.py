"""Preparation-time system reference; never infer a historical host at report time."""
from datetime import datetime, timezone
import os
import platform
import subprocess


def _command(*argv):
    try:
        result = subprocess.run(argv, capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0:
        return None
    return (result.stdout + result.stderr).strip() or None


def _sysctl_number(name):
    value = _command("sysctl", "-n", name)
    return int(value) if value and value.isdecimal() and int(value) > 0 else None


def collect_environment(runtimes, profiled=False):
    system = platform.system()
    environment = {
        "schemaVersion": 1,
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "platform": platform.platform(),
        "machine": platform.machine(),
        "machineModel": None,
        "osVersion": platform.release(),
        "osBuild": platform.version(),
        "kernel": platform.release(),
        "cpu": platform.processor() or None,
        "physicalCores": None,
        "logicalCores": os.cpu_count(),
        "memoryBytes": None,
        "cpuMaxHz": None,
        "javaFlags": ["-Xms1G", "-Xmx2G", "-XX:+UseG1GC"],
        "javaRuntimes": [{"id": runtime.id, "java": runtime.java, "version": _command(runtime.java, "-version")} for runtime in runtimes],
        "loadGenerator": "same host; declared per-case client count",
        "profiledDiagnostic": profiled,
    }
    if system == "Darwin":
        environment.update({
            "machineModel": _command("sysctl", "-n", "hw.model"),
            "osVersion": _command("sw_vers", "-productVersion"),
            "osBuild": _command("sw_vers", "-buildVersion"),
            "cpu": _command("sysctl", "-n", "machdep.cpu.brand_string"),
            "physicalCores": _sysctl_number("hw.physicalcpu"),
            "logicalCores": _sysctl_number("hw.logicalcpu"),
            "memoryBytes": _sysctl_number("hw.memsize"),
            "cpuMaxHz": _sysctl_number("hw.cpufrequency_max"),
        })
    elif system == "Linux":
        try:
            environment["osVersion"] = platform.freedesktop_os_release().get("PRETTY_NAME")
        except OSError:
            environment["osVersion"] = None
        try:
            environment["memoryBytes"] = os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES")
        except (OSError, ValueError):
            pass
    return environment
