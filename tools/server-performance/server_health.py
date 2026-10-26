"""Native logger stability is independent of successful action/throughput checks."""
from pathlib import Path
import re
from models import Decision, Status


SEVERE = re.compile(r"^\[[^\]]+\] \[[^\]]*/(?:ERROR|SEVERE)\]: (.*)$|^\[[^\]]+ (?:ERROR|SEVERE)\]: (.*)$")


def assess_log(text):
    lines = text.splitlines() if isinstance(text, str) else text
    entries = 0
    watchdog = 0
    observed = False
    for line in lines:
        observed |= bool(line.strip())
        matched = SEVERE.match(line.rstrip("\n"))
        if matched:
            entries += 1
            message = matched.group(1) or matched.group(2)
            watchdog += "The server has not responded for" in message
    if not observed:
        return Decision(Status.BLOCKED, ("native server log is empty",))
    reasons = []
    if entries:
        reasons.append(f"native ERROR/SEVERE log entries: {entries}")
    if watchdog:
        reasons.append(f"native watchdog nonresponse reports: {watchdog}")
    return Decision(Status.FAIL if reasons else Status.PASS, tuple(reasons))


def native_log_health(path):
    try:
        with Path(path).open(errors="replace") as source:
            return assess_log(source)
    except OSError as error:
        return Decision(Status.BLOCKED, ("native server log unavailable: " + str(error),))
