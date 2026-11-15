# Server Performance Implementation Plan — Minimal Critical Scope

This scope replaces the comprehensive audit as the required delivery, following
the user's November 15 request for a minimal scope covering critical cases.
The [former comprehensive plan](2026-10-24-server-performance-comprehensive-historical.md)
and all historical evidence remain available. Unfinished broad
coverage is optional future work, not a prerequisite for this delivery.


**Updated:** November 15, 2026. This is the authoritative implementation plan.
The older design/specification, continuation plans and coverage ledger describe
broader historical requirements; they do not expand this delivery's scope.

**Goal:** Produce a small, trustworthy regression report for a current development
candidate on Paper 1.21.11, covering five critical groups and bounded timing checks.

**Implementation:** Reuse the Python runner, Paper Java probe, Azalea client and
existing disposable fixtures. Keep Java 17-compatible source, Kotlin DSL builds,
GSON, and probe/tooling code outside production JARs. Preserve artifact hashes,
owned process cleanup, native receipts and independent expected-state assertions.
No production repairs, commits, publishing or baseline promotion are authorized
by this documentation revision. Do not start runtime execution from this edit.

## Existing work to reuse

- Original Tasks 1–10 completed the core harness, fixtures, Paper measurement,
  bot/protocol support and selected storage/command audits. Those historical
  results do not establish that the current candidate passes.
- Task 11 supplies native navigation, terrain and protection checks, diagnostic
  support and command acknowledgements. Reconcile only checks needed below.
- Task 12/13 partial adapters and original Tasks 14–22 are not required here.
- Existing performance and correctness failures remain in their original reports.
  The [progress log](../validation/progress.md) preserves detailed history.

## Scope and rationale

Use one native runtime: Paper 1.21.11, with one-owner functional checks and one
ten-owner representative performance workload, plus command-tree delivery timing. Reuse the existing harness,
fixtures and native probes. Do not build new platform adapters for this scope.

A comprehensive platform matrix would retain most of the original implementation
cost. Merely shortening runs would save execution time without removing that cost.
This selection instead removes entire adapters and scenario families while keeping
native completion and independent correctness assertions.

Use an exactly identified current development candidate for the final validation.
This is an implementation choice for a useful current regression suite, not a
claim that the user selected a particular release. Record source/build/artifact
identities before execution. Historical published-release checks can establish
prior findings, but cannot certify changed candidate behavior.

## Required critical cases

| Group | Minimal checks |
| --- | --- |
| Storage and permissions | Create/edit/remove/restore a waypoint; denied mutation leaves state unchanged; save/stop/reopen preserves expected contents. Reuse the existing Task 10 driver and assertions without repeating every command leaf. |
| Command-tree delivery | Measure initial login delivery and an explicit permission-triggered refresh for an ordinary player and an operator; verify the decoded tree reflects permissions. See the timing protocol below. |
| Navigation lifecycle | Start with all supported methods, retarget between two existing waypoints, stop and disconnect; judge target/items and session/task/display/map cleanup. Include a short delete-active-target check against current intended behavior. |
| Terrain map | Compare exact expected map pixels for two prepared regions and after one save/stop/reopen. Check two owners remain isolated when one retargets. |
| Direct item protection | One ordinary/protected inventory move, chest interaction and drop/pickup path; verify direct protected-item expectations and ordinary-item conservation. Use current top-level protection policy. |

Protection expectations must follow current production semantics: recursive nested
cleanup and automatic hopper-transfer protection were intentionally removed.
Do not restore these features or require historical nested-protection expectations
to pass on the current candidate. Preserve original-release failures as historical
findings with their original artifact identities.

## Minimal performance check

- Run one idle control and one ten-owner navigation-retarget workload with all
  supported navigation methods active, using the existing command completion path.
- Offer one retarget per owner per second, between two prepared targets.
- Use 30 seconds warm-up and 60 seconds measurement, with three fresh-process
  repetitions of each case, serially. Six runs have nine minutes of timing
  windows; startup, setup and shutdown add time.
- Keep existing medium tick budgets: p95 <40 ms and p99 <50 ms. Record actual
  offered/completed counts and matched native acknowledgements. Issued packets
  alone cannot establish delivered load.
- Verify target/items and post-disconnect counters. A delivery or observation gap
  remains BLOCKED; never silently reduce load or treat missing evidence as PASS.
- Profile only an unexplained failure. No additional profile or soak is required
  merely to complete this scope.

Functional checks need one fresh execution on the final candidate, without timed
phases or three repetitions. Existing evidence may be reused only when artifact,
fixture and assertion identities still match. Short adapter checks should precede
measured execution; rerun affected checks after relevant changes.

## Command-tree delivery timing

This is a required additional critical test. Follow
[command-tree performance guidance](../../../tips/command-tree-performance.md).
Use Paper 1.21.11 native paths; the existing isolated 26.3 command-tree benchmark
cannot substitute for live delivery evidence on this target.

- Use an empty waypoint catalog, since command grammar alone can cause expansion.
  Test one ordinary player and one operator, sequentially, with the same candidate
  and permission configuration recorded in the fixture.
- In each of three fresh server processes, capture each player's initial command
  tree on login and one refresh following a controlled permission change. Verify
  the ordinary player's grant and the operator's revocation in the received tree.
  Identify the expected Server Waypoint command paths from the actual configuration;
  an unexpected or unchanged permission view is a correctness failure.
- Report native server command-tree preparation/send duration: start at the target
  version's command-list send entry, end when the packet is handed to the connection.
  This includes visible-tree copying and packet construction, but must not be called
  wire delivery or include asynchronous encoding unless instrumented separately.
- Record encoded packet size and client successful decode/installation. For the
  explicit refresh, measure client-local request-to-received-tree latency using a
  native test trigger that changes permissions and invokes the normal refresh path.
  Correlate connection, phase and sequence; permit only one outstanding refresh.
  Initial-login client timing may be reported separately as login-to-tree latency,
  explicitly including other login work. Never subtract server and client clocks.
- Retain every sample, server-thread stall/tick observations, visible node counts,
  packet bytes and expected-permission assertions. Report median and maximum by
  role and trigger; three samples per combination do not justify tail percentiles.
- Missing send instrumentation, unmatched refreshes or absent client receipt are
  BLOCKED, never a zero-duration PASS. Capture diagnostics for unexplained stalls.
  No command-tree-specific latency budget has been established: report the measured
  durations without claiming a latency-budget PASS. Record a proposed budget for
  later review rather than inventing one from the measured candidate.

This adds three short fresh-process sessions, covering two roles and two delivery
triggers each, outside the six idle/retarget runs. It does not add exhaustive
command-leaf execution or a multi-version command-tree matrix.

## Remaining implementation steps

### Step 1: Bind the candidate and minimal selection

**Owning files:** `tools/server-performance/cli.py`, `registry.py`, existing
configuration and provenance modules; feature validation reports.

- [ ] Record exact candidate, runtime, probe, bot and fixture identities.
- [ ] Add an explicit minimal selection with distinct case IDs. Preserve historical
  registry entries; the minimal selection must not schedule the comprehensive suite.
- [ ] Map each of the five groups to existing native checks and list only missing
  observations/adapters. Reuse evidence only when identities and assertions match.

### Step 2: Close the critical functional checks

**Owning files:** existing `scenarios/storage_commands.py`, `scenarios/navigation.py`,
Paper probe assertions and their focused tests.

- [ ] Implement only gaps needed for the storage, lifecycle, terrain and direct
  protection rows above. Check current candidate semantics before fixing expectations.
- [ ] Execute short functional checks against the final candidate, including
  persistence and owner isolation. Retain native observations and independent
  expected values; record product failures separately from missing evidence.

### Step 3: Implement and measure command-tree delivery

**Owning files:** Paper performance-probe sources, existing Azalea driver/action
code, Python scenario integration and focused contract tests.

- [ ] Inspect Paper 1.21.11 native send/packet boundaries and implement the timing
  protocol above without changing production behavior.
- [ ] Verify receipt correlation and permission-tree assertions in a short check;
  reject missing/mismatched receipts and mixed-clock timing.
- [ ] Run three fresh-process sessions covering both roles and both triggers.
  Report server send duration, client timing, packet bytes and node counts separately.

### Step 4: Execute bounded performance checks

- [ ] Prove the existing retarget adapter observes completed native commands and
  expected target/items before timing it.
- [ ] Execute the idle and ten-owner cases with three repetitions each using the
  rates, timing windows and budgets above. Run timed workloads serially.
- [ ] Preserve individual failures, delivery gaps and post-disconnect observations.
  Rerun only checks affected by subsequent relevant changes.

### Step 5: Write the minimal completion report

**Output:** a report under `docs/features/server-performance/validation/`, linked
from the feature README and progress log.

- [ ] Report all five groups, six idle/retarget runs and three command-tree sessions
  with artifact identities, retained roots, metrics and judged outcomes.
- [ ] List known defects and explicit exclusions. Mark missing evidence BLOCKED;
  a reproduced product FAIL can complete an audited case but cannot establish a
  healthy candidate. Command-tree durations have no established latency budget.
- [ ] Run relevant focused tool/probe checks after implementation, verify no probe
  code enters production JARs, and confirm owned processes stop cleanly.
- [ ] Mark this plan complete only when all required evidence exists. Do not require
  any excluded historical task, and do not claim comprehensive release coverage.

No product repair is implied by this scope change. Report demonstrated defects
before choosing their repair scope. No baseline promotion or release certification
is part of this delivery.

## Removed from required delivery

- Exhaustive command leaves, method combinations, terrain boundaries and original
  14-variant timing beyond the selected critical cases.
- New sync/upload/edit/sharing traffic adapters (original Task 12).
- Fabric/Forge/NeoForge version matrix, Folia and Velocity/two-backend coverage
  (Tasks 13–15).
- Fifty-owner stress, long soaks, exhaustive offline/world-item leak cycles and
  advanced cache/viewer stress (Task 16).
- Comprehensive original-release audit, full repair/candidate matrix, healthy
  baselines, JMH, PR selection and publishing enforcement (Tasks 17–22).

These exclusions are untested/out of this delivery's scope, never PASS or
NOT_APPLICABLE. They do not need to be completed later unless separately requested.
The earlier simplified Task 11 gate and its deferred Task 16/17 requirements remain
historical broader proposals; this minimal delivery supersedes their required scope.
