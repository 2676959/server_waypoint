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

`report.md` includes a system build reference, and `report.json` embeds the captured
`environment.json`. Preparation records the OS version/build, kernel, machine model,
architecture, CPU/core counts, RAM, selected Java executable versions/builds, JVM flags
and load-generator placement. This snapshot is sealed with the run inputs. Optional
values unavailable from the OS remain explicitly unavailable; reporting never substitutes
the reporting machine's current configuration for a missing run snapshot. CPU frequency
is OS-managed with no suite cap; an OS-reported maximum, when available, is a reference
value, not a measurement of frequency or thermal throttling during the workload.

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

## M1 native helper checkpoints

Tasks 8–9 provide a locked Azalea bot and immutable release codec exporter;
Task 10 adds native command callbacks, deterministic datasets and storage checks.
The main audit CLI still blocks unimplemented player/feature adapters. Helpers
are explicitly partial evidence, with progress tracked in the feature ledger.

```sh
rtk proxy python3 tools/server-performance/bots/command_smoke.py --behavior --traffic --tier small --root "$SW_PERF_NATIVE_ROOT"
```

Set `SW_PERF_NATIVE_ROOT` to a new absolute disposable directory. Use `--tier medium`
for the 10,000-waypoint fixture and ten-actor five-second traffic smoke. The helper
uses the pinned local runtime and hash-verified cached 4.0.0 artifact. Read its
`traffic/traffic.json` and exact saved/reopened-state evidence: native callback
completion, command submission and client-issued completion are separate measures.
Operator actors are declared for throughput smoke; permission-denial cases use a
separate non-operator fixture. These helpers do not satisfy timed performance gates,
client-roundtrip latency, the full platform matrix or the 30-minute soaks.


Task 10's prepared player runner currently implements `storage:concurrent` on the
original Paper 1.21.11 anchor. Its default phases are 30s warm-up and 60s measurement,
with three fresh process repetitions. Marker registration and command completion are
separate native acknowledgements; command RTT uses one client monotonic clock and
excludes the separately recorded marker-registration interval. Console callback
processing/allocation and server/driver whole-process samples are distinct metrics.

```sh
rtk proxy python3 tools/server-performance/run.py prepare --output /Volumes/ssd/sw-perf-storage-new --suite all --scenarios storage --variants concurrent --tiers small,medium
rtk proxy python3 tools/server-performance/run.py run --root /Volumes/ssd/sw-perf-storage-new --suite all --scenarios storage --variants concurrent --tiers small,medium --repeats 3
```

Use a fresh root. Other storage/command variants remain BLOCKED until their native
behavior coverage is implemented. Operator throughput does not establish non-operator
command admission or complete command-leaf/suggestion coverage.

Task 11's standalone navigation helper exercises every nonempty method selection,
real movement, retarget/edit/teleport effects, stop and disconnect cleanup with
independent native state and normal client packets:

```sh
rtk proxy python3 tools/server-performance/bots/navigation_smoke.py --root /Volumes/ssd/sw-perf-navigation-new --count 1
```

Use `--count 10` for ten real actors and a one-owner retarget isolation check.
Ordinary stone/diamond stacks are seeded and conserved through selections and
retargeting. The original release can exit 1 for retained post-disconnect tick
counters; retain that result. These are partial functional checks, not timed
navigation acceptance, all protection hooks or persistent map-terrain validation.
See the [navigation observations](../../docs/features/server-performance/validation/2026-10-25-m1-navigation.md).

```sh
rtk proxy python3 tools/server-performance/bots/terrain_smoke.py --root /Volumes/ssd/sw-perf-terrain-new --count 10
```

The terrain helper compares all native pixel bytes with independently retained
region references and actual saved terrain files, requires complete client map
patch coverage/checksum agreement, then saves/reopens and verifies join restoration
of all five methods with exact owner map IDs, targets and live effects. It covers
two regions in one dimension with one or ten independent owners; cross-dimension
terrain, eviction and shared-map viewers remain separate requirements.

```sh
rtk proxy python3 tools/server-performance/bots/lifecycle_smoke.py --root /Volumes/ssd/sw-perf-lifecycle-new --count 10
```

This standalone helper observes actual death/automatic respawn and native world
transitions with all five methods, checks ordinary death-drop conservation and
protected-item exclusion, and verifies owner targets/items/live effects. A native
Nether landing fixture loads its chunks and reads back its floor/walls/clearance
before travel. Same-dimension respawn preserves level text entities; dimension
changes replace them. Native receipts and packet state do not establish rendering.

```sh
rtk proxy python3 tools/server-performance/bots/protection_smoke.py --root /Volumes/ssd/sw-perf-protection-new --count 10
```

This helper uses normal drop/swap/container-click packets and native actor/slot/
cancellation receipts, with exact owner targets/items and atomic inventory/world
ordinary-item conservation. It checks nine selected player-menu cases and an
ordinary drop's actual correlated world entity. It does not cover every protection
hook, actual crafting recipes, nested carriers, equipment or other containers.
The original 1/10/50-player smoke uses explicit inventory slots and a peaceful
disposable world so random mob deaths cannot invalidate action conservation.
The bot smoke writes a separate `health.json`; native ERROR/SEVERE log entries or
watchdog reports fail stability even when every action completes. The physical
helper also checks native log health. Retain prior functional/stability outcomes
separately; the fifty-player original release has produced watchdog reports during
native command-tree serialization.

```sh
rtk proxy python3 tools/server-performance/bots/protection_smoke.py --root /Volumes/ssd/sw-perf-nested-new --count 10 --case-set nested
```

The nested selection creates owned fixtures through native `World.dropItem`, using
clones of live owner navigation items. It observes actual spawn events and world
entities rather than synthesizing Bukkit events. An independent PDC/container graph
reader checks protected descendants; separately constructed clean carriers preserve
ordinary children, names, lore and metadata exactly. Cases include shulkers, mixed
containers, bundles at depths one/eight/nine, and plain controls. This is a controlled
server fixture, not proof that players can construct the carriers through inventory
UI. Every case is retained before owned entity cleanup; the original release exits
1 when the depth-nine item escapes even if later controls pass.


Native console read-leaf/provider smoke (functional scope; fresh owned root):

```sh
rtk proxy python3 tools/server-performance/bots/command_leaves_smoke.py --root /Volumes/ssd/sw-perf-leaves-new --baseline /Volumes/ssd/sw-performance-m1-storage-timed/fixtures/navigation-terrain-small/frozen --suggestions
```

The supplied frozen baseline must have a valid native `baseline.json` manifest.
The helper executes 5,295 console read leaves and observes 51 local custom suggestion
paths on the verified anchor. Feedback is captured from the real native console file
log between delivery-scheduler markers; provider receipts identify the actual invoked
Brigadier callbacks. `--limit N` makes the read selection partial. Remote providers and
other command families retain separate requirements; this helper cannot establish a
complete command family or performance gate.


Native console storage mutation leaf smoke (functional scope):

```sh
rtk proxy python3 tools/server-performance/bots/mutation_leaves_smoke.py --root /Volumes/ssd/sw-perf-mutations-new --baseline /Volumes/ssd/sw-performance-m1-storage-timed/fixtures/navigation-terrain-small/frozen --inventory /Volumes/ssd/sw-performance-m1-read-suggestions-full/command-tree.json
```

The reference inventory seeds independent fixtures; its selected paths must match the
live native tree before execution. The helper checks 43 storage leaves, every persisted
property/revision, single-use restore, nonempty-list rejection and actual config/language
reload behavior. It saves/reopens the server and compares exact contents. Generated
quick-add color values are explicitly domain-checked before entering the operation ledger.
This console smoke does not establish remaining player/permission/platform coverage.
