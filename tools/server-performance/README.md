# Server performance tools

M0 provides the Paper 1.21.11 zero-player runner. Other platforms, player-dependent
variants and later feature families remain explicit coverage requirements. The
registry is broader than the implemented native adapters.

Python 3.11+ uses only the standard library. Java source uses Java 17-compatible
features; Paper 1.21.11 runs on the pinned Java 21 runtime. Querz/NBT 6.1 generates
structures; Paper places, validates, saves and reopens them. Azalea is not needed
for `--suite zero-player`.

From the repository root:

```sh
rtk ./gradlew -p tools/server-performance/worldgen test installDist --console=plain --max-workers=2
rtk ./gradlew :cross-server:performanceProbeContractJar :cross-server:performanceProbeTest :paper:1.21.11-paper:performanceProbeJar --console=plain --max-workers=2
rtk proxy python3 -m unittest discover -s tools/server-performance/tests -v
rtk proxy python3 tools/server-performance/run.py inventory --output /Volumes/ssd/sw-performance-inventory
rtk proxy python3 tools/server-performance/run.py doctor --targets paper-1.21.11 --suite zero-player --output /Volumes/ssd/sw-performance-doctor
```

The runtime lock identifies local Java/server/probe paths and SHA-256 hashes. A
fresh probe build must match its recorded hash before preparation. Other hosts
must explicitly supply their own runtime lock; missing mappings remain BLOCKED.
Do not change the global Java or Rust defaults.

Prepare into a **new directory outside the checkout**. Existing roots and symlink
aliases are refused. Only server launcher/cache/libraries are copied from the
runtime source; its worlds, plugins and settings are not reused.

```sh
rtk proxy python3 tools/server-performance/run.py prepare --output /Volumes/ssd/sw-performance-example --targets paper-1.21.11 --suite zero-player --tiers medium --variants shulker-books-100,container-shulker-books,empty
rtk proxy python3 tools/server-performance/run.py run --root /Volumes/ssd/sw-performance-example --suite zero-player --scenarios inventory-regression --tiers medium --repeats 3
rtk proxy python3 tools/server-performance/run.py run --root /Volumes/ssd/sw-performance-example --suite zero-player --scenarios world-container --tiers medium --repeats 3
rtk proxy python3 tools/server-performance/run.py run --root /Volumes/ssd/sw-performance-example --suite zero-player --scenarios idle --tiers medium --repeats 3
rtk proxy python3 tools/server-performance/run.py report --root /Volumes/ssd/sw-performance-example
```

Each repetition starts fresh control and release processes from the same frozen
world. Preparation finishes before the 30-second warmup and 60-second measurement.
Do not build or run another timed workload on the worker during sampling. Changing
executable suite/configuration inputs after preparation requires a new root.

Reports retain prior attempts and failures. Exit codes are 0 for a passing selected
result, 1 for an observed defect, 2 for incomplete/inconclusive/blocked evidence,
and 130 for interruption. An expected release defect still exits 1. Filtered
reports are partial release evidence. One repetition is a smoke/calibration run
and does not establish the default three-repeat acceptance result.

Use a separately prepared `--profile` root for JFR diagnostic reruns. The same
instrumentation is present in both members of a pair. Profiles stay local. Raw
native tick samples, named-server-thread workload allocations, whole-process
CPU/RSS observations and GC logs have separate scopes. Callback spacing is never
reported as tick processing time.

For M0, `CaseSpec.offered_rate` uses events/workload-tick for synthetic cases,
circulation-cell counts for physical cases, and zero for idle. Native counters
independently record the achieved demand.

Physical counters record observed hopper inventory increases, a conservative
movement count; synthetic dispatch counters never stand in for moved items.
All physical cases require zero players, disabled empty-server pausing, force-loaded
chunks, continued movement and item conservation. Lower throughput in an active
release world remains a failure. A nonmoving control is invalid fixture evidence.

Fixtures exist for 16/128/512 cells and four item families. Tests and generated
recipes alone do not prove every native size/variant combination has run. See the
[execution progress](../../docs/features/server-performance/validation/progress.md)
and retained reports for the actual selection.

The probes have separate Gradle source sets and archives. No performance classes,
Querz dependency or bot code are added to production JARs. This milestone does not
repair the production listener or implement later publication gates.
