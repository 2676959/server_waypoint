"""Bound, untimed critical functional cases; missing evidence never passes."""
import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from contracts import atomic_write
from models import Decision, Status

REQUIRED = {
    "storage": ("create", "edit", "remove", "restore", "denied", "save", "reopen"),
    "navigation": ("methods", "retarget", "delete", "stop", "cleanup"),
    "terrain": ("regions", "isolation", "save", "reopen"),
    "protection": ("inventory", "chest", "drop", "pickup"),
}
VARIANTS = {"minimal-storage-permissions":"storage", "minimal-lifecycle":"navigation",
            "minimal-terrain":"terrain", "minimal-direct-protection":"protection"}


def bound_inputs(binding):
    if binding is None or len(binding) != 3:
        raise ValueError("minimal execution requires explicit runtime/artifact/release binding")
    return binding[0], binding[1], Path(binding[2])


def selections(supported):
    return [tuple(sorted(supported))]


def summarize(kind, checks):
    values = list(checks)
    names = [value["name"] for value in values]
    for name in REQUIRED[kind]:
        if name not in names:
            values.append({"name":name, "status":"BLOCKED", "reasons":["required native evidence absent"]})
    if len(set(names)) != len(names):
        values.append({"name":"coverage-identity", "status":"BLOCKED", "reasons":["duplicate named native check"]})
    for value in values:
        value["decision"] = Decision(Status(value["status"]), tuple(value.get("reasons", [])))
    completed = all(value["status"] in ("PASS", "FAIL") for value in values)
    status = "FAIL" if any(value["status"] == "FAIL" for value in values) else ("PASS" if completed else "BLOCKED")
    return {"schemaVersion":1, "status":status, "completed":completed, "checks":values,
            "reasons":[reason for value in values for reason in value.get("reasons", [])]}


def run(variant, root, binding, baseline=None):
    if variant not in VARIANTS:
        raise ValueError("unsupported minimal functional variant: " + variant)
    kind = VARIANTS[variant]
    bound_inputs(binding)
    if kind == "storage":
        from task10_smoke import run as invoke
        return invoke(root, baseline, "small", False, binding=binding, minimal=True)
    if kind == "navigation":
        from navigation_smoke import run as invoke
        return invoke(root, 1, binding=binding, minimal=True, baseline=baseline)
    if kind == "terrain":
        from bots.minimal_terrain import run as invoke
        return invoke(root, binding, baseline)
    from protection_smoke import run as invoke
    return invoke(root, 1, binding=binding, minimal=True, baseline=baseline)


def check(evidence, name, status="PASS", reasons=()):
    evidence.setdefault("checks", []).append({"name":name, "status":status, "reasons":list(reasons)})


def minimal_adapter(kind, filename):
    """Keep historical entrypoints unchanged while sealing partial minimal evidence."""
    def decorate(function):
        def wrapped(root, *args, **kwargs):
            if not kwargs.get("minimal", False):
                return function(root, *args, **kwargs)
            error = None
            try:
                function(root, *args, **kwargs)
            except Exception as failure:
                error = failure
            path = Path(root) / filename
            evidence = json.loads(path.read_text()) if path.is_file() else {}
            checks = evidence.get("checks", [])
            if error is not None:
                if isinstance(error, NativeAssertion):
                    check(evidence, error.name, error.decision.status.value, error.decision.reasons)
                else:
                    check(evidence, "execution", "BLOCKED", [f"{type(error).__name__}: {error}"])
                checks = evidence["checks"]
            result = summarize(kind, checks)
            result["observations"] = evidence
            atomic_write(Path(root) / "minimal-functional.json", result)
            return result
        return wrapped
    return decorate


class NativeAssertion(RuntimeError):
    """A judged native observation, separate from harness/setup failures."""
    def __init__(self, name, decision):
        self.name = name
        self.decision = decision
        super().__init__("; ".join(decision.reasons))


def observation_deadline(decisions, observed_owners, expected_owners):
    from reporting import combine_decisions
    values = list(decisions)
    if observed_owners != expected_owners or not values:
        values.append(Decision(Status.BLOCKED, ("native owner/observation missing at deadline",)))
    decision = combine_decisions(values)
    if decision.status == Status.PASS:
        return Decision(Status.BLOCKED, ("complete matched effects not established by deadline",))
    return decision
