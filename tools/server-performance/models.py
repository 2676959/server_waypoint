"""Schema 1 value objects. Runtime evidence is distinct from applicability."""
from dataclasses import dataclass, field
from enum import Enum
import math
from pathlib import Path
from typing import Any


class Status(str, Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    INCONCLUSIVE = "INCONCLUSIVE"
    BLOCKED = "BLOCKED"
    NOT_APPLICABLE = "NOT_APPLICABLE"


@dataclass(frozen=True)
class Decision:
    status: Status
    reasons: tuple[str, ...] = ()
    schemaVersion: int = 1

    def __post_init__(self):
        if self.status == Status.NOT_APPLICABLE and not self.reasons:
            raise ValueError("NOT_APPLICABLE needs a source/release reason")


@dataclass(frozen=True)
class ArtifactSpec:
    name: str
    sha256: str
    size: int
    url: str
    release: str
    revision: str
    provenance: str = "published"


@dataclass(frozen=True)
class RuntimeSpec:
    id: str
    artifact: str
    platform: str
    minecraft: str
    java: str = ""
    server: str = ""
    java_sha256: str = ""
    server_sha256: str = ""
    argv: tuple[str, ...] = ()
    capabilities: tuple[str, ...] = ()
    dependencies: tuple[dict, ...] = ()
    bot: dict = field(default_factory=dict)
    probe: dict = field(default_factory=dict)
    evidence: tuple[str, ...] = ()


@dataclass(frozen=True)
class ScenarioSpec:
    feature: str
    variants: tuple[str, ...]
    fixture: str
    metrics: tuple[str, ...]
    budgets: tuple[str, ...]
    clients: int = 0


@dataclass(frozen=True)
class CaseSpec:
    id: str
    runtime: str
    feature: str
    variant: str
    tier: str
    mode: str
    fixture: str
    metrics: tuple[str, ...]
    budgets: tuple[str, ...]
    clients: int = 0
    warmup_seconds: float = 30
    measurement_seconds: float = 60
    repeats: int = 3
    offered_rate: int = 0
    deadline_seconds: float = 180
    minimum_samples: int = 100
    allocation_scope: str = "server-thread"
    applicability_reason: str = ""


@dataclass(frozen=True)
class MetricSample:
    metric: str
    value: float
    unit: str
    tick: int
    phase: str = "MEASURING"
    clock: str = "server-monotonic"
    scope: str = "server-thread"
    region: str = "global"

    def __post_init__(self):
        if not math.isfinite(self.value) or self.value < 0 or self.tick < 0:
            raise ValueError("nonfinite or negative sample")
        if not self.unit or not self.clock or not self.scope:
            raise ValueError("sample metadata missing")


@dataclass(frozen=True)
class CaseResult:
    case: CaseSpec
    decision: Decision
    run_id: str = ""
    attempt_id: str = ""
    artifact: dict = field(default_factory=dict)
    identities: dict = field(default_factory=dict)
    samples: tuple[MetricSample, ...] = ()
    assertions: dict = field(default_factory=dict)
    observations: dict = field(default_factory=dict)
    paths: dict = field(default_factory=dict)
    cleanup: str = "unverified"
    schemaVersion: int = 1


@dataclass(frozen=True)
class AttemptRecord:
    run_id: str
    attempt_id: str
    case_id: str
    state: str = "CREATED"
    last_completed: str = "CREATED"
    reason: str = ""
    exit_code: int = 2
    schemaVersion: int = 1


@dataclass(frozen=True)
class RunRecord:
    run_id: str
    results: tuple[CaseResult, ...]
    expected: tuple[str, ...]
    partial: bool = True
    schemaVersion: int = 1


@dataclass(frozen=True)
class OwnedProcess:
    pid: int
    pgid: int
    owner: str
    cwd: Path
    log: Path
    process: Any = field(compare=False, repr=False)
