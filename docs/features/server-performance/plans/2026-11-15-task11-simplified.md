# Task 11: focused Paper completion

> **Current required scope (November 15):** [Minimal critical Paper suite](2026-11-15-minimal-critical-scope.md) supersedes the comprehensive delivery and earlier Task 11 gate. Broader unfinished tasks are optional future work; historical results remain unchanged.


This revision responds to the November 15 request to simplify Task 11. It
replaces the Task 11 completion gate in the master plan and the outstanding
Tasks C–E of the November 9 continuation. Historical results remain unchanged.
This is a planning change; the runner does not yet implement this selection.

## What closes Task 11

Complete a focused published-4.0.0 Paper 1.21.11 audit: reuse valid functional
evidence, fill essential gaps with short native checks, and measure three
representative workloads. An observed product FAIL can close an audited case;
missing required evidence cannot. Closing this task does not certify the full
release matrix or create a healthy baseline.

### 1. Reconcile functional evidence once

- [ ] Make one compact evidence table for the groups below. Each entry needs
  its retained root, artifact identity, asserted behavior and PASS/FAIL result.
  Inspect raw evidence before reusing it; a report title or test count is not
  enough. Rerun only missing, invalidated or changed assertions.
- [ ] Close essential gaps using short functional runs. One owner is enough
  except for the owner-isolation check, which uses ten. These checks do not need
  30/60-second phases or three repetitions.

| Group | Required functional evidence |
| --- | --- |
| Navigation | Individual supported methods and all together; movement/arrival, retarget/edit/delete, disable and disconnect cleanup. Reuse existing full method-combination evidence where valid. |
| Lifecycle | Death/respawn, world change, rejoin and restoration of the expected owner/target/methods. |
| Terrain | Exact warm/cold pixels, dimension switch, one save/stop/reopen comparison, and ten-owner map/target isolation when one owner retargets. |
| Protection | Published inventory/container/drag, craft/cartography, drop/pickup/entity/equipment and death handlers; ordinary-item conservation and protected-item expectations. Preserve the nested-depth failure. |

Use the existing standalone native checks where they already produce the
required evidence. Moving every check into the timed runner is not a completion
requirement. Keep assertions independent of the fixture's observed output.
For disconnect, judge available session/task/display/map counters; explicitly
track offline inventory and world-item cleanup in the follow-up table below.

### 2. Measure three representative workloads

- [ ] Add an explicit reduced selection with distinct identities from the
  existing 14-variant catalog. Use the narrowest existing driver/probe path and
  record the action counted, expected native receipt and fixture state.
- [ ] Prove each adapter in a short one-/ten-owner check before timing it.
  Verify active sessions, actual native completion, stable fixture state and
  ordinary/protected item expectations. A sent packet cannot count as work.
- [ ] Run the three workloads below at small (one owner) and medium (ten owners),
  three independent fresh processes each, serially on the host. Keep the
  30-second warm-up, 60-second measurement and existing tick budgets.

| Workload | Measured operation |
| --- | --- |
| Navigation | Retarget between two existing waypoints with all supported navigation methods active; judge the target/items and native command acknowledgement. |
| Terrain | Switch between two prepared map regions; judge native map identity/pixels and received client map updates. |
| Protection | Open/close a placed chest with a fixed ordinary/protected fixture; restore the declared fixture outside the action clock and verify native receipts and conservation for each operation. |

For this representative selection, offer **one operation per owner per second**
(aggregate 1/s small, 10/s medium). This is an explicit lower-load scope change,
not a pass at the original 10/50/s catalog rates. Record fixture-reset overhead
and actual offered/completed counts. If the adapter cannot sustain this rate,
repair or report the delivery gap; do not silently lower it again. Do not use
repeated `/kill` or `/tp` as the representative navigation load.

The reduced gate has **6 cases × 3 repetitions = 18 runs**, with **27 minutes**
of warm-up/measurement windows instead of 126 minutes for the 84-run catalog.
Startup, functional checks, fixture resets and shutdown add time; this is not a
wall-clock estimate. Rerun affected workloads after changes, not the entire
matrix after every harness fix.

### 3. Record the outcome and move on

- [ ] Write one completion report linking the functional table, six measured
  cases, known defects and explicit follow-ups. Close Task 11 only when all
  required functional groups and representative runs have judged evidence.
  Keep every FAIL. No further profile is required unless an unexplained new
  finding needs one; reuse the November 12 profiles for their documented scope.

| Deferred requirement | Owning completion gate |
| --- | --- |
| Full 14-variant small/medium timing at original catalog rates, including vanilla/physical completion adapters | Task 17 original-release audit; implement missing adapters before the full run. |
| Forced terrain-cache eviction, simultaneous same-region viewer/cache stress beyond owner isolation | Task 16 stress/recovery, required before Task 17 comprehensive coverage. |
| Saved/offline inventories, world-item cleanup and repeated resource-leak cycles | Task 16 lifecycle/leak checks, required before Task 17 comprehensive coverage. |
| Fifty-owner stress and 30-minute mixed soaks | Task 16 and the native platform tasks; unchanged. |

These are deferred requirements, not PASS or NOT_APPLICABLE results. Keep them
in the full coverage ledger and registry. Task 17 cannot claim comprehensive
coverage until they execute. Task 12 can start after the reduced Task 11 gate;
it does not need to wait for Task 16/17's broader coverage.

## Execution boundaries

Keep immutable published release bytes, precise tool hashes, fresh owned roots,
native acknowledgements, ordinary-item conservation and interruption-safe
cleanup. Preserve all historical roots and findings. No production repair,
budget relaxation, automatic commit or baseline promotion is part of this
revision. Update the runner's selection and evidence handling before executing;
do not relabel old catalog results to fit the smaller gate.
