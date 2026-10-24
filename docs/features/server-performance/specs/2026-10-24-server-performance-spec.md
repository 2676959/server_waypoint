# Server performance suite specification

Date: 2026-10-24

Status: implementation specification derived from the design approved by the user on
2026-10-24. No implementation or execution result is implied by this document.

## 1. Delivery contract

The first delivery is an executable, comprehensive performance audit of the published
**Server Waypoint 4.0.0** binaries, followed by evidence-backed source repairs and the
same audit against repaired candidates. Future PR selection and publishing gates follow
that work. They cannot substitute for the initial audit.

Inputs and decisions:

- [Approved design](2026-10-24-server-performance-design.md).
- [Published asset inventory](../validation/2026-10-24-release-assets.json): 44 JARs,
  release target `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`.
- Azalea is required for real connected bots; Querz/NBT is required for world fixtures.
- Both player-driven and zero-player world workloads require multiple load tiers.
- Source repairs target the current development branch, preserving unrelated work.
  Original-release, unrepaired-development, and repaired-candidate results are distinct.
- Java code uses Java 17-compatible language features; runtime JDKs remain target-specific.
  Gradle scripts use Kotlin DSL; Java JSON uses GSON; indentation is four spaces.
- No commits, pushes, publication, production-server changes, or product compatibility
  layers are authorized by this documentation task. Tests stay outside production JARs.

The implementation plan is [2026-10-24-server-performance.md](../plans/2026-10-24-server-performance.md).
This specification fixes the interfaces and acceptance semantics; the design explains
their rationale. Calibrations must be recorded, not silently substituted for these defaults.

## 2. Architecture and ownership

| Owner | Files/responsibility |
| --- | --- |
| `tools/server-performance/` | Python standard-library CLI, manifests, orchestration, reporting, comparison and gate validation. |
| `tools/server-performance/worldgen/` | Standalone Gradle Kotlin DSL/Java tool using Querz/NBT and GSON; emits NBT structures and independent expected-state manifests. |
| `tools/server-performance/bots/` | Rust Azalea driver, scenario protocol, exact dependency/toolchain pins, per-Minecraft builds, lockfiles. |
| `paper/src/performanceProbe/` | Test-only Paper/Folia plugin: fixture placement, native telemetry, production-event dispatch and state assertions. |
| `mods/src/performanceProbe/` | Test-only loader mod: native fixture placement/telemetry and workload assertions through actual loader hooks/mixins. |
| `velocity/src/performanceProbe/` | Test-only proxy plugin: actual transfers, online routes, runtime/coordinator counters and cleanup assertions. |
| `cross-server/src/performanceProbe/` | Platform-free test-only command/event contract shared by backend and proxy probes; separate `performanceProbeContractJar`, never production content. |
| `common/src/performanceFixtures/` | Development-only Java wire/waypoint fixture exporter using production codecs. Its output identifies the source revision and production-class hashes. |
| `common`, `cross-server`, `proxy-common` benchmark source sets | Later JMH measurements of their own production APIs. No platform dependencies move into shared production modules. |

Existing `tools/folia-live-test`, `tools/proxy-lifecycle-test`, and
`tools/live-game-test` are references for isolated runs and assertions. Do not invoke
their artifact-selection shortcuts, fixed ports, or historical counts as audit authority.

New source-set verification tasks are `performanceProbeJar` on Paper, Forge, NeoForge
and Velocity; Fabric uses `remapPerformanceProbeJar`. `:common:exportPerformanceFixtures`
exports source-built fixtures. These names refer to planned tasks, not existing commands.
Before compiling probes, read `settings.gradle.kts` and both active Stonecutter files.
Use fully qualified target tasks; preserve `26.1.2-fabric` / `26.2-paper` active selections.
Verify all touched Stonecutter branches, API signatures and runtime descriptors before
adapting probes. Probes must load the audited production classes rather than ship copies.

## 3. CLI and runtime lifecycle

The entry point is `python3 tools/server-performance/run.py`. Proposed subcommands:

| Command | Contract |
| --- | --- |
| `inventory --release-manifest FILE --runtime-lock FILE --output DIR` | Expand artifact/runtime/feature requirements; report cardinality and missing mappings. No benchmark execution. |
| `doctor --runtime-lock FILE --output DIR` | Validate available dependencies, hashes, native bot capabilities, tick metrics, profiler capabilities and host controls. Preserve blockers. |
| `prepare --release-manifest FILE --runtime-lock FILE --output NEW_ROOT` | Create one owned disposable root; verify immutable artifacts; build tools; generate, place, validate and freeze runtime-specific world fixtures. |
| `run --root ROOT --mode audit --suite all --tiers small,medium,stress --repeats 3` | Run every applicable scheduled case; continue independent cases after failures; retain complete original-release findings. |
| `run --root ROOT --mode candidate --suite all --tiers small,medium,stress --repeats 3` | Run the same registry/fixtures against explicitly supplied repaired artifact hashes. |
| `report --root ROOT` | Rebuild JSON/Markdown summaries from validated evidence without changing measured outcomes. |
| `gate --root ROOT --artifacts DIR --profile release` | Require complete passing coverage, approved host/budgets and exact final JAR hashes. No builds or uploads. |

Allow `--targets` and `--scenarios` filters for iteration, but label filtered reports as
partial and reject them as full-release evidence. `run` creates a new attempt beneath
the prepared root; it never overwrites a previous result. A retry is a new attempt.
Also support `--suite zero-player` on `doctor`, `prepare` and `run`: select idle,
world-container and inventory-regression variants requiring zero clients. Preflight
requires only capabilities consumed by the selection. Missing bot binaries must not
block this milestone; they still block later bot-dependent/full-audit coverage.

Preparation states: `CREATED -> ARTIFACTS_VERIFIED -> FIXTURES_VALIDATED -> PREPARED`.
Attempt states: `CREATED -> STARTING -> READY -> WARMUP -> MEASURING -> QUIESCING ->
STOPPED -> REPORTED`. Any stage may terminate in `FAILED` or `INTERRUPTED`; retain the
last completed stage and reason. Only readiness/measurement-complete attempts qualify.

Resolve and reject symlink aliases into existing servers, source checkouts, or immutable
fixture roots. Writable runtime roots are new directories outside the repository and
contain an ownership marker and run UUID. Cache immutable downloads separately.
Use argument arrays without a shell. Bind test services to loopback; a separately
provisioned load generator uses an explicitly recorded test-network topology instead.
Allocate collision-checked ports per topology. Shut down only owned processes/process
groups, with graceful stop followed by a bounded kill; never kill by Java process name.
Write partial results atomically and retain logs on setup failure, crash or interruption.

Exit codes: `0` complete passing selection, `1` observed correctness/performance failure,
`2` invalid/incomplete/inconclusive setup or coverage, `130` interruption. A known defect
still exits `1`; testing that classification is successful harness validation, not a
passing release. When failures and blockers coexist, preserve both and return `1`.

## 4. Artifact and runtime lock

`config/releases/4.0.0.json` is copied from the verified published inventory, never
regenerated from a local `build/libs` directory. A matching version string is insufficient.
Rehash all staged JARs before launch and after measurement. Source/class provenance for
probes and wire fixtures is separate from the audited JAR provenance.

`config/runtimes.json` contains one or more native runtime anchors for each published
artifact, with exact Minecraft/server/loader/JVM builds and hashes, launcher arguments,
required dependencies, bot build, probe build, profiler and applicable capabilities.
Derive the audit matrix from the release revision's configuration/metadata; derive later
development coverage from its own revision. Do not assume future matrices equal 44.

Run all applicable feature families at small/medium tiers for each published backend
artifact; exercise Velocity in real proxy/two-backend topologies. Add Folia and anchors
at relevant API transitions inside published ranges. Stress/30-minute soak covers each
distinct loader/API family, including Folia and Velocity. A runtime-anchor result is
not a claim about every untested patch version in the artifact's advertised range.

Pin Azalea to native protocol builds: `0.15.1+mc1.21.11` is the initial candidate for
Paper 1.21.11; the inspected Git revision `0d38e0dcf47eb258c41b1b09ebc2bcde7253e185`
documents 26.2. Other mappings require actual connection/action verification. Commit
dependency/toolchain pins only if separately authorized; the plan requires recording
them in the working files regardless. Do not change a global Rust default, insert a
translation proxy, or mark a missing native bot mapping as covered.

Pin Querz/NBT to the verified candidate `com.github.Querz:NBT:6.1` after resolution and
checksum validation. Tool builds, native mappings and profiler availability have their
own capability results; an unavailable requirement is `BLOCKED`, not `NOT_APPLICABLE`.

## 5. Fixture and workload contracts

Each `FixtureRecipe` declares `id`, `targetRuntime`, `seed`, `dataVersion`, `bounds`,
`structures`, `initialInventories`, `waypointDataset`, `activation`, `expectedState`,
`requiredTickingRegions` and hashes. Use seed `95920261024` for initial recipes; fix
positions, facing, names and distributions. Include negative coordinates and chunk/
region boundaries in preparation verification. Timestamps/run IDs are not recipe inputs.

Querz/NBT constructs generic NBT structure templates. Its inspected MCA `Chunk` helper
requires a `Level` compound; do not assume it can author every target's native save
schema. Bootstrap with the exact Minecraft server, place templates with a test-only
native adapter, settle lighting/updates, validate state, save, stop, reopen, and validate
again. Preserve nested item metadata, modern components, inventories and target data
versions. Do not relabel a newer save or add a general world converter.

Freeze mechanisms in a quiescent baseline, then clone and activate identically in each
attempt. All fixture generation, installation and initial validation finish outside
warm-up and measurement. Never modify live server files with Querz/NBT. For independent
content verification, compare runtime state against the recipe manifest, not just an
NBT read/write round trip through the same generator.

World fixtures: `idle`, `container-stone`, `container-meta`, `container-shulker-books`,
`container-bundle`, `navigation-terrain`, `cross-server-a`, `cross-server-b`, `mixed`.
The container variants use closed four-hopper circulation cells and an independent
activation switch. Verify that topology in the first native fixture experiment before
replicating it; if it cannot sustain transfers, fix the recipe and version its hash.

Initial execution defaults (engineering calibration inputs, not a production SLA):

| Parameter | Small | Medium | Stress |
| --- | ---: | ---: | ---: |
| Connected bots for player-dependent cases | 1 | 10 | 50 |
| Active navigation sessions | 1 | 10 | 50 |
| Shared waypoints | 1,000 | 10,000 | 50,000 |
| Lists / dimensions | 10 / 3 | 100 / 3 | 500 / 3 |
| Container circulation cells | 16 | 128 | 512 |
| Clients during unattended world cases | 0 | 0 | 0 |
| Read/suggestion requests per second, aggregate | 10 | 50 | 100 |
| Mutations per second, aggregate | 1 | 5 | 10 |
| Concurrent upload clients | 1 | 2 | 8 |
| Waypoints per valid upload fixture | 1,000 | 4,096 | 16,384 |
| Catalog mutations per second per backend | 1 | 5 | 10 |
| Synthetic inventory events per tick | 0, 10, 100 | 0, 100, 1,000 | Bounded ramp from 100 to 1,000 |

Distribute waypoints equally among the declared dimensions except a separate skewed-list
case holding 90% in one list. Include valid and invalid names/icons/metadata and denied
actions. Upload fixtures must remain within the recorded valid-transfer limits; add
separate limit-boundary/rejection cases instead of calling an oversized upload valid.
Honor cooldowns in successful throughput cases; deliberately exceed them in admission
tests. Saturation ramps have declared offered rates and deadlines, never unbounded loops.

Unattended cases must prove zero online players throughout warm-up/measurement,
target-appropriate chunk/region ticking, pause configuration, sustainable transfers,
and item conservation. No hidden control bot may keep the world active. Record actual
tick counts, physical transfers and event-dispatch counts separately. A synthetic
event does not prove a real hopper moved anything.

Calibrate available physical demand in the identical control world before comparing the
candidate. No movement in both processes is a fixture failure. Lower candidate throughput
caused by slow ticks is a performance failure, not grounds to discard the candidate's
sample. A stalled/overloaded load generator is an inconclusive sample with its own evidence.

## 6. Bots and cross-language wire validation

Azalea bots must establish real server-observed player entities. Each bot has a distinct
offline identity, role, offered rate and bounded action queue. Use normal client physics,
inventory operations and commands for native gameplay cases. Verify outcomes on the
server; a queued Rust action is not evidence of completion. Account for Azalea's
documented physics limitations when selecting/validating routes.

Driver modes: `vanilla`, `compatible`, `incompatible`. Vanilla omits mod negotiation;
compatible implements the audited release's custom payloads; incompatible uses a
deliberately wrong declaration. Verify Forge/NeoForge channel envelopes and configuration
handshakes separately from vanilla protocol compatibility. The audited release source
declares Server Waypoint protocol **2**, verified at its release revision; do not copy
the obsolete protocol numbers in older live-test runbooks.

Export golden valid/invalid handshake, chunk, upload, edit and catalog fixtures using
the release's production Java codecs. Rust must decode the Java fixtures and its encoded
messages must be accepted/rejected by those Java codecs as specified. Validate compression,
length/sequence/checksum limits, endianness and revisions. Do not bundle Java production
classes with native server probes or claim Rust directly executes the Java codecs.

Python communicates with the Rust process over line-delimited JSON on stdin/stdout;
logs go to stderr. Both directions include `schemaVersion: 1`, `runId`, `attemptId`,
`sequence` and a message type. Inputs are `start`, `action`, `barrier`, `quiesce`, `stop`;
outputs are `ready`, `action_result`, `packet_summary`, `rate_sample`, `stopped`, `error`.
Use action IDs to correlate results; reject stale, duplicate or out-of-order messages.
Do not subtract timestamps from unrelated process clocks to compute latency. Client
latency uses one client monotonic clock; server processing uses its own monotonic clock.

Stagger joins before steady-state samples; join bursts are separate cases. Readiness
requires the server's expected online count and negotiated capability per bot. Explicitly
disable automatic reconnect during disconnect assertions. Measure bot CPU/RSS and action
lag; release measurements require adequate independent load-generator capacity.

## 7. Native telemetry and probe interface

Java probes expose console commands with prefix `swperf`:
`hello`, `fixture place`, `fixture verify`, `fixture activate`, `fixture quiesce`,
`scenario start`, `scenario stop`, `snapshot`, `shutdown`. Arguments carry the run and
attempt IDs; structured `SW_PERF ` JSON lines carry replies/events. The controller
accepts only matching IDs, schemas and monotonic sequence numbers.

`hello` reports actual runtime/build, loaded audited-JAR identity, fixture adapter,
supported operations/metrics, owning-thread model and source/class fingerprints.
Snapshot assertions include inventories, task/session/display/map counts, queued
transfers, open owner stores where implemented, and expected disk outcomes after save.
Keep hot-path counters cheap: no recursive item serialization per transfer for telemetry.
The fixture/telemetry part must also boot with Server Waypoint absent for no-plugin world
controls. Isolate SW-specific class loading/assertions behind an optional adapter; do not
give the base probe a mandatory SW dependency. `hello` may report no audited JAR only
in an explicitly declared no-plugin control. A candidate reporting absence is invalid.

Measure actual tick processing boundaries, not callback spacing. Paper uses verified
native tick events where available; mods use the target loader's actual pre/post hooks.
Folia requires verified per-region processing boundaries or native region timing data.
If an adapter cannot obtain valid timing, report missing capability; a global callback
or repeating-task interval is not a substitute. Any observational agent must be pinned,
fingerprinted and present identically in matched controls, without bypassing feature work.

Each tick sample contains `regionId` where applicable, `durationNs`, `tickIndex`,
`phase`, operation counters and named-thread allocation deltas where supported. Collect
whole-process CPU, GC pause/allocation evidence and RSS separately. Thread-local allocated
bytes are never labeled process-wide allocation. Proxy operations use latency and queue/
CPU/allocation metrics, not TPS. Record sampler/probe overhead using matched controls.

## 8. Required coverage registry

`config/features.json` maps the following IDs to concrete scenario variants, source
entry points, native trigger, fixture, tiers, expected effects, metrics and budget IDs.
`config/scenarios.json` expands them without silently removing expensive cases.

| ID | Mandatory cases | Owning production sources |
| --- | --- | --- |
| `idle` | Zero players/sessions, dataset sizes, disabled settings, unrelated chat/items. | `WaypointServerCore`, platform runtime tick loops. |
| `world-container` | All four physical container variants, three sizes, zero clients, control/candidate, conservation and sustained activity. | Paper protection listener; mod inventory/item/hopper hooks discovered from release sources. |
| `inventory-regression` | Plain/meta items, empty/full/nested shulkers/bundles, direct/nested tags, nesting boundary, synthetic 0/100/1,000-event stages with/without navigation. | `PaperNavigationItemManager`, `NavigationProtectionListener`, `ModNavigationItemManager`. |
| `storage` | Waypoint/list add/edit/rename/remove/restore, revisions/conflicts, dimensions, snapshots, concurrent reads/mutations, save/reload/restart. | `WaypointFilesManagerCore`, `WaypointFileManager`, `WaypointRestoreRegistry`. |
| `commands` | Every implemented command leaf and suggestion provider, list layouts/filter/sort/paging/details, console/execute-as feedback, permissions/denials, local TP, config/language reload and logging. | `CoreWaypointCommand`, permission adapters, text/translation helpers, `PlayerActionLog`. |
| `navigation` | Every supported method individually and together; lifecycle, movement/arrival, retarget/edit/delete, disconnect/death/respawn/world transitions, persisted resume. | `NavigationService`, platform navigation handlers/listeners. |
| `map-terrain` | Warm/cold caches, exploration, boundary/dimension changes, multiple viewers, persistence/eviction/restart; verify map ownership/target. | `NavigationMapTerrainStore`, platform map cache/renderers. |
| `protection` | Enumerate every protection event/mixin in release source: click/drag/open/close, crafting/result/cartography, drop/pickup/spawn/move, item entities/frames, equipment/container/death and reconciliation where supported; tagged moves denied, innocent items retained, no duplicates. | Paper listener and loader registrations/mixins. |
| `sync` | Compatible/incompatible/unmodded joins, channels, full/incremental/list/dimension downloads, forced refresh, broadcasts, world identity and reconnect. | `PlatformMessageSender`, platform senders/channel listeners and C2S handlers. |
| `upload-edit` | Compression modes, valid multiframe traffic, merge/conflict policies, concurrent peers, size/checksum/header/sequence rejection, limits, timeouts, cancellation and disconnect. | `UploadCoordinator`, chunk buffers/registries, codecs, C2S handlers. |
| `sharing-icons` | Valid/invalid/unrelated/long chat, duplicate identities, permissions, Xaero/VoxelMap server protocol semantics and registered/invalid icons. | `ChatMessageHandler`, platform chat listeners and icon validation. |
| `transport` | Pairing/authentication, encrypted records, partial/invalid/replayed frames, rotation/reconnect, rate/queue saturation and cleanup. | `cross-server` transport/protocol, backend runtimes, proxy coordinator. |
| `catalog` | Snapshots/deltas/fan-out/filtering, remote suggestions/list/details, stale/offline peers and revision replacement. | Backend `CatalogPublisher`, `ClientCatalogSync`, `RemoteCatalogQuery`, proxy `CatalogDistributor`. |
| `handoff` | A/B/return, same-server destination, authorization/offline/expiry/duplicate/disconnect/cancel, simultaneous requests, exactly-once success. | Source/destination handoff services, proxy handoff registry, `VelocityRuntime`. |
| `mixed-soak` | All active mechanisms plus bots/navigation/mutations/upload/catalog/save, join/quit/reconnect cycles and recovery; bounded resource counts. | Combined backend/proxy topology. |
| `private-development` | Owner isolation/quotas, load/eviction, concurrent flush/reload/shutdown and collection identity only in development candidates. | `PrivateWaypointStore`, collection-aware `NavigationService` and lifecycle. |

Implementation must enumerate command leaves, event registrations, mixins, configuration
branches and background tasks from the release source into a coverage ledger. Each gets
a scenario ID or an explicit reason it has no distinct server execution/performance path.
Unmapped entry points fail registry validation. Performance sampling may group equivalent
command leaves, but every leaf still needs execution/correctness evidence.

Platform applicability comes from verified runtime capability and release sources.
Private is explicitly absent from published 4.0.0; client-only rendering is outside this
suite. Missing runtimes, unsupported probes or incomplete bots are blockers, not exclusions.

## 9. Measurements and decisions

Defaults: 30 seconds warm-up, 60 seconds measurement, three independent fresh-process
repetitions; 30-minute mixed soak per loader/API family. Brief PR smoke is labeled smoke.
Each independent repetition clones the same validated baseline and resets mutable data.
Store raw samples and nearest-rank p50/p95/p99/max, counts, units and measurement windows.
All repetitions must satisfy absolute budgets; never average away an offending run.

Initial budget IDs:

- `backend-medium`: p95 tick processing **<40 ms**, p99 **<50 ms**, per active Folia
  region, with requested work successfully sustained. Record overruns and wall throughput.
- `inventory-100`: ordinary untagged shulker-of-enchanted-books, 100 synthetic transfer
  events/tick and no navigation: incremental callback p95 **<=5 ms** and allocated bytes
  **<=2,097,152 per workload tick** against identical dispatch with SW listeners absent.
  This listener-removal run is diagnostic only. Never disable protection in a certified JAR.
- `relative-runtime`: regression above **25% and 0.5 ms** for latency, or above **25%**
  and an explicit per-scenario byte noise floor for allocation, against an approved healthy
  matching baseline. Report pair-level results; conflicting repetitions are inconclusive.
- `correctness-bounds`: any corrupted state, lost/duplicated items, unauthorized mutation/
  teleport, deadlock, watchdog, crash, unbounded queue or failed cleanup fails independently
  of average time. Saturation must reject within production's configured limits and recover.

Before execution, every concrete case declares offered rate, deadline, required metrics,
minimum valid samples, allocation scope and budget IDs. Other action/latency budgets are
calibrated on healthy matched controls and written to `config/budgets.json`; absence is
visible `INCONCLUSIVE`, never an implicit pass. Record the initial discovery measurements
even before final healthy baselines exist. The defective 4.0.0 release is not a healthy
baseline. Creating a healthy baseline requires evidence meeting absolute/behavior budgets;
the candidate cannot overwrite it automatically.

Use identical runtime builds, settings, fixtures, heap/GC/profiler settings and host class
for paired comparisons. A host health failure or inconsistent instrumentation invalidates
the pair. Report CPU model/OS/architecture/memory, server/Java flags, bot placement and
probe/profile overhead. Keep profiled diagnostic reruns separate from unprofiled gate
samples, or match instrumentation explicitly. Missing Folia timing is inconclusive.

## 10. Results, profiles and evidence

Result states are `PASS`, `FAIL`, `INCONCLUSIVE`, `BLOCKED`, `NOT_APPLICABLE`.
`NOT_APPLICABLE` requires a source-backed applicability reason. A missing result is
`BLOCKED`. Complete coverage and passing budgets are separate report axes; an original
release audit can be complete and failing. Independent scenarios continue after failure.

Each result includes schema/version, run/attempt/case IDs, mode, feature/variant/tier,
artifact name/hash, release/source identity, runtime/bot/probe/world hashes, clock/units,
measurement phase and durations, offered/completed counts, correctness assertions,
raw sample paths, budgets/observations, control/baseline references, decision reasons,
profile/log paths and cleanup outcome. Reject NaN/infinity, negative durations/counts,
missing units, stale IDs and unsafe artifact paths. Unknown schema versions are errors;
do not implement old-reader compatibility.

Keep local spark profiles and JFR with scenario markers; no automatic profile uploads.
Use capability-checked `spark profiler stop --save-to-file` (`sparkv` on Velocity),
or JFR as the recorded supported alternative. A sampler failure cannot erase measured
defects. Retain a diagnostic profile for each runtime feature family and each distinct
investigated failure. Retain full logs/crashes and interrupted sample metadata.

Output tree: `artifacts/`, `fixtures/`, `runtime-lock.json`, `attempts/<id>/`,
`profiles/`, `report.json`, `report.md`, `coverage.json`, `findings.json`, and
`environment.json`. Findings include mechanism, trigger, original/repaired hashes,
reproduction command, measured impact, correctness risks, source links and rerun outcomes.
Do not claim attribution of the user's production incident without incident evidence.

## 11. Repair sequence and later gates

First reproduce the known Paper 1.21.11 artifact with SHA-256
`0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57`.
Then complete the original-release feature/platform scan, preserving independent findings
and blockers before making source repairs. Record failure to reproduce honestly and
inspect environment differences; do not fabricate the expected defect.

For each reproducible issue, create a focused behavior regression and retain the failing
performance case, implement a minimal repair in the development source, rerun it plus
neighboring correctness checks, and finally run the required candidate matrix/soak.
Preserve item protections, permissions and inactive Stonecutter branches. Source changes
to Adventure feedback require its guide/translation conventions; GUI changes are outside
scope. No speculative cache, relaxed protection, or budget relaxation counts as a repair.

Only after that cycle add JMH, changed-path PR scenario selection and a full release
performance workflow. Unknown/shared changes select all relevant families, not zero tests.
Release and root Gradle publish paths must require reports for exact final JAR hashes,
required coverage, trusted worker class and fixture/budget revisions. Source/worktree
fingerprints cover production/build inputs and executable suite/config/fixture/budget
inputs; generated evidence and report-writing changes are excluded and separately
hashed so writing a report cannot invalidate its own measurements. Reports come from
required jobs in the same trusted workflow; arbitrary PR-supplied JSON cannot authorize
publication. Verification performs no upload. Actual publication still requires explicit
user authorization. Manual uploads outside these paths are beyond the gate's control.

## 12. Acceptance evidence

The [acceptance checklist](../validation/2026-10-24-acceptance-checklist.md) starts
unchecked. Every implementation task must identify its new tests and actual runtime
evidence; compilation, dry runs, NBT serialization and simulated clients are not live
feature coverage. Documentation completion does not mark any runtime item complete.
