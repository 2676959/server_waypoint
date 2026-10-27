# Priority performance repairs: Paper and Fabric

Date: 2026-10-26.

**Result:** the controlled Paper inventory overload improved from 18.19 to 20.00 ticks/s. Its full native tick p95 fell from 55.95 ms to 3.77 ms. Shared query/completion changes reduced allocation; they did not establish a response-latency improvement.

Scope: focused, user-directed development repairs and Paper/Fabric 1.21.11 runtime checks. This supersedes the audit-before-repair ordering for these findings only. The broader release audit, all-version runtime matrix and soaks remain open. Paper and Folia retain the same plugin source/build; Folia runtime testing was omitted as the user requested.

## Repairs and preserved behavior

- **INV-READ-01:** `PaperNavigationItemReader` reads native custom-data, bundle and shulker components without constructing Bukkit metadata/block states. The marker remains the Bukkit PDC byte type, with any nonzero byte meaning true. Reads run on the existing owner thread. No session-based bypass, item-result cache or off-thread inventory access was introduced.
- Negative cleanup uses the read-only scan before entering the original Bukkit mutation path. Detection retains its depth-eight boundary; cleanup retains its existing ability to remove a marked direct child at depth nine. Shulker/bundle type restrictions, ordinary-item preservation and positive cleanup remain covered.
- **QUERY-SCRATCH-01:** fuzzy filter tokens and distance buffers are reused within one list query. Each request owns its buffers; immutable waypoint snapshots, live-result identity, matching algorithm, grouping and sorting remain unchanged. Standalone substring matches retain their fast path.
- **COMMAND-TOOLTIP-01:** plain, unstyled tooltips use Brigadier `LiteralMessage`; styled, translated and child-bearing components retain the platform conversion. Escaped identifiers and all 10,000 suggestions are preserved.
- Localization impact: none. No language keys or user-facing text changed. No separate Folia artifact or Folia-specific exemption was added.

## Native Paper tick measurements

Matched probe-v2 runs: `paper-before-4` and `paper-bounded-2`. Full completed native tick records contain the entire corresponding workload interval; tick IDs are contiguous. Achieved tick rate comes from native tick-start timestamps. Callback spacing is not used as processing duration.

| Workload per native tick | Before native p95 ms | Final native p95 ms | Before ticks/s | Final ticks/s | Before workload allocation MB | Final workload allocation MB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Idle/control callback | 1.64 | 1.24 | 20.00 | 20.00 | 0.00 | 0.00 |
| 100 synthetic transfer events | 15.03 | 1.31 | 19.99 | 20.00 | 34.75 | 0.10 |
| 1,000 synthetic transfer events | 55.95 | 3.77 | 18.19 | 20.00 | 347.54 | 1.02 |

Each event contains an ordinary, untagged shulker holding 27 Mending books. These are synthetic production-listener invocations, separate from the physical hopper test below. Allocation is the exact named-thread delta for the timed workload, not total JVM allocation. MB is decimal.

At 100 events/tick, final timed listener work p95 is 0.398 ms and allocation p95 is 101,600 bytes. Both satisfy the unchanged 5 ms / 2,097,152-byte workload budgets. The initial pre-edit native allocation check failed at 34.35 MB per 100-event batch; that failure is retained in `evidence/before-budget.json`. This is a scoped budget check, not promotion of a complete M0/release gate.

## Shared component measurements

Matched probe-v1 component runs: `paper-before-3` versus `paper-bounded-1`, and `fabric-before-1` versus `fabric-final-1`. Values are operation/batch processing p95 and mean named-thread allocation. They are not full-server tick times.

| Platform / operation | Before p95 ms | Final p95 ms | Before allocation MB | Final allocation MB |
| --- | ---: | ---: | ---: | ---: |
| Paper / 10,000-waypoint fuzzy query | 13.48 | 12.80 | 7.30 | 5.54 |
| Paper / 10,000-result native completion | 14.63 | 14.30 | 7.36 | 2.40 |
| Fabric / 10,000-waypoint fuzzy query | 12.67 | 12.71 | 7.30 | 5.54 |
| Fabric / 10,000-result native completion | 13.65 | 14.08 | 8.96 | 2.40 |
| Fabric / 50 ordinary shulker cleanup calls (unchanged implementation) | 0.81 | 0.88 | 0.53 | 0.53 |

Query allocation fell about 24% on both platforms. Completion allocation fell about 67% on Paper and 73% on Fabric. These single before/after pairs do not establish a latency win; inspect the p95 values rather than treating allocation reduction as proof of faster responses. Retain these bounded allocation reductions, without claiming the remaining synchronous O(n) query/completion cost has been eliminated. Fabric inventory code was not rewritten because its distinct implementation did not reproduce the Paper metadata mechanism.

## Physical movement and protection

A fresh paired, zero-player circulation test used the existing sealed medium shulker/books fixture: 128 cells, 512 force-loaded hoppers, 10-second warm-up and 30-second measurement per control/candidate. Physical movement is measured as observed inventory increases, not synthetic dispatches.

| Physical circulation | Observed transfers | Seconds | Observed transfers/s |
| --- | ---: | ---: | ---: |
| Control | 11,008 | 30.05 | 366.27 |
| Candidate | 11,008 | 30.04 | 366.40 |

Activity, zero-player/unpaused state, item conservation, quiescence and matched-control throughput checks passed. Both runs retained 128 top-level carriers.

One real Azalea client separately exercised physical protected drops, offhand swaps, allowed/denied inventory slots and ordinary-item controls; those checks passed. Each Paper diagnostic run also passed 47 protection assertions, including Bukkit-written markers, mutation between reads and depth boundaries.

**Existing unfixed failure:** independent nested world-spawn checks report seven PASS / one FAIL on both the frozen pre-fix JAR and the final JAR. The failure is `bundle-9/map`: a protected map at depth nine remains in an uncancelled world entity. Both helper commands retain exit status 1. The listener returns when its depth-eight detection reports false, so it never calls the cleanup method that could remove the depth-nine child. This same original-release defect was already recorded in `validation/progress.md`; the focused optimization deliberately preserves the existing boundaries. The other seven nested cases, innocent contents/metadata, owner state, fixture cleanup and native log health pass. This report does not label the complete nested suite passing. See `evidence/nested-bounded-comparison.json` and `player-nested-before/protection.json` and `player-nested-bounded/protection.json`.

## Verification and proof boundaries

- 679 common, 37 Paper 1.21.11 and 927 Fabric 1.21.11 Java tests passed; 118 performance-tool Python tests passed.
- Paper production API compilation passed for 1.21, 1.21.9, 1.21.11 and 26.2; 26.2 test sources also compile. Container entries became templates in 26.2, handled by small Stonecutter branches. A review-found over-capacity shulker edge case was reproduced by a failing test and fixed: only physical slots 0–26 are scanned, matching Bukkit before empty slots are filtered. All four Paper APIs compile after this correction. Earlier API/Stonecutter compile failures and the boundary-test RED remain in the retained logs. Read-only review confirmed the 27-slot correction and found no further actionable regressions.
- Native diagnostics use 200 warm-up ticks and 600 measured ticks per case, nominally 10/30 seconds at 20 TPS. Overload stretches wall time. Nearest-rank distributions include every measured sample. Component phases have no connected clients; fake player contexts used for unrelated map setup do not establish multiplayer performance.
- Java 21.0.11, -Xms1G/-Xmx2G, Apple M4/16 GiB. Paper is 1.21.11 build 111. Fabric is loader 0.18.2 / API 0.141.3 with frozen named-development artifacts. No compiler or other owned workload ran during timed samples.
- These are current-development before/final comparisons, not a replacement for the immutable published 4.0.0 audit. The baseline is commit `04c5ac18`; published-release profiles/failures remain separate. Intermediate candidates and every setup/reporting failure are retained.
- No Folia, Forge or NeoForge runtime run, full remaining-feature matrix, long soak or production incident attribution is claimed. The shared Paper/Folia fix is present in one build. Tests cover the requested Paper/Fabric runtime scope.
- No commits, pushes or deployments accompany this change.

## Exact artifacts and retained evidence

- `paper-before.jar`: `9de6b4bb56b3513b9e04d53830f2830744034d4441e3513bf1077574c032d2e0`.
- `fabric-before.jar`: `50ac977e8c362fdae08a59974d17a8acc0a1e7528f211effb37fdaf2c44e8fe6`.
- `paper-bounded.jar`: `d9447d77c08040b535958a985bfa30d17c9ed9583b1c8cf3a43528b52dd3fa95`.
- `fabric-final.jar`: `6930edcb24853a142285d73dca88fa22c9b86ff1640eaaa474277c97da188c49`.

Root: `/Volumes/ssd/sw-performance-repair-20261026/`.

- `evidence/summary.json` / `summary.txt`: all valid native diagnostic distributions, including intermediate candidates.
- `*/samples.json`, `console.log`, `argv.json`: raw timing/allocation/checksums, clocks, errors and executed commands. Fabric v1 probe bytes are retained under `evidence/probe-v1`; the verification creates explicit per-run replay arguments for its originally shared probe path.
- `physical-*-3/measurements.json`, `evidence/physical-pair-bounded.json`: raw physical activity and paired decisions; `player-*/protection.json`: actual protocol actions and independent state/health decisions.
- `evidence/manifest.json`: source/input/output hashes, exact run artifact/probe mapping, excluded-attempt reasons and owned-process cleanup verification.
- `src/diag/`, `tools/`, `evidence/probe-v1/`, `evidence/probe-v2/`: reproducible harnesses. `evidence/shulker-boundary-green-build-2.log` and `python-tests-final.log`: successful verification logs.
- The first missing-descriptor attempt, old-constructor harness attempt and missing-schema physical report attempt remain excluded from passing comparisons. The physical pair was repeated in fresh roots after the reporting correction.
