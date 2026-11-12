# Task 11 continuation — tool lock

This report records the 2026-11-09 Paper tool lock, the Task 11 behavior
contracts, and the 2026-11-10 timed Paper small/medium run. It is not a
release audit and it does not replace the retained original findings.

## Source and toolchain identity

| Item | Value |
| --- | --- |
| HEAD | `5ae679a8be707c548f07366ebc3739cf6ebe6560` |
| Branch | `codex/experimental-world-marker` |
| Dirty at inspection | `docs/features/server-performance/README.md`, `plans/2026-10-24-server-performance.md`, untracked `plans/2026-11-09-server-performance-next-steps.md` |
| Stonecutter | `settings.gradle.kts` includes Paper `1.21-paper`, `1.21.9-paper`, `1.21.11-paper`, `26.2-paper` |
| Active mods project | `26.1.2-fabric` (`mods/stonecutter.gradle.kts`) |
| Active paper project | `26.2-paper` (`paper/stonecutter.gradle.kts`) |
| Probe task actually built | `:paper:1.21.11-paper:performanceProbeJar` |
| Bot toolchain | `nightly-2026-07-15`, Azalea `0.15.1+mc1.21.11` |

The active Paper project is 26.2. The 1.21.11 probe was built by its
versioned task, not by whichever project Stonecutter marks active.

## Published artifact

`/Volumes/ssd/sw-performance-m0-audit/artifacts/server_waypoint-4.0.0-paper-mc1.21.11-26.1.2.jar`

| Check | Result |
| --- | --- |
| Size | 1,210,479 bytes, matching `config/releases/4.0.0.json` |
| SHA-256 | `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57` |

## Previous lock versus bytes before rebuild

| Tool | Locked SHA-256 | On-disk SHA-256 before this lock update |
| --- | --- | --- |
| Bot | `b31f632c69b2137b91d53b2356f39b3bb15395dd99ae0f575d98e6565eddcf5c` | `3d33e86866200ca3fdd7a45f6471cce8af768fb0d047b604936b7bfa10050478` |
| Probe | `a0b5760661a52d403da9a3562cde7d2c973f6ed99dc31499d339d107b4160854` | `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9` |

Java `04005388bac0c272ea914210ca519ce94b2f873ea3962b9874a6859f74d7f279` and
Paper server `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3`
already matched the lock. `check_runtime` rejects a locked bot or probe whose
bytes change. That behavior was already implemented; the new provenance test
records it. No sample is stored on the doctor record.

## Builds and isolation

Python harness: 202 tests, OK.

Pinned Rust tests: OK (`sw-performance-bots` unit, integration, and
`wire_contract` suites in the captured cargo log).

Release bot rebuild: `Finished release profile`. The harness `CARGO_TARGET_DIR`
points at a sandbox cache, so the release binary was copied onto the lock path
`tools/server-performance/bots/versions/mc1.21.11/target/release/sw-performance-bots`.

Probe jar: Gradle `BUILD SUCCESSFUL`, task `UP-TO-DATE`.

Probe-class isolation, prefix `_959/server_waypoint/perf/`:

| Archive | Probe entries |
| --- | ---: |
| `server-waypoint-performance-paper-probe.jar` | 22 |
| Local `server_waypoint-4.0.0-paper-mc1.21.11-26.1.2.jar` | 0 |
| Local `server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar` | 0 |
| Published 4.0.0 paper artifact above | 0 |
| `server-waypoint-folia-live-test-load.jar` | 0 |

## Native readiness

Root: `/Volumes/ssd/sw-performance-taskA-readiness-20261109-3`

The check used the rebuilt bytes and the published 4.0.0 jar. It did not
rewrite hashes to make a mismatch pass. `hello` accepted the published
artifact. One vanilla player, `swp_reader_00`, was server-observed.
`lock-walk-1` completed on the client monotonic clock (before z=6.5, after
z≈7.97). `players.snapshot` then recorded 11 native movement events.
`check_runtime` for `paper-1.21.11` with bots, fixture, tick-processing,
profile, wire-protocol and zero-player-ticking returns PASS against the
updated lock.

## Updated lock

| Tool | SHA-256 |
| --- | --- |
| Bot | `5fe1bc3dad91a07b6605397c9be6b8fe59b8373c167fa9902a546b445ef10ce1` |
| Probe | `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9` |

Task 11 tier expansion, reusable behavior, timed execution and the 28-case
audit are not claimed here.

## Task 11 variant map

Each variant below is the native contract in
`tools/server-performance/scenarios/navigation_behavior.py`. A variant with no
attached probe and driver stays `BLOCKED` on its own operation. Distinct map
IDs still prove owner separation only; same-region viewers, forced eviction
and chunk/region boundaries are separate decisions.

| Variant | Operation counted | Native receipt | Before | After | Handler |
| --- | --- | --- | --- | --- | --- |
| navigation/methods | every method combination | snapshot methods and client effects | ordinary stone and diamond counts | requested methods present, others absent | `method_coverage` |
| navigation/lifecycle | move, arrive, disconnect | position and resource counts | live target | quiescent cleanup counts | `cleanup_counts` |
| navigation/retarget-edit-delete | retarget, edit, delete | target snapshot and owner rows | two distinct targets | only the removed session stops | `deleted_navigation` |
| navigation/death-respawn-world-change | die, respawn, change world | lifecycle drops and world items | ordinary drops and protected items | ordinary conserved, protected absent | `death_protection` |
| navigation/rejoin-restore | disconnect and rejoin | restored methods, map id, target | saved session | restored session matches | `restored_session` |
| map-terrain/warm-cold | warm, cold, restore | map colors and client checksum | 16384 independent pixels | exact native and client pixels | `terrain_roundtrip` |
| map-terrain/multiple-viewers | two owners, one retarget | map ids, pixels, targets | one shared region | other owner pixels and id unchanged | `same_region_viewers` |
| map-terrain/boundary-dimension | chunk/region crossing, separate dimension switch | positions and dimension map identity | origin chunk | boundary crossed; a dimension change is not that boundary | `boundary_transition` |
| map-terrain/persist-evict-restart | evict, save, restart, reopen | saved bytes and reopened identity | rendered bytes | forced eviction and identical reopen | `persisted_evicted_terrain` |
| protection/inventory-click-drag-open-close | click, drag, open, close | protection receipts and container graphs | dirty protected fixture | protected removed, ordinary conserved | `container_cleanup` |
| protection/craft-result-cartography | prepare and take station results | station receipts | protected or ordinary matrix | protected result unusable; ordinary take allowed | `station_outcome` |
| protection/drop-pickup-spawn-move | drop, pickup, spawn, move | drop, spawn, world entity | ordinary stack | correlated world item, or blocked if the snapshot is missing | `physical_drop_spawned` |
| protection/entity-frame-equipment | pickup, frame, equipment | entity receipts and carrier snapshot | protected item offered to a carrier | protected item stays off the carrier | `entity_outcome` |
| protection/death-reconcile | die with mixed items | death drops and world snapshot | ordinary and protected items | ordinary conserved, protected absent | `death_protection` |

Stale receipts fail `accepted_receipt`. Clean-before container fixtures, missing
world-item snapshots, ordinary-item loss, wrong-owner edits and a protected
station result that remains usable already fail in `test_navigation_scenarios.py`.

Native reruns on 2026-11-09, published 4.0.0 paper bytes, current probe and bot:

| Run | Root | Result |
| --- | --- | --- |
| Container, 1 owner | `/Volumes/ssd/sw-performance-taskC-container-1` | PASS, other-container open/dirty-close |
| Container, 10 owners | `/Volumes/ssd/sw-performance-taskC-container-10` | PASS, other-container open/dirty-close |
| Terrain, 1 owner | `/Volumes/ssd/sw-performance-taskC-terrain-1` | PASS, including chunk/region `boundary` |
| Dimension, 1 owner | `/Volumes/ssd/sw-performance-taskC-dimension-1` | PASS, cross-dimension map identity |
| Dimension, 10 owners | `/Volumes/ssd/sw-performance-taskC-dimension-10` | PASS, `sameRegionViewers` and `separateViewers` |

`persisted_evicted_terrain` still returns `BLOCKED` unless a snapshot says the
cache was forcibly evicted. The terrain smoke proves exact save/reopen pixels.
It does not emit that eviction flag, so persist-evict-restart is not closed by
the pixel restart alone.

## Timed Paper small/medium run

Root: `/Volumes/ssd/sw-performance-task11-20261109-5`.
Run id `37a240b4-b53e-4969-81a7-7c71c0b099e1`.
Started `2026-11-10T05:30:29Z`, finished `2026-11-10T08:17:29Z` (2 hours 47 minutes).
Host: Mac16,10, Apple M4, 10 cores, 16 GiB, macOS 26.5 build 25F71.
Java `-Xms1G -Xmx2G -XX:+UseG1GC`. The run is unprofiled. No JFR, save/reopen
comparison, or post-quiescence item/session/task/entity/map count was retained,
so that part of the Task 11 gate stays open.

The published 4.0.0 Paper artifact is the one above. The lock's probe is
`987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9` and the bot
is `5fe1bc3dad91a07b6605397c9be6b8fe59b8373c167fa9902a546b445ef10ce1`.
Selection is 28 cases: 14 variants, small and medium, three fresh-process
repetitions, 30-second warm-up, 60-second measurement. Small uses 1 owner at
10 actions/second. Medium uses 10 owners at 50 actions/second. `report` exits
1. Decision: FAIL. Counts: 3 FAIL, 25 BLOCKED, 0 PASS. Exit 1 is an observed
defect inside this selection. It is not release acceptance.

Earlier roots on the same day are harness attempts, not this audit:
`/Volumes/ssd/sw-performance-task11-20261109` and `-2` were prepared before
the runner addressed connected owners; `-3` records a vanilla command-spam
kick; `-4` records duplicate warm-up and measurement action ids. They stay
on disk and are not folded into the report above.

| Case | Decision | Completed/offered per repetition |
| --- | --- | --- |
| `map-terrain:boundary-dimension:medium` | FAIL | 2998/3000, 3000/3000, 3000/3000 |
| `map-terrain:boundary-dimension:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `map-terrain:multiple-viewers:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `map-terrain:multiple-viewers:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `map-terrain:persist-evict-restart:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `map-terrain:persist-evict-restart:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `map-terrain:warm-cold:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `map-terrain:warm-cold:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `navigation:death-respawn-world-change:medium` | FAIL | 3000/3000, 3000/3000, 3000/3000 |
| `navigation:death-respawn-world-change:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `navigation:lifecycle:medium` | BLOCKED | 1144/3000, 1144/3000, 1144/3000 |
| `navigation:lifecycle:small` | BLOCKED | 0/600, 0/600, 0/600 |
| `navigation:methods:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `navigation:methods:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `navigation:rejoin-restore:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `navigation:rejoin-restore:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `navigation:retarget-edit-delete:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `navigation:retarget-edit-delete:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `protection:craft-result-cartography:medium` | BLOCKED | 2059/3000, 2061/3000, 2049/3000 |
| `protection:craft-result-cartography:small` | BLOCKED | 28/600, 28/600, 28/600 |
| `protection:death-reconcile:medium` | FAIL | 3000/3000, 3000/3000, 3000/3000 |
| `protection:death-reconcile:small` | BLOCKED | 600/600, 600/600, 600/600 |
| `protection:drop-pickup-spawn-move:medium` | BLOCKED | 3000/3000, 3000/3000, 3000/3000 |
| `protection:drop-pickup-spawn-move:small` | BLOCKED | 357/600, 357/600, 358/600 |
| `protection:entity-frame-equipment:medium` | BLOCKED | 0/3000, 0/3000, 0/3000 |
| `protection:entity-frame-equipment:small` | BLOCKED | 0/600, 0/600, 0/600 |
| `protection:inventory-click-drag-open-close:medium` | BLOCKED | 0/3000, 0/3000, 0/3000 |
| `protection:inventory-click-drag-open-close:small` | BLOCKED | 0/600, 0/600, 0/600 |

The three FAIL cases are product or server observations, not relabeled
delivery gaps:

- `map-terrain:boundary-dimension:medium` offers `/tp @s -300 -61 -300` at 50
  per second. Repeats 1 and 2 record 21 and 23 Paper watchdog nonresponse
  reports and 868 and 906 error/severe log lines. The server thread is inside
  Brigadier `CommandNode.equals`. Those repeats keep one and two native tick
  samples, with p95 36326.67 ms and 13526.37 ms, so the medium tick budget
  fails and the sample count is short. Repeat 1 also completes 2998 of 3000
  actions. Repeat 3 completes 3000/3000 with p95 4.51 ms and still records one
  watchdog report and 52 error lines.
- `navigation:death-respawn-world-change:medium` and
  `protection:death-reconcile:medium` both offer `/kill` at 50 per second and
  complete 3000/3000. Each repetition's native p95 is between 3712.17 ms and
  5111.76 ms, above the 40 ms medium limit, with only 76 to 99 tick samples.
  The behavior probe also reports that lifecycle observations were not armed.
  That missing observation stays on the decision, and the   tick-budget failure
  keeps the case FAIL. The matching small cases deliver 600/600 and stay
  BLOCKED because lifecycle observations were not armed. Their nearest-rank
  tick p95 is not uniformly fast: `death-respawn-world-change` repeat 1 is
  869.35 ms (458 samples) and `death-reconcile` repeat 2 is 997.10 ms (475
  samples). The other four small repetitions are about 2.3–2.6 ms. Small has
  no medium tick budget, so those spikes do not change the BLOCKED decision.

Sixteen BLOCKED cases deliver every offered action and still have no judged
native before/after observation. Their judges are the explicit "not yet judged"
decision, or the probe has no `navigation.viewers` / `navigation.boundary`
operation. A complete command window is not a behavior PASS. Small
`boundary-dimension` delivers 600/600 without a watchdog; only the medium rate
trips the watchdog.

The remaining BLOCKED cases do not deliver the offered rate, and their ticks
stay under the medium budget where samples exist:

- `navigation:lifecycle` walks for 500 ms. Small completes 0/600. Medium
  completes 1144/3000 on every repetition. That is load-generator delivery,
  recorded as a deficient bot, and the case stays BLOCKED because the cleanup
  observation is not judged.
- `protection:inventory-click-drag-open-close` completes 0 actions. The
  behavior step reports that the other container did not open. Ticks remain
  available (p95 about 1.5–3.9 ms).
- `protection:craft-result-cartography` completes 28/600 small and
  2059–2061/3000 medium.
- `protection:drop-pickup-spawn-move` small completes 357–358/600. The
  behavior snapshot throws a probe `NullPointerException` while reading a
  missing JSON string. Medium completes 3000/3000 and stays BLOCKED on the
  unjudged behavior decision.
- `protection:entity-frame-equipment` completes 0 actions. The small behavior
  step disconnects the owner with `Cannot interact with self!` because the
  catalog action addresses entity id 1. Medium repeats then hit the same
  missing-JSON probe failure. Tick p95 stays under 5 ms, so this is not
  recorded as server saturation.

Original stop/quit cleanup, depth-nine escape, deletion, and watchdog findings
in the earlier navigation evidence stay in force. This run does not clear
them, and no production code was changed to alter them. Task 11's two master
plan checkboxes stay open: the timed selection ran, and the behavior,
eviction, save/reopen, quiescence-count, and profile evidence it asks for did
not.

## Container case follow-up

Root: `/Volumes/ssd/sw-performance-container-timed-4`. Published 4.0.0 Paper,
same probe, bot `95f5bb03dde1b4b6efe7c6275572be0d1f47c25398e4c4a4b97c62bfc41bf887`.
This root replaces the 0/600 and 0/3000 container rows above for the container
case only. The other 26 cases were not rerun.

A one-owner functional session, before the timed windows, recorded 28 PASS
container and drag decisions and a cleanup removal with `present: false`.

| Case | Decision | Measurement completed/offered | Behavior | Tick p95 |
| --- | --- | --- | --- | --- |
| `protection:inventory-click-drag-open-close:small` | INCONCLUSIVE | 104/600, 76/600, 52/600 | PASS, PASS, PASS | 4.10 ms, 3.39 ms, 3.58 ms |
| `protection:inventory-click-drag-open-close:medium` | BLOCKED | 0/3000, 47/3000, 0/3000 | BLOCKED, PASS, BLOCKED | 7.22 ms, 5.89 ms, 6.51 ms |

Each small repetition removed its containers with `present: false` and no
`present: true`. Successful open-close receipts take about 240 ms. One client
therefore cannot sustain the declared 10 opens per second, and ten clients
cannot sustain 50 per second. The driver kept one open in flight per bot.
Tick samples stay under the medium budget, with the driver queue still holding
unsent work, so the short delivery stays a deficient bot rather than server
saturation.

Medium repeats 1 and 3 stopped during fixture setup because one open returned
`other container did not open`. Those repetitions did not arm the timing chest.
Repeat 2 passed all 280 native container and drag decisions, then its
measurement window completed 47 actions and recorded 243 `other container did
not open` errors. The original audit's "container did not open" at coordinates
`0, -60, 0` is not the result here: timed actions address the placed chest at
`300, -61, 297` and the neighboring owner chests.


