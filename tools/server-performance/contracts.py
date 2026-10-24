"""Strict JSON contracts; no compatibility readers."""
import dataclasses
import json
import math
import re
from enum import Enum
from pathlib import Path

from models import ArtifactSpec, RuntimeSpec


def safe_name(name):
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.+\-]*", name) or ".." in name:
        raise ValueError(f"unsafe artifact name: {name}")
    return name


def validate_values(value):
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError("nonfinite JSON number")
    if isinstance(value, dict):
        for item in value.values():
            validate_values(item)
    if isinstance(value, (list, tuple)):
        for item in value:
            validate_values(item)


def validate_document(value):
    if not isinstance(value, dict) or type(value.get("schemaVersion")) is not int or value["schemaVersion"] != 1:
        raise ValueError("unknown or missing schemaVersion")
    validate_values(value)
    return value


def read_document(path):
    return validate_document(json.loads(Path(path).read_text(), parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value))))


def json_value(value):
    if dataclasses.is_dataclass(value):
        return {field.name: json_value(getattr(value, field.name)) for field in dataclasses.fields(value)}
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, dict):
        return {key: json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_value(item) for item in value]
    return value


def to_json(value):
    result = json_value(value)
    validate_document(result)
    return json.dumps(result, indent=4, allow_nan=False) + "\n"


def atomic_write(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(to_json(value))
    temporary.replace(path)


def load_release(path):
    document = read_document(path)
    artifacts = []
    for asset in document["assets"]:
        digest = asset["digest"]
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest) or asset["size"] <= 0:
            raise ValueError("invalid artifact identity")
        artifacts.append(ArtifactSpec(safe_name(asset["name"]), digest[7:], asset["size"], asset["downloadUrl"], document["tag"], document["releaseTarget"]))
    if len({a.name for a in artifacts}) != len(artifacts):
        raise ValueError("duplicate artifact")
    return tuple(artifacts)


def load_runtimes(path):
    entries = []
    for value in read_document(path)["runtimes"]:
        value = dict(value)
        safe_name(value["id"])
        for key in ("argv", "capabilities", "dependencies", "evidence"):
            if key in value:
                value[key] = tuple(value[key])
        entries.append(RuntimeSpec(**value))
    if len({r.id for r in entries}) != len(entries):
        raise ValueError("duplicate runtime")
    return tuple(entries)


def load_case(value):
    from models import CaseSpec
    value = dict(value)
    for key in ("metrics", "budgets"):
        value[key] = tuple(value[key])
    return CaseSpec(**value)


def load_result(path):
    from models import CaseResult, Decision, MetricSample, Status
    value = read_document(path)
    value["case"] = load_case(value["case"])
    decision = value["decision"]
    value["decision"] = Decision(Status(decision["status"]), tuple(decision["reasons"]))
    value["samples"] = tuple(MetricSample(**sample) for sample in value["samples"])
    return CaseResult(**value)
