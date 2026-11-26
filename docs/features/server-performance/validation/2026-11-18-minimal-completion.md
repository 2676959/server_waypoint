# Minimal Paper performance suite completion

Completed November 18, 2026. All seven required cases passed on the pinned
development candidate running Paper 1.21.11 build 111. The five critical groups
have complete evidence, all six bounded tick runs meet their budgets, and none
of the required cases is FAIL or BLOCKED. Command-tree PASS covers delivery,
permission correctness and complete timing observations. The plan sets no
command-tree latency budget. [Minimal plan](../plans/2026-10-24-server-performance.md)

## Candidate and retained evidence

| Input | Identity |
| --- | --- |
| Candidate source | `0ea2d348df673cb9495a8f45b1d7e19a620a790f` |
| Candidate JAR SHA-256 | `a9b21e37d2deaddcea29c22e8763e6ac5b8ab65abf946b201fd50c3602c77915` |
| Paper 1.21.11 build 111 SHA-256 | `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3` |
| Java 21 executable SHA-256 | `04005388bac0c272ea914210ca519ce94b2f873ea3962b9874a6859f74d7f279` |
| Functional probe / Azalea SHA-256 | `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9` / `95f5bb03dde1b4b6efe7c6275572be0d1f47c25398e4c4a4b97c62bfc41bf887` |
| Command-tree and bounded probe / Azalea SHA-256 | `5e66454735cefc0eb0945f4b1e4c43b36d6e2f90b93dd4b40ec4012e58d025e9` / `bff7a17c5a3a4e62046565da786dd0e6613fdf8bb956cb6957f00224c9cac243` |

The three executions used byte-identical candidate JARs and the locked Paper
and Java runtimes. The command-tree instrumentation required a new probe and bot
after the functional run. Candidate provenance and the cached production JAR live
under `/Volumes/ssd/sw-performance-minimal-20261115-inputs/`. The Step 3/4 probe
and bot binaries live under `/Volumes/ssd/sw-performance-minimal-step3-20261116-inputs/`.
The earlier functional bot hash is recorded in its runtime lock and attempt
bindings. The executable at that lock's path now contains the later bot build,
so the functional bot identity is based on those retained records.

| Execution | Retained root | Run ID |
| --- | --- | --- |
| Functional | `/Volumes/ssd/sw-performance-minimal-step2-20261115-r4/` | `cb60e4cd-10e3-4485-9fe5-bc215a13f873` |
| Command tree | `/Volumes/ssd/sw-performance-minimal-step3-20261116-r2/` | `9badd572-4d89-4258-8a34-71edab04ec47` |
| Bounded timing | `/Volumes/ssd/sw-performance-minimal-step4-20261116/` | `81666b7f-b27b-4e9c-9088-25f60bc62e07` |

Each root retains `report.json`, `results/`, fixture and environment seals,
runtime lock and attempt source snapshots. The reports below index the exact
fixture hashes, attempts, raw receipts, native observations and independent
expected values. [Candidate binding](2026-11-15-minimal-execution.md) ·
[Functional](2026-11-16-minimal-functional.md) ·
[Command tree](2026-11-16-command-tree-delivery.md) ·
[Bounded timing](2026-11-16-bounded-performance.md)

## Five critical groups

| Group | Outcome | Evidence |
| --- | --- | --- |
| Storage and permissions | PASS | Create, edit, remove and restore matched expected contents. Explicit edit denial preserved committed state. Saved and reopened contents matched. |
| Navigation lifecycle | PASS | All six methods started. Retarget, active-target deletion and removal of its resume state, restart, stop and disconnect matched native and client expectations. The login-scoped task cleared on disconnect. |
| Terrain maps | PASS | Independently prepared 16,384-byte rasters matched in both regions, during two-owner isolation and after save/reopen. Client reconstruction matched expected scale and checksum. |
| Direct item protection | PASS | Inventory moves, chest interactions and drop/pickup paths enforced direct protection and conserved ordinary items. |
| Command-tree delivery | PASS | Twelve matched native submissions and client installations across three fresh Paper processes. Ordinary login lacked `/wp tp` and the grant added it. Operator login included it and the explicit revocation removed it. The other expected branches matched. |

The four functional cases cover 24 named assertions. [Functional observations](2026-11-16-minimal-functional.md)

## Command-tree sessions

The empty-catalog fixture tested ordinary and operator connections sequentially.
Each connection observed login and one controlled permission refresh. Native
time spans `Commands.sendCommands` entry through connection submission, including
Paper's asynchronous tree preparation. Client login time includes connection and
configuration. Client refresh time spans the request through installation in the
test driver's decoded graph. The clocks are measured independently. Packet size
covers the encoded command body.

| Session / PID | Matched receipts | Native ticks sampled | Max tick processing ms | Max sync-to-submission ms | Outcome |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 / 84922 | 4/4 | 59 | 17.177 | 75.191 | PASS |
| 2 / 84987 | 4/4 | 51 | 28.633 | 100.686 | PASS |
| 3 / 85047 | 4/4 | 46 | 19.190 | 79.028 | PASS |

| Role / trigger | Native median / max ms | Client median / max ms | Body bytes | Nodes |
| --- | ---: | ---: | ---: | ---: |
| Ordinary login | 152.234 / 176.948 | 896.098 / 1127.213 | 74,180 | 4,367 |
| Ordinary refresh | 116.698 / 135.201 | 142.463 / 171.050 | 74,300 | 4,372 |
| Operator login | 100.294 / 139.743 | 276.346 / 279.021 | 107,934 | 6,489 |
| Operator refresh | 76.817 / 94.387 | 103.663 / 139.615 | 107,813 | 6,484 |

The median and maximum each use three samples per role and trigger. Sync work can
occur between tick callbacks, so the sync interval and tick processing duration
measure different work. The sealed fixture proposes native elapsed time under
1,000 ms, owner-thread phases under 50 ms and client refresh under 1,000 ms for
later review. The proposal carries no acceptance verdict. The twelve individual
samples, packet CRCs, decoded graphs, permission assertions and server-thread
observations are retained. [Command-tree measurements](2026-11-16-command-tree-delivery.md)

## Bounded idle and ten-owner retarget

All six fresh processes ran serially with at least 30 seconds of warmup and
60 seconds of measurement. Every retarget run first proved the expected A/B/A
targets and items with all six navigation methods enabled. Each of ten owners
offered one alternating retarget per second. The idle control had zero owners.
The native tick processing budgets were p95 <40 ms and p99 <50 ms for each run.

| Case / repeat / PID | Warmup / measurement s | Measured ticks | p95 ms | p99 ms | Max ms | Warmup completed / offered | Measured completed / offered | Outcome |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Idle 1 / 89002 | 30.058 / 60.097 | 1,201 | 0.506333 | 0.622584 | 1.788750 | 0/0 | 0/0 | PASS |
| Idle 2 / 89105 | 30.070 / 60.041 | 1,200 | 0.565416 | 0.669500 | 1.952542 | 0/0 | 0/0 | PASS |
| Idle 3 / 89200 | 30.029 / 60.045 | 1,200 | 0.588000 | 0.735333 | 10.707334 | 0/0 | 0/0 | PASS |
| Retarget 1 / 89305 | 30.058 / 60.098 | 1,201 | 3.370208 | 3.948084 | 7.336292 | 300/300 | 600/600 | PASS |
| Retarget 2 / 89424 | 30.026 / 60.052 | 1,200 | 3.305208 | 3.590708 | 5.176625 | 300/300 | 600/600 | PASS |
| Retarget 3 / 89537 | 30.036 / 60.048 | 1,200 | 3.555208 | 7.126666 | 15.638041 | 300/300 | 600/600 | PASS |

Retargets recorded 900/900 warmup and 1,800/1,800 measured successful native
acknowledgements. Each measured tick had the expected session count. Active
navigation counters cleared after disconnect within the 10–11 observed ticks.
Retarget runs retained ten intended owner map-cache entries. Measurements include
instrumentation overhead on the Apple M4 host with Java 21, G1 GC, a 1 GiB initial
heap and a 2 GiB maximum heap. [Per-run windows, roundtrip latency and cleanup](2026-11-16-bounded-performance.md)

## Verification at closeout

- 310 Python runner tests, 36 pinned Rust bot tests and 2 Java probe contract tests
  passed. The test-only Paper probe JAR task passed on 1.21.11 without an active
  Stonecutter project switch. The commands were
  `rtk proxy python3 -m unittest discover -s tools/server-performance/tests`,
  `rtk proxy cargo +nightly-2026-07-15 test --manifest-path tools/server-performance/bots/versions/mc1.21.11/Cargo.toml --locked`,
  and `rtk ./gradlew :cross-server:performanceProbeContractJar :cross-server:performanceProbeTest :paper:1.21.11-paper:performanceProbeJar --console=plain --max-workers=2`.
- Rehashed the candidate, prepared evidence and attempt source snapshots in all
  three roots. Rechecked the current execution sources against the Step 4 seal.
  Inspected the production JAR copies for archive integrity and found zero
  performance-probe, ASM or Querz/NBT entries. Of 287 candidate build-source hashes,
  286 match the current checkout and the one difference is the test-only probe
  wiring in `paper/build.gradle.kts`.
- Rechecked all 16 candidate server logs across the three roots for saved
  shutdown. The logs show no ERROR/SEVERE entries or watchdog nonresponse.
  A fresh process scan found no matching Paper test JVM or Azalea bot.
- Reused the November 16 runtime results for the unchanged candidate. Closeout
  added document and evidence verification without repeating the runtime cases.

## Known issues and scope

- Earlier artifacts retain the original inventory defect, stop/quit cleanup and
  depth-nine nested-item escape, and watchdog nonresponse under broader workloads.
  Their original verdicts and artifact identities still apply. Current protection
  checks only direct tagged items. Recursive cleanup and automatic hopper-transfer
  protection were intentionally removed. [Original M0](2026-10-24-m0-paper.md) ·
  [Original navigation](2026-10-25-m1-navigation.md) ·
  [Broader timed workload](2026-11-09-task11-continuation.md)
- A delayed command-tree scheduling thread can lose a probe end boundary. Missing
  instrumentation or receipts are BLOCKED, and all twelve final samples carried
  the required boundaries. The separate whole-probe Java 17 compile also remains
  limited by the existing `ThreadMXBean.getTotalThreadAllocatedBytes()` call.
  The supported Java 21 build passed. [Probe verification and deferred finding](2026-11-16-command-tree-delivery.md)
- Scope is Paper 1.21.11 only. Excluded coverage includes Fabric, Forge, NeoForge,
  Folia and Velocity/two-backend runs, exhaustive command/method/terrain variants,
  additional sync/upload/edit/sharing load, fifty-owner stress, soaks, cache/viewer
  stress and offline inventory/world-drop leak cycles. The terrain fixtures
  validate prepared raster persistence and delivery. Vanilla geographic rendering
  and graphical client rendering remain outside the exercised checks.
- The conclusion applies to the pinned candidate in these selected scenarios.
  The comprehensive release matrix, baseline promotion, profiled comparisons,
  JMH and publishing enforcement are outside this delivery. [Scope](../plans/2026-10-24-server-performance.md)
