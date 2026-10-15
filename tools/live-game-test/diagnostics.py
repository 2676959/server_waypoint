"""Keep complete runtime evidence while reducing duplicate console diagnostics."""
from pathlib import Path
import re

from runner import write_json


HEADER = re.compile(r"^\[.*?\]\s*\[([^\]]*?)/(WARN|ERROR|FATAL|INFO|DEBUG|TRACE)\]:?\s*(.*)")
EXCEPTION = re.compile(r'^(?:Exception in thread |[\w.$]+(?:Exception|Error)(?::|$))')
NON_ACTIONABLE = {
    "Failed to fetch Realms feature flags": "Offline test identity cannot use Realms",
    "io exception while checking versions: Online mod data expired!": "Optional map-mod update service data expired",
}


def collect_diagnostics(home):
    files = sorted(set(list((home / "game/logs").glob("*.log")) +
                       list((home / "game").glob("std*.log"))))
    entries = {}
    for path in files:
        active = None
        for number, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
            match = HEADER.match(line)
            if match:
                active = None
                logger, severity, message = match.groups()
                if severity not in {"WARN", "ERROR", "FATAL"}:
                    continue
            elif EXCEPTION.match(line) and active is None:
                logger, severity, message = "stderr", "ERROR", line
            else:
                if active is not None and line:
                    # Repeated stdout/latest entries keep the same full exception once.
                    active["continuation"].append(line)
                continue
            key = (severity, logger, message)
            entry = entries.setdefault(key, {"severity": severity, "logger": logger,
                                             "message": message, "sources": [], "details": [],
                                             "ignored": False})
            for prefix, reason in NON_ACTIONABLE.items():
                if message.startswith(prefix):
                    entry.update(ignored=True, ignore_reason=reason)
            active = {"entry": entry, "continuation": []}
            entry["sources"].append({"file": str(path.relative_to(home)), "line": number})
            entry["details"].append(active["continuation"])
    result = []
    for entry in entries.values():
        details = max(entry.pop("details"), key=len, default=[])
        entry["message"] += ("\n" + "\n".join(details)) if details else ""
        result.append(entry)
    return result


def record_diagnostics(home, result):
    home = Path(home)
    entries = collect_diagnostics(home)
    crashes = sorted(list((home / "game/crash-reports").glob("*")) +
                     list((home / "game").glob("hs_err_pid*.log")))
    crashes = [str(path.relative_to(home)) for path in crashes if path.is_file()]
    counts = {"errors": sum(entry["severity"] in {"ERROR", "FATAL"} and not entry["ignored"] for entry in entries),
              "warnings": sum(entry["severity"] == "WARN" and not entry["ignored"] for entry in entries),
              "ignored": sum(entry["ignored"] for entry in entries)}
    write_json(home / "diagnostics.json", entries)
    result["diagnostics"] = counts
    result["crash_reports"] = crashes
    actionable = sorted((entry for entry in entries if not entry["ignored"]),
                        key=lambda entry: entry["severity"] == "WARN")
    result["diagnostic_preview"] = [{"severity": entry["severity"],
                                     "message": entry["message"].splitlines()[0][:200],
                                     "source": entry["sources"][0]} for entry in actionable[:3]]
    if result["status"] == "PASS" and (counts["errors"] or crashes):
        result["status"] = "FAIL"
        result["failure"] = "Runtime errors or crash reports; see diagnostics.json"
    return entries


def print_result(result):
    counts = result.get("diagnostics", {})
    suffix = ""
    if counts.get("errors") or counts.get("warnings"):
        suffix = f" errors={counts.get('errors', 0)} warnings={counts.get('warnings', 0)}"
    if result["status"] != "PASS":
        failure = " ".join(result.get("failure", "Incomplete run").split())
        suffix += " " + result.get("stage", "unknown") + ": " + failure[:240]
    print(result["status"], result["profile"] + suffix, flush=True)
    for entry in result.get("diagnostic_preview", []):
        source = entry["source"]
        print(f"  {entry['severity']} {entry['message']} ({source['file']}:{source['line']})", flush=True)


def write_summary(output, results):
    write_json(output / "summary.json", results)
    lines = ["# Live Minecraft verification", "", "| Profile | Suite | Outcome | Errors | Warnings | Failure |",
             "| --- | --- | --- | --- | --- | --- |"]
    for row in results:
        counts = row.get("diagnostics", {})
        failure = " ".join(row.get("failure", "").split()).replace("|", "\\|")
        lines.append(f"| {row['profile']} | {row['suite']} | {row['status']} | {counts.get('errors', 0)} | "
                     f"{counts.get('warnings', 0)} | {failure} |")
    lines.extend(["", "Full runtime logs and diagnostics are retained in each profile directory.",
                  "Native game-thread checks do not certify visual appearance or multiplayer transfers.", ""])
    (output / "report.md").write_text("\n".join(lines))
