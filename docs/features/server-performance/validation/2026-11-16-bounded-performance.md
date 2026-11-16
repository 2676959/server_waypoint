# Bounded performance checks — minimal Step 4

Completed November 16, 2026, against the [authoritative minimal plan](../plans/2026-10-24-server-performance.md).

**Step 4 is complete: all six fresh-process runs PASS.** Three zero-owner idle controls and three ten-owner navigation-retarget runs used 30 seconds warmup and 60 seconds measurement, serially. Every measured run meets p95 <40 ms and p99 <50 ms. Step 5, the combined minimal completion report, remains pending. This is bounded Paper regression evidence, not release certification.

## Retained evidence and identities

- Root: `/Volumes/ssd/sw-performance-minimal-step4-20261116/`.
- Run ID: `81666b7f-b27b-4e9c-9088-25f60bc62e07`.
- Idle attempt: `ecc2f854-62fe-4262-97a5-d5a479dd2d96`.
- Retarget attempt: `5a829f17-c737-43bd-9698-2356574073ed`.
- `report.md`, `report.json`, `step4-summary.json`, manifests, runtime lock, environment and evidence seals remain in the root. Each `performance/<attempt>/` retains its source snapshot and `inputs.json`; every `repeat-N/` retains raw phase ticks/receipts, setup acknowledgements, expected/native target and item state, disconnect counters, server/bot/GC logs, argv and saved shutdown.

| Input | SHA-256 / source identity |
| --- | --- |
| Candidate revision | `0ea2d348df673cb9495a8f45b1d7e19a620a790f` |
| Candidate JAR | `a9b21e37d2deaddcea29c22e8763e6ac5b8ab65abf946b201fd50c3602c77915` |
| Probe/agent | `5e66454735cefc0eb0945f4b1e4c43b36d6e2f90b93dd4b40ec4012e58d025e9` |
| Azalea binary | `bff7a17c5a3a4e62046565da786dd0e6613fdf8bb956cb6957f00224c9cac243` |
| Paper 1.21.11 build 111 | `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3` |
| Java 21 | `04005388bac0c272ea914210ca519ce94b2f873ea3962b9874a6859f74d7f279` |
| idle-medium baseline manifest | `89d4f098ef8538c3d22218fecd0c609d12ce85050310e76894536fdd1f0e6d99` |
| navigation-terrain-medium baseline manifest | `080de24b0a4a327130316a165251e9c04173c2a0dfb06a8dfc194965dd928ba3` |

The exact candidate and tools match Step 3. All 286 unchanged production/build source hashes match the candidate build identity; the remaining `paper/build.gradle.kts` difference is the previously recorded test-only probe/ASM configuration. `candidate-verification.json` confirms no probe or ASM entries in the production JAR. This step changes Python test tooling only; it rebuilds neither the candidate nor native tools.

The retained environment is Apple M4 / Mac16,10, 10 cores, 16 GiB RAM, macOS 26.5, with the locked Java 21 runtime using `-Xms1G -Xmx2G -XX:+UseG1GC`. Runs are unprofiled; native probe and command-completion instrumentation remain enabled. Results include that observation overhead and apply to this host/fixture.

## Adapter proof and workload

Each ten-owner process verifies exact bot/native owner identities with reconnect disabled, explicitly grants operator status to avoid vanilla command-spam admission, seeds 64 stone and 3 diamonds per owner, and selects all six supported methods: compass, map, bossbar, actionbar, text display and world marker. Native setup A→B→A commands require one successful matched completion per owner and exact independent target, compass/map, display and ordinary-item expectations before timing begins. The same native target/item/conservation checks run after each phase.

Every owner receives one retarget per second, alternating B/A; owners are staggered at 100 ms intervals, for ten aggregate operations per second. Each warmup offers/completes 300 commands and each measurement offers/completes 600. The three measured runs therefore retain **1,800/1,800** matched native completions, plus **900/900** during warmup. Receipts match action ID, owner, command and successful native return. Client-local acknowledgement roundtrip timing never subtracts server clocks.

Every retained native tick sample reports zero active sessions for idle or ten for retarget. Native tick indices are consecutive in each phase. Missing receipts, duplicate/unmatched completions, shortened windows, incomplete sample coverage, session-count gaps or offer lag reaching one owner interval are BLOCKED. Sampling stops at the phase boundary before the bounded pending-command drain; issued/completed counts and the actual native phase duration remain retained. No measured run has a delivery gap.

## Individual measurements

Native tick processing durations use Paper’s tick-end duration, not callback spacing. Percentiles are nearest-rank over each measurement window; runs are not pooled. Warmup tick durations are retained but do not receive acceptance-budget judgments.

| Case | Repeat / PID | Warmup / measurement s | Measured ticks | Tick p95 ms | Tick p99 ms | Tick max ms | Result |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| idle | 1 / 89002 | 30.058 / 60.097 | 1201 | 0.506333 | 0.622584 | 1.788750 | PASS |
| idle | 2 / 89105 | 30.070 / 60.041 | 1200 | 0.565416 | 0.669500 | 1.952542 | PASS |
| idle | 3 / 89200 | 30.029 / 60.045 | 1200 | 0.588000 | 0.735333 | 10.707334 | PASS |
| retarget | 1 / 89305 | 30.058 / 60.098 | 1201 | 3.370208 | 3.948084 | 7.336292 | PASS |
| retarget | 2 / 89424 | 30.026 / 60.052 | 1200 | 3.305208 | 3.590708 | 5.176625 | PASS |
| retarget | 3 / 89537 | 30.036 / 60.048 | 1200 | 3.555208 | 7.126666 | 15.638041 | PASS |

| Retarget repeat | Warmup completed / offered | Measured completed / offered | Maximum offer lag ms | Ack roundtrip p50 / p95 / max ms |
| --- | ---: | ---: | ---: | ---: |
| 1 | 300 / 300 | 600 / 600 | 6.753 | 38.544 / 39.904 / 57.663 |
| 2 | 300 / 300 | 600 / 600 | 6.606 | 38.511 / 39.766 / 58.271 |
| 3 | 300 / 300 | 600 / 600 | 7.903 | 40.114 / 42.028 / 42.870 |

Worst measured retarget tick p95 is **3.555208 ms**, p99 is **7.126666 ms**. All individual measurements pass the established 40/50 ms exclusive budgets. Command roundtrip latency is descriptive; it has no separate acceptance budget here.

## Disconnect, verification and review

Each process retains 10–11 native ticks after all owners disconnect. Sessions, tasks, displays/entities, marker-motion tracks, bossbars, online protected items, map targets, target-index entries and tick counters are all zero. Ten owner map-cache entries remain intentionally retained in retarget runs; idle retains zero. These are native post-disconnect counters: offline inventories and world drops are outside this observation. Step 2 remains the separate lifecycle/client-effect evidence.

All six distinct measured PIDs stopped, all six server logs prove saved shutdown and PASS native health, and no matching server/bot executable remained. `process-verification.json` retains that check. There was no unexplained failure requiring a diagnostic profile. No individual failed run or delivery gap was discarded or relabelled.

The preliminary root `/Volumes/ssd/sw-performance-minimal-step4-smoke-20261116/short-retarget/` retains a passing ten-owner 2s/3s adapter check, including 20/20 warmup and 30/30 measured acknowledgements. It is not timing acceptance evidence. Subsequent harness failure-path changes are exercised by every final process’s setup and by regression tests; final runs use a fresh executable/evidence seal.

Independent review found two Important failure-path defects: interruption could record a partial repetition as PASS, and later collection/shutdown errors could detach already observed tick failures. Four regressions reproduced these defects before the fixes and then passed. Partial windows now finalize their judgments even on error, incomplete execution is BLOCKED, and interrupted current repetitions remain in case/root results. No Step 4 review findings remain deferred; the unused imports/state noted as Minor were removed in that fix.

Final verification: **310 Python tests PASS**; source/evidence seals match; `git diff --check` is clean. Existing native probe/bot bytes were reused unchanged. No production repair, commit, push, baseline promotion or broader platform/stress test was performed.

Execution continues in the existing feature checkout, following Steps 1–3. The authoritative minimal plan controls scope. The explicit boundaries are the instrumented native measurement, online-inventory cleanup counters, and Paper-only bounded coverage; they make no offline-leak, comprehensive-platform or healthy-release claim.

**Next:** Step 5, combine the five functional/timing groups and their retained evidence into the minimal completion report.
