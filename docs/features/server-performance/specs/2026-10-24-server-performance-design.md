# Server-side performance suite — approved design

Date: 2026-10-24

Status: approved by the user on 2026-10-24. The user requested different load tiers and
comprehensive testing of the current release first; future PRs may select relevant tests.
Azalea is the selected bot framework. Numerical defaults are initial calibration policy,
not an established production SLA. No implementation or resolved defect is implied.

The [implementation specification](2026-10-24-server-performance-spec.md) and
[staged implementation plan](../plans/2026-10-24-server-performance.md) make the
approved direction concrete. They distinguish implementation choices from measured facts.

The user also selected [Querz/NBT](https://github.com/Querz/NBT) for creating test-world
fixtures. This is a required test-tool dependency, outside the production artifacts.

## Intent

First scan the current published release for existing performance issues across all its
server-side features, then repair reproducible issues and repeat the same measurements.
Retain profiles and results, and make these checks reusable for future changes. Subsequent
release enforcement must prevent shipping artifacts with demonstrated defects; targeted
future PR tests are an optimization after the comprehensive initial audit. A finite suite establishes
performance under its recorded workloads; it cannot prove the absence of every possible
performance issue.

The initiating investigation is the chat **Find Paper 1.21.11 TPS cause**,
thread `01a16f1a-0ec1-7681-9cef-990c8847a6e1`. Its local evidence is at
`/Users/mini/.codex/visualizations/2026/10/24/01a16f1a-0ec1-7681-9cef-990c8847a6e1/paper-tps-investigation/`.
The implementation must preserve a repository-owned summary of the reproduction so
future users do not depend on this machine-specific evidence path.

The investigation measured ordinary, untagged shulker transfer events on Paper 1.21.11.
At 1,000 synthetic events per tick the production listener consumed about 86 ms and
allocated 364 MB per tick; removing the plugin's listeners eliminated that measured cost.
This demonstrates a defect under controlled traffic, not attribution of a production
incident or a realistic count of physical hopper transfers.

## Current release and infrastructure

The latest published GitHub release was verified as **4.0.0**, published
2026-10-15, targeting `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`, with 44 JAR assets:
13 Fabric, 13 Forge, 13 NeoForge, 4 Paper, and 1 Velocity. Record the asset manifest
and hashes before execution. See [verified release scope](../validation/2026-10-24-release-scope.md).

The active development branch is newer and still labels its artifacts 4.0.0. Therefore
neither a matching filename nor `mod_version=4.0.0` proves that a local build is the
published release. The first audit must use downloaded release assets or local copies
whose hashes match the published assets. It must not rebuild HEAD and call that a
current-release audit. Preserve immutable original copies before making repairs.

- `common`, `cross-server`, `proxy-common`, `paper`, and versioned `mods` already have
  JUnit infrastructure. These correctness tests do not impose performance budgets.
- `paper/build.gradle.kts` has a separate development-only Folia load source set.
  `tools/folia-live-test` has isolated preparation and deterministic transport fixtures.
  Its deliberate region blocking is a fault injector, not a performance measurement.
- `tools/proxy-lifecycle-test` provides an isolated Velocity/two-backend pattern.
  Existing game probes and transport tests can inform adapters; their old runbooks and
  artifact counts are not authoritative release manifests.
- `.github/workflows/build.yml` builds and collects artifacts. The release-draft
  workflow checks packaging but has no performance requirement before creating a draft.
  Root Gradle publishing tasks also have no performance evidence requirement.
- Derive the initial audit targets from the published asset manifest and the published
  release revision's Stonecutter configuration/metadata. Use current HEAD's matrix and
  collector only for later development/candidate runs. Do not infer runtime coverage
  from compilation or assume the two manifests remain identical.
  Current active projects are `26.1.2-fabric` and `26.2-paper`; use fully qualified
  verification tasks so performance runs do not change those selections.
- Test and repair source must use Java 17-compatible language features. Minecraft
  runtime/toolchain requirements remain target-specific. Use Gradle Kotlin DSL and GSON
  for Java-side JSON. Test code must stay outside production/release source sets.
- Retain existing version branches and unrelated work. No compatibility layer, commits,
  publishing, or production-server modifications are part of this proposal.
- Private owner storage and collection-aware navigation exist only in newer development
  work, so exclude them from the 4.0.0 audit with an explicit release applicability reason.
  They belong in a separate development coverage registry when the suite is applied to
  this branch. Do not confuse an unimplemented future feature with a missing test of a
  feature that does ship in 4.0.0.

## Approaches

1. **Comprehensive runtime audit, then a layered regression suite — recommended.** First
   exercise every current-release feature/platform with tiered real-server workloads,
   retain findings, and repair measured defects. Then add fast component benchmarks and
   relevant PR selection around those same scenarios, followed by full release gates.
   This addresses existing problems before investing in CI optimization. The audit may
   take hours and needs provisioned server binaries and controlled workers.
2. **Full runtime suite on every PR.** Run the complete release matrix for each change.
   This catches platform-specific regressions earlier but has substantially higher
   setup, compute, and feedback costs. The measurement design is otherwise identical.
3. **Real-server suite only.** Exercise all features in running servers and retain
   profiles, without component benchmarks. This reduces harness types but makes small
   regressions harder to isolate and every diagnostic iteration more expensive.

Decision: the user approved comprehensive current-release discovery before targeted
future PR testing, with multiple tiers and the selected libraries. Numerical defaults
below remain subject to evidence-based calibration on the controlled worker.

## Architecture

### Component measurements

After the live current-release audit is executable, use separate benchmark source sets
for the production APIs in `common`, `cross-server`,
and `proxy-common`. Prefer JMH with JVM forks, warm-up, consumed results, and allocation
measurement over timing assertions inside JUnit. Pin the benchmark dependency when
implemented. Keep file fixtures and mutation reset outside measured CPU-only operations;
measure disk/persistence explicitly as separate workloads. Exercise real transport
loopback sessions for encryption, distribution, and backpressure benchmarks.

These source-built checks locate algorithmic and allocation regressions. They cannot
certify the published binary unless explicitly tied to verified identical production
classes, and cannot substitute for
Minecraft runtime measurements, Bukkit metadata behavior, or platform scheduler checks.

### Real-server workloads

Use a Python standard-library orchestrator under `tools/server-performance/` and explicit
Gradle verification tasks for test-only Java probes in their owning projects. Probes
must call production entry points; they must not reimplement waypoint algorithms.
Paper inventory tests must use actual server-created item stacks and registered event
listeners. Mod adapters must execute through the production loader hooks/mixins.

Every run creates a new disposable root with isolated worlds, configurations, ports,
identities, credentials, fixtures, logs, and artifact copies. Bind test servers to
loopback and launch only test-owned processes. Pin server, loader, Java, profiler, and
bot versions and hashes. Cache immutable downloads separately from writable runtime
trees. Refuse production or existing runtime directories. Record the prerequisites and
EULA configuration used to provision disposable Minecraft test servers.

#### Deterministic test worlds and unattended activity

World fixtures are a required part of the suite, independent of the bot framework.
Use a standalone Java 17-compatible tool under `tools/server-performance/worldgen/`,
built with Gradle Kotlin DSL and [Querz/NBT](https://github.com/Querz/NBT), to construct
the fixtures. Keep this tool and its dependency outside `common` and all six production
modules; world generation is test tooling, not a shared backend helper. Use GSON for its
JSON recipe/manifest handling and Querz/NBT for binary NBT data.

Maintain versioned construction recipes, seeds, block coordinates, inventory contents,
waypoint datasets, and fixture hashes. Generate or provision each world's save for its
target runtime; do not assume a save produced by a newer Minecraft version can be
opened by an older one. Clone/reset the prepared fixture before independent runs and
use the same initial state for candidate and control. Pre-generate normal measurement
areas; measure cold generation/exploration separately.

##### Querz/NBT generation and format validation

On 2026-10-24 the latest upstream release was verified as
[6.1](https://github.com/Querz/NBT/releases/tag/6.1); the inspected `master` revision was
`9133f65fbf8bb5abb49372bb04c3f8305ed70d2c`. Upstream documents the dependency as
`com.github.Querz:NBT:6.1` through JitPack. Treat it as the candidate pin; verify resolution,
artifact checksum, and the chosen APIs during implementation. Do not use a floating
branch or add this dependency to production shading/packaging.

The library's generic NBT serialization is distinct from Minecraft's version-specific
world schema. Its inspected high-level `net.querz.mca.Chunk` implementation requires a
root `Level` compound and reads `Sections` and `TileEntities` below it. Do not use those
helpers as if they certify all supported runtime save formats, or patch a newer save's
`DataVersion` to make it appear to belong to an older runtime.

Use the generic `CompoundTag`, `ListTag`, and NBT serialization APIs to generate
target-appropriate vanilla structure templates containing palettes, block positions,
hopper/container block entities, and inventory payloads. Bootstrap a new world with its
target server and seed, place the generated templates with a test-only native adapter,
and let that runtime serialize the final chunk/region data. This creates the actual test
world using Querz/NBT fixture data while delegating canonical world saving to Minecraft.
No general save converter or product compatibility layer is needed.

All generation, placement, lighting/neighbor-update settling, fixture validation, and
canonical saving must finish before benchmark warm-up. Quiesce active mechanisms before
freezing the baseline, then start activity through the same declared procedure in each
candidate/control run. Querz/NBT must never edit files owned by a running server; offline
edits are limited to disposable preparation copies and must be followed by validation.

Validate each fixture by loading it in the exact target runtime and checking server-side
block states, hopper direction/enabled state, inventory contents, nested item metadata,
entity counts, and fixture bounds against the GSON manifest. Save, stop, reopen, and
repeat those checks before accepting the reusable baseline. An NBT round-trip in the
generator alone is insufficient evidence. Record the library artifact hash, recipe and
template hashes, target data version, seed, native adapter version, and frozen save
manifest. Later runtime samples must additionally prove actual ticking and transfers.

Reference: [inspected high-level chunk implementation](https://github.com/Querz/NBT/blob/9133f65fbf8bb5abb49372bb04c3f8305ed70d2c/src/main/java/net/querz/mca/Chunk.java).

Required world fixtures:

- **Idle:** an otherwise quiet world, with empty/small/large waypoint datasets and zero
  connected players or navigation sessions. Measure background work and retained state.
- **Unattended container traffic:** bounded banks of real hoppers and containers moving
  ordinary items, metadata-heavy items, full shulkers, and bundles where the target
  supports them. Use sustainable routes and sufficient stock so traffic persists through
  warm-up and measurement. Assert expected transfers, item conservation, and initial/
  final inventory contents. Positive navigation-item protection cases are separate from
  ordinary-item cost cases and include their required session/owner setup.
- **Navigation terrain:** prepared routes, terrain, multiple regions/dimensions, and map
  cache boundaries for moving/stationary bots and repeated cache/terrain transitions.
- **Cross-server:** independent A/B worlds with distinct fixtures and waypoint catalogs
  for transfer, isolation, disconnect, and recovery workloads.
- **Mixed activity:** the container world plus tiered bots, navigation, mutations,
  uploads, and saves, to expose contention hidden by isolated feature measurements.

Every small/medium/stress world-activity tier must have an unattended variant with
**zero connected clients**, scaled by active fixture size and achieved operations per
second rather than by player count. Idle-only cases remain separate. Bots may be used
for setup but must disconnect before the unattended warm-up; server-side telemetry must
confirm zero online players throughout the measurement window.

Provision target-appropriate chunk loading/ticking and empty-server pause settings for
active unattended cases, including independently owned regions on Folia. Record those
settings identically for candidate and control. Prove actual tick progression and
container movement from counters and independent state assertions; a world file or a
loaded chunk alone is not proof that the mechanisms ran. If activity stops, inventories
empty, a server pauses, or a required region stops ticking, invalidate the sample and
report a fixture failure rather than a performance pass. Teardown removes only the
test-owned runtime root's active resources.

The previous Paper reproduction used synthetic transfer-event dispatch with no clients.
Retain it as a focused regression, but do not report its dispatch count as a measured
physical hopper rate. The unattended world fixture supplies the separate real-mechanics
measurement. Compare the same world activity with and without Server Waypoint enabled
to distinguish base-game cost from the plugin's incremental cost.

Use actual protocol connections from Rust bots built with
[Azalea](https://github.com/azalea-rs/azalea), as selected by the user. Use its swarm
facilities to run the 1/10/50 connected-player tiers. Keep the bot crate under
`tools/server-performance/bots/`, outside the Java modules and production artifacts.
The Python orchestrator owns the bot process and scenarios; Java runtime probes remain
Java 17-compatible. Azalea bots handle commands, movement, inventory actions, connection
lifecycle, and packet observations. A Server Waypoint extension handles its custom
payload negotiation, downloads, edits, and uploads. A fake player object does not satisfy
a live-player scenario. Validate server-observed join counts, negotiated channels,
successful operations, and workload throughput before accepting a performance result.

#### Azalea version selection and workload control

Azalea targets one Minecraft version at a time. As verified on 2026-10-24, upstream
`main` at `0d38e0dcf47eb258c41b1b09ebc2bcde7253e185` documents Minecraft 26.2;
the published [0.15.1+mc1.21.11 documentation](https://docs.rs/azalea/0.15.1%2Bmc1.21.11/azalea/)
provides a candidate for the first Paper 1.21.11 audit. That candidate has not yet been
built or connected in this suite. Do not use floating `main` or assume the latest crate
connects to all Minecraft versions in the release matrix.

- Maintain a runtime-to-Azalea mapping with an exact crate version or immutable Git
  revision, dependency lockfile, pinned Rust toolchain, and bot binary hash. Use separate
  builds where protocol versions require them. Pin nightly Rust locally to the bot
  project when required; do not change the developer's global default toolchain.
- Verify native connection, login/configuration, movement, inventory actions, custom
  payload delivery, and reconnect/Velocity transfer behavior for each required runtime.
  Historical releases/revisions are candidates until these checks pass. A missing native
  mapping is a coverage blocker. Do not introduce a translation proxy or claim another
  runtime passed merely because a bot connected somewhere else.
- Ordinary clients omit Server Waypoint negotiation. Protocol-aware clients explicitly
  register/negotiate the tested release's channels. Keep incompatible-handshake scenarios
  separate. Rust wire handling must be checked against deterministic packet fixtures
  produced/consumed by that release's production Java codecs, including compression,
  framing, revisions, and rejection cases. Do not assume Rust can directly reuse Java
  codecs, or label an Azalea bot a real Xaero/VoxelMap client.
- Use isolated offline identities for disposable local servers, deterministic per-bot
  roles and paths, staggered joins, bounded action rates, and acknowledgement/deadline
  checks. Separate login bursts from steady-state workloads. Recheck expected player
  counts after disconnects and transfers; automatic reconnection must not hide failures.
- Emit structured bot readiness, action, packet, rejection, completion, and failure
  events for the orchestrator. Begin timed samples only after readiness and warm-up.
  Record achieved rates and response counts so stalled bots cannot produce a false
  fast result. Swarm world sharing does not merge server-side player connections.
- Build optimized bot binaries and measure bot CPU, memory, and scheduling lag separately
  from server metrics. Use a separate load-generator host for controlled release runs
  when necessary; local loopback runs must record shared-host contention. Preserve
  required protocol/physics processing and configure pathfinding consistently rather
  than silently disabling behavior to reach a player-count target.

Azalea's documented physics has limitations, including entity pushing and sprint
swimming. Scenarios depending on those behaviors require independent server-observed
assertions before being accepted as coverage. Movement and container workloads must
check resulting server state, rather than assuming sending an action makes it happen.
This suite still does not certify client rendering or human input.

References: [upstream README at the inspected revision](https://github.com/azalea-rs/azalea/blob/0d38e0dcf47eb258c41b1b09ebc2bcde7253e185/README.md),
[Azalea version policy](https://github.com/azalea-rs/azalea#non-goals),
[Minecraft 1.21.11 Azalea API](https://docs.rs/azalea/0.15.1%2Bmc1.21.11/azalea/).

Synthetic events isolate listener cost; a separate real hopper/container fixture must
exercise actual transfer mechanics. Report physical transfer counts separately from
synthetic dispatch counts. Likewise, map fixtures distinguish stationary cache hits,
exploration, dimension/region transitions, and cold terrain without allowing unrelated
world generation to dominate the normal navigation case.

Folia workloads must use the owning entity/region scheduler, test multiple separated
regions, and report each loaded region. A global scheduler callback or server-wide
average cannot certify region tick behavior. Velocity has CPU, allocation, queue, and
handoff latency measurements; TPS is not its performance metric.

### Coverage registry

Maintain a machine-readable registry for each tested release/source revision mapping
each implemented server feature to
scenario IDs, owning sources, platform applicability, correctness assertions, load
tiers, measurement units, and budgets. Compare the selected run with the required
registry: missing results, setup failures, timeouts, absent metrics, and unsupported
required adapters fail the gate. An explicitly inapplicable feature is different from
an unexecuted applicable feature. Tests should detect removal of required scenarios.

The initial registry must include every row below and enumerate the command tree and
event handlers during implementation to avoid treating this table as exhaustive API
discovery. Every performance repair includes a focused behavior regression test.

| Feature family | Required workloads and correctness assertions |
| --- | --- |
| Idle and disabled paths | No players, no sessions, many stored waypoints; disabled features; unrelated chat and inventory traffic; no unbounded recurring work. |
| Unattended world activity | Zero clients throughout measurement; active real hopper/container fixtures at small/medium/stress sizes; verified ticking, sustained transfers, item conservation, and matching candidate/control world settings. |
| Shared storage | Add/edit/rename/remove waypoint and list, restore, revisions/conflicts, dimensions, snapshots, save/reload/restart, large datasets, mutation/read contention; independent persisted contents. |
| Private storage (development follow-up; absent from published 4.0.0) | Many owners, owner-isolated reads/mutations, configured quotas and rejection, lazy load/eviction, flush/reload/shutdown contention; no cross-owner access or lost writes. |
| Commands and feedback | All implemented command families, list/search/filter/sort/paging/details, tab completion, help, permissions/denials, local teleport, config/language reload, styled feedback and action logging. |
| Navigation lifecycle | Start/retarget/stop, movement and arrival, stale/deleted targets, reconnect/death/respawn, world changes, session save/restore and cleanup; collection identity where implemented stays correct. |
| Every navigation method | Compass, map, bossbar, actionbar, text display individually and allowed combinations; stationary and moving players; entity/packet/item counts and proper teardown. |
| Terrain maps | Cold/warm cache, multiple viewers, exploration, region/dimension transitions, terrain persistence/eviction/reload; bounded storage and correct visible target/map ownership. |
| Item protection | Plain/meta-heavy items, empty/full/nested shulkers and bundles to the supported depth, tagged/nested navigation items, transfer/click/drag/pickup/drop/spawn/slot-change/death/reconciliation handlers; no item escape, duplication, or innocent-item deletion. |
| Client synchronization | Compatible/incompatible/unmodded joins, full/incremental dimension/list sync, downloads, broadcast fan-out, channel unregister/rejoin, world identity messages; delivered revisions/contents and isolation. |
| Upload/edit protocol | Compressed/uncompressed multiframe traffic, edit conflicts, merges, concurrent peers, checksums/headers/size limits, admission/backpressure, timeouts, cancellation, disconnect; exact committed changes and released buffers/leases. |
| Chat sharing and integrations | Valid, unrelated, malformed, and long chat messages; permissions; server-side Xaero/VoxelMap upload/download semantics and icon validation; expected feedback and state. Client map rendering is outside this suite. |
| Cross-server transport | Pairing/authentication, encrypted frames, reconnect/rotation, partial/invalid traffic, per-peer limits and saturation; bounded queues, no blocking game/region thread, correct recovery. |
| Catalogs and remote commands | Snapshot/delta publication, fan-out, filtering, remote suggestions/list/details, stale/unavailable peers, snapshot replacement; visibility and revision correctness. |
| Handoffs and Velocity | A-to-B transfers and return, same-server remote teleport, authorization/denial, offline destinations, expiry/duplicate/disconnect/cancellation, simultaneous transfers; exactly one authorized completion and cleaned coordinator/backend state. |
| Combined load and soak | Navigation plus container traffic, mutations, uploads, catalog churn, save/reload, join/quit cycles, reconnects; no leaked sessions, tasks, entities, buffers, files, or steadily growing retained state. |

### Initial workload tiers

| Tier | Connected players | Shared waypoints | Active navigation sessions | Inventory sensitivity stages |
| --- | ---: | ---: | ---: | --- |
| Small | 1 | 1,000 | 1 | 0, 10, 100 synthetic events/tick |
| Medium | 10 | 10,000 | 10 | 0, 100, 1,000 synthetic events/tick |
| Stress | 50 | 50,000 | 50 | Increasing rate until saturation, then recovery |

These are the bot-dependent tiers. The required world fixtures also run separate
zero-player tiers, with fixture size, physical operations/second, and observed event rate
recorded explicitly. A passing 1/10/50-player matrix cannot substitute for these cases.

Distribute waypoints across multiple lists/dimensions, include skewed large lists, and
exercise private owners below, at, and beyond their configured limits. Traffic rates for
commands, uploads, and catalog changes must be explicit per scenario; do not derive an
unbounded loop from player count. Stress beyond supported admission limits must measure
bounded rejection and recovery rather than demand unlimited successful throughput.

The initial 4.0.0 audit must run all applicable feature families at small/medium tiers on
every published server artifact's declared runtime anchor, plus the stress/soak coverage
below. Begin with Paper 1.21.11 to establish the known failing control, then expand to
all other feature families and platforms before calling the audit comprehensive.

Future PR defaults: component checks and relevant runtime scenarios selected by changed
ownership, always retaining mandatory idle/container regressions for affected item paths.
Future release defaults: all applicable feature families at small/medium tiers on every
published server artifact's declared runtime anchor, and stress/soak on each loader/API
family plus Folia and the Velocity/two-backend topology. Use the source matrix to select
additional runtime anchors where a published version range crosses relevant API changes.

A representative run is labeled representative; it is not evidence that all targets
passed. If required runtime binaries, clients, or workers are unavailable, report blocked
coverage and keep the release gate closed. Do not claim arbitrary versions within an
artifact's supported range were measured when only its recorded anchors were run.

## Measurement and provisional budgets

Measure tick processing time rather than time between scheduled callbacks or rolling TPS
alone. Retain p50/p95/p99/max, overruns, steady-state throughput, whole-process CPU and
allocation/GC, scenario latency, bytes/packets, and peak/post-cleanup retained resources.
Thread allocation counters cover only their named thread; include workers separately
and use JFR to investigate process-wide pressure. State unsupported metrics explicitly.

Use a frozen, adequately provisioned worker class for absolute release budgets; no
competing server workloads on the same worker during timed samples. Record CPU, OS,
architecture, memory, JVM flags/heap/GC, server settings, fixture hash, and affinity where
available. A host health/control failure invalidates the run instead of passing it.

Initial policy, subject to calibration on that worker:

- Warm up each runtime scenario for at least 30 seconds, then measure 60 seconds in
  three independent fresh-process runs. Use declared shorter PR smoke settings without
  treating those as release evidence. Component runs use independent JVM forks.
- Medium steady-state backend load: tick processing p95 below 40 ms and p99 below 50 ms;
  record per-region metrics on Folia. Sustained TPS loss or an exhausted workload deadline
  fails even when a sample percentile happens to pass.
- Known inventory regression: 100 ordinary, untagged shulker-of-enchanted-books transfer
  events per tick with no navigation must add no more than 5 ms p95 processing and
  2 MiB allocation per workload tick versus the same event-dispatch control. Repeat
  with navigation active and retain the 1,000-event sensitivity stage. These are proposed
  limits, not measurements demonstrating a feasible repair.
- Compare candidate and approved baseline with identical fixtures, server/profiler
  versions, hardware class, flags, and operations. Interleave repeated paired runs.
  A runtime latency increase above 25% and 0.5 ms, or an allocation increase above 25%
  and the scenario's explicit noise floor, fails pending investigation. Define separate
  appropriate units/noise floors for nanosecond component operations and network rates.
  Use confidence/variance checks; unstable runs are inconclusive and block a release.
- Run a 30-minute mixed-load soak per API family. Check post-quiescence resource
  counts against expected baseline counts; assess retained heap across repeated identical
  cycles rather than comparing two noisy heap readings. Define bounded queue/state
  limits from production configuration and fail leaks, deadlocks, watchdogs, or corrupt
  state regardless of average speed.

Budgets must include all runtime overhead needed to deliver a feature. A no-plugin
process is a useful environmental control; removing listeners is a narrow diagnostic
control only. It must never replace the candidate measurement or disable protection in
the artifact being certified. Synthetic positives must assert denied moves/cleanup.

The current defective build is a known-failing control, not an approved healthy baseline.
First establish its failure, repair it without relaxing the budget or behavior assertions,
and calibrate an initial healthy baseline against absolute budgets and control runs.
Later baseline/budget changes require an explicit reviewed change with evidence; a failed
candidate must not overwrite its baseline or automatically broaden tolerances.

## Profiles and artifacts

Use direct measurements for gates and spark/JFR for attribution. Sampling percentages
alone cannot establish a latency/allocation budget. Separate profiled diagnostic reruns
from the unprofiled timing gate, or explicitly match profiler settings in both runs.
Record engine availability and overhead. Preserve a profile for each release scenario
family and every investigated failure, with scenario markers and exact workload windows.

Spark supports CPU, allocation, thread selection, and local file output using
`profiler stop --save-to-file`; Velocity uses `sparkv`. Pin and capability-check the
installed profiler rather than assuming every bundled version has identical flags.
Start the profiler without an automatic upload timeout, stop/save locally under an
orchestrator-enforced deadline, and ensure cancellation/teardown on failure. Where an
allocation sampler is unavailable, retain JFR and allocation-counter evidence and label
the limitation. Missing required diagnostic evidence must be explicit.

References: [spark command usage](https://spark.lucko.me/docs/Command-Usage) and
[Paper profiling guidance](https://docs.papermc.io/paper/profiling/).

Each run writes structured results and a concise Markdown report with scenario/feature
coverage, operation counts, units, thresholds, percentiles, raw samples, correctness
outcomes, profiles, logs/crashes, provenance, and an unambiguous pass/fail/inconclusive
status. Use monotonic timing. Output must survive failure or interruption.

## Later release enforcement

This work follows the first comprehensive audit and its evidence-backed repair cycle.
Do not substitute CI configuration or a passing subset for the requested current-release
investigation. The audit runner and reports must be usable independently of GitHub.

Build final artifacts first, copy and hash them into the run, and test those copies.
Generate a report binding source revision/worktree fingerprint, every tested artifact
SHA-256, runtime anchor, suite/fixture/budget revisions, runner identity, and scenario
coverage. A source revision by itself is insufficient to identify an artifact.

The release-draft workflow must run the required suite or consume verified results from
its own required jobs before creating a draft. Upload reports on both success and failure.
Before any upload, publishing tasks must validate required coverage and the exact hashes
of the final files they are about to publish. Missing, stale, incomplete, inconclusive,
wrong-hardware, or mismatched results fail closed. A local gate cannot prevent an operator
from uploading files outside these workflows; document that boundary.

PR quick checks are not release authorization. Shared hosted PR workers can provide
diagnostics and relative comparisons, but must not silently substitute for the approved
release worker. Do not require repository secrets for untrusted PR benchmark jobs, and
do not execute untrusted PR code on a privileged production-adjacent self-hosted worker.

## Repair and verification workflow

1. Freeze the published 4.0.0 asset manifest and preserve hash-verified originals.
   Implement result validation, fixture generation, owned-process lifecycle, metric
   collection, and the known inventory reproduction; prove the known-bad case fails.
2. Expand the runner to all 4.0.0 feature families/platforms and different tiers; retain
   an original-release audit report with failures, profiles, coverage gaps, and setup
   blockers. Do not modify the release copies to make this report pass. A known failure
   does not abort discovery of unrelated features; continue gathering their results.
3. For each demonstrated issue, retain a failing reproduction, make a focused source
   repair with version-specific API verification, and rerun the same workload plus
   meaningful behavior checks. Preserve nested-item protections and authorization.
   Distinguish original release, rebuilt development source, and repaired candidate.
4. Run small/medium coverage and stress/soak against repaired candidates. Establish
   healthy baselines only from reviewed correct results meeting absolute budgets.
   Then add component benchmarks and targeted future PR selection.
5. Wire release/publishing enforcement only after the audit and reports are executable.
   Test gate rejection for missing scenarios, changed JARs, stale fingerprints, malformed
   results, interrupted runs, absent metrics, and deliberately slow/broken controls.
6. Run the final required matrix against final release artifact copies; retain evidence
   and report outstanding targets/issues honestly. Never claim coverage from a dry run.

Repair base: use the current development branch for focused source repairs,
preserving its unrelated Private work. Label these outputs development/repaired candidates,
never patched published 4.0.0 binaries; include applicable Private regression checks for
this newer source. Original-release audit adapters must target the published APIs and
must not bundle a second copy of production classes into the server. If a separate
maintenance release is later requested, applying repairs to its source revision is a
separate integration decision; it does not delay discovery of current-release issues.

The initiating Paper artifact's recorded SHA-256 is
`0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57`, matching
the published asset metadata. Reverify the bytes in the new run and reproduce the
failure. If it does not recur, retain that result, inspect workload/runtime differences,
and continue other feature tests; never fabricate a failure or substitute another build.

Azalea is the user-selected bot framework; Querz/NBT is the user-selected test-world
fixture library. Specific adapter layouts, exact dependency/
toolchain pins for each runtime, per-operation budgets beyond those above, and server
provisioning details are builder decisions to be resolved in the
implementation plan and verified with small experiments. Use the proposed small/medium/
stress tiers as initial calibration targets; the user asked for different tiers rather
than supplying one production size. Final budget policy and execution costs remain
visible review decisions; thresholds must not be described as an established production SLA.
