# Server Performance Next Steps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution, or superpowers:subagent-driven-development if the user chooses task-by-task agents. This request produces a plan only. Do not commit without an explicit request.

**Goal:** Complete the original Paper Task 11 evidence path, then unblock Task 12 protocol workloads and Task 13 native loader anchors in dependency order.

**Architecture:** Reuse the prepared-run lifecycle, immutable published artifacts, native probes, Azalea actions and existing navigation decision contracts. Move reusable behavior out of standalone smoke scripts into focused scenario modules, then connect those modules to a timed runner. Preserve original-release failures independently of performance measurements and later candidate results.

**Tech Stack:** Python standard library, Java 17-compatible probe sources, Gradle Kotlin DSL, GSON, Querz/NBT, pinned Rust/Azalea and native Paper 1.21.11.

**Spec:** [Implementation specification](../specs/2026-10-24-server-performance-spec.md), [approved design](../specs/2026-10-24-server-performance-design.md), and [master plan](2026-10-24-server-performance.md). This is a continuation of their scope, not a replacement audit or a new production feature.

## Current state and chosen order

Checked against source and local artifact bytes on 2026-11-09 at `5ae679a8`:

| Area | Evidence and remaining work |
| --- | --- |
| Task 10 | Selected published 4.0.1 Paper cases pass with the patched chat decoder. This does not replace the original 4.0.0 audit or establish a healthy baseline. |
| Task 11 | Native functional checks cover methods, lifecycle, terrain, inventory/container/station/entity protection and deletion. `scenarios/__init__.py` still routes all three families to the inventory runner, which blocks player cases. |
| Task 11 tier sizing | `registry.expand_cases` currently emits one client for both small and medium navigation, map-terrain and protection cases. Ten-owner smoke results do not correct this prepared-run configuration. |
| Paper runtime lock | The configured bot hash is `b31f632c…5eddcf5c`, but the local binary hashes to `3d33e868…050478`. The configured probe is `a0b57606…b4160854`, but the local JAR hashes to `987f0f0d…905d0c9`. Reverify tools before creating a new lock; do not edit retained runs. |
| Task 12 | Decision functions exist, but `sync_upload.run_case` unconditionally returns `BLOCKED`. Rust upload actions do not send a waypoint-data body. The master plan's implementation checkbox therefore overstates live execution readiness. |
| Task 13 | Three 1.21.11 loader probes have build evidence. Native readiness and channel negotiation remain unproved; Forge/NeoForge additionally need an envelope-capable client. Missing runtimes were last recorded November 5 and must be rediscovered before execution. |

**Recommendation:** Finish the Task 11 prepared-run path first. It reuses the most mature native evidence and exposes missing tier/workload contracts before additional adapters copy them. Then complete Task 12's codec and traffic path, followed by Task 13's native anchors. Starting the full platform matrix now would mostly generate setup blockers; selecting more production optimizations now would not close the outstanding audit coverage.

The 28 existing Task 11 small/medium cases imply 84 fresh-process repetitions. Their 30-second warm-up plus 60-second measurement windows alone total 126 minutes; startup, behavior checks, persistence, teardown and diagnostic profiles add time. This is a lower bound, not a runtime estimate. Recalculate if coverage adds variants.

## Global constraints

- Audit the published 4.0.0 bytes from the verified inventory, not rebuilt HEAD or published 4.0.1. Keep all three identities distinct.
- Defaults: 30 seconds warm-up, 60 seconds measurement, three independent fresh-process repetitions; 30-minute mixed soak per loader/API family remains a later requirement.
- Medium tick processing: p95 <40 ms and p99 <50 ms. Keep synthetic inventory-100 limits at incremental p95 <=5 ms and allocation <=2,097,152 bytes per workload tick.
- Use 1/10/50 connected owners and active navigation sessions for small/medium/stress Task 11 cases. Zero-player cases must remain zero-player.
- Preserve current declared Task 11 aggregate offered rates of 10/50/100 actions per second; document the operation counted, action mix, deadline and expected native receipt for each variant before measuring. A bot-issued packet alone is not fulfilled work.
- Missing native evidence is `BLOCKED`; incomplete timing or load-generator delivery is `INCONCLUSIVE`; observed product correctness/performance defects remain `FAIL`. A complete original audit may fail.
- Keep existing original stop/quit cleanup, depth-nine escape, deletion and watchdog findings. Current top-level-only protection and removed scans/transfers must not redefine published 4.0.0 expectations.
- Time only one workload at a time on this host. Use fresh owned roots, preserve old attempts and stop only owned processes. Keep profiled diagnostic runs separate from unprofiled acceptance samples.
- Follow repository Java, JSON, helper-scope and Stonecutter rules. Check current settings/active projects and applicable Minecraft-source skills before editing native APIs; use fully qualified Gradle tasks.
- No production repairs, backward-compatibility code, automatic baseline promotion, commits or publication belong to this continuation's first execution slice.

## Review focus

1. Medium cases silently use one owner: Task B pins the expanded client counts and Task D verifies server-observed readiness throughout each window.
2. A short smoke or issued-only bot action passes timing: Task D tests phase lengths, native completion, deadlines and per-action latency evidence.
3. Newer assertions inherit older PASS labels: Task C reruns container checks and audits each terrain/protection assertion against independent expected state.
4. Cleanup, deletion or watchdog failures disappear beneath successful timings: Task D tests combined decisions, continued independent cases and interrupted partial results.
5. Stale binaries, another release or a reused mutable world contaminate evidence: Tasks A and D test hash rejection, immutable provenance and independent repetition roots.

## Task A: Establish a verified Paper tool lock

**Files:** Modify `tools/server-performance/config/runtimes.json` and, only where coverage is missing, `tools/server-performance/tests/test_provenance.py` / `test_runtime_matrix.py`. Record results in a new `docs/features/server-performance/validation/2026-11-09-task11-continuation.md` report (use the actual execution date if later).

**Interfaces:** Consume existing `RuntimeSpec`, `check_runtime(runtime, output, required_capabilities) -> Decision` and artifact verification. Produce a verified runtime-lock revision for new preparations; sealed roots retain their own locks.

- [x] Inspect current `settings.gradle.kts`, `mods/stonecutter.gradle.kts` and `paper/stonecutter.gradle.kts`; record source/dirty-tree identity and tool hashes. Verify the published Paper artifact against the release manifest.
- [x] Check existing provenance tests cover a changed bot/probe byte after locking; add a failing regression only for an uncovered path. Expected result: stale bytes prevent readiness before any sample can count.
- [x] Run the Python harness tests and pinned Rust tests, rebuild the Paper probe and bot, and inspect production/probe archive isolation. Resolve failures before updating lock values.
- [x] Verify connection, native action and probe readiness using the rebuilt bytes in a new disposable root. Only then update the working runtime lock with full hashes and evidence references; do not copy a shortened hash from this document.

Verification commands:

```sh
rtk proxy python3 -m unittest discover -s tools/server-performance/tests -v
rtk proxy cargo +nightly-2026-07-15 test --manifest-path tools/server-performance/bots/versions/mc1.21.11/Cargo.toml --locked
rtk proxy cargo +nightly-2026-07-15 build --release --manifest-path tools/server-performance/bots/versions/mc1.21.11/Cargo.toml --locked
rtk ./gradlew :paper:1.21.11-paper:performanceProbeJar --console=plain --max-workers=2
```

Expected: tool tests/builds pass, production archives contain no probe classes, actual bytes match the new lock, and native readiness/action checks succeed. Compilation alone does not close this task.

## Task B: Correct and pin Task 11 tier expansion

**Files:** Modify `tools/server-performance/registry.py`, `tools/server-performance/config/scenarios.json` only if needed to make the existing rates explicit, and `tools/server-performance/tests/test_contracts.py` / `test_cli.py`.

**Interfaces:** Preserve `expand_cases(features, scenarios, runtimes, mode, tiers) -> tuple[CaseSpec, ...]`. For `navigation`, `map-terrain` and `protection`, set `CaseSpec.clients` from the 1/10/50 tier mapping. Preserve existing storage/command rules and zero-player selection.

- [x] Add `test_navigation_families_use_tier_owner_counts`: expand all three tiers and assert every selected family's clients equal `{"small": 1, "medium": 10, "stress": 50}[case.tier]`. Assert offered rates remain 10/50/100, warm-up 30, measurement 60 and repeats 3.
- [x] Run the focused tests and observe the medium/stress client-count failure before changing expansion.
- [x] Apply the narrow family-specific expansion change. Keep `multiple-viewers` honest: a small one-owner result cannot by itself establish concurrent-viewer behavior; require the medium observation for that coverage claim.
- [x] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_contracts.py' -v` and the equivalent command with `test_cli.py`. Confirm existing zero-player tests pass and the Paper small/medium selection still contains all 28 cases.

## Task C: Make Task 11 behavior reusable and close evidence gaps

**Files:** Create `tools/server-performance/scenarios/navigation_behavior.py`; modify `scenarios/navigation.py`, `tests/test_navigation_scenarios.py`, and existing `bots/{navigation,terrain,dimension_terrain,lifecycle,protection}_smoke.py` as needed. Change `paper/src/performanceProbe/java/_959/server_waypoint/perf/paper/{NavigationAssertions,ProtectionFixtures,LifecycleAssertions,PaperPerformanceProbe}.java` only for a demonstrated missing native observation.

**Interfaces:** Add `exercise_variant(case: CaseSpec, probe, driver: BotDriver, output: Path) -> dict` to `navigation_behavior.py`. Return `decisions` (a tuple of `Decision`), `assertions` (named booleans), and `paths` (retained native evidence paths). Existing scenario functions remain the independent decision layer; smoke entry points delegate reusable behavior without importing process-launching code into the runner.

- [x] Map each of the 14 registered variants to actual actions, native receipt types, independent before/after expectations and relevant published handlers. Record this map in the continuation validation report. Unimplemented variants stay individually blocked.
- [x] Add failing coverage/decision tests for any unmapped variant. Include stale receipts, clean-before fixtures, missing world-item snapshots, ordinary-item loss, wrong-owner changes, and a protected result that incorrectly remains usable.
- [x] Extract existing behavior with the same assertions. Rerun one-/ten-owner container cases with the strengthened assertions: the November 1 review explicitly did not rerun native suites after tightening those checks.
- [x] Audit `multiple-viewers` and `persist-evict-restart` separately: ten distinct map IDs prove owner separation, not every viewer/cache case. Require simultaneous viewers of the same terrain region, independent retargeting, unchanged other-owner pixels/IDs, forced eviction and exact persisted/reopened terrain. Add missing native cases if the retained evidence lacks them. Include chunk/region boundary transitions as well as the already observed dimension switch.
- [x] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_navigation_scenarios.py' -v`, then focused one-/ten-owner native checks for new or changed behavior. Retain the original deletion/depth/cleanup failures as failures; do not repair production code to make the harness pass.

Expected: every Task 11 variant has a concrete native behavior path or an explicit blocker. Functional PASS/FAIL classifications are independent of any later performance result.

## Task D: Integrate prepared, timed Task 11 execution

**Files:** Create `tools/server-performance/scenarios/navigation_runner.py`, `scenarios/navigation_workload.py` and `tests/test_navigation_runner.py`; modify `scenarios/__init__.py` and `tests/test_cli.py`. Reuse `controller.py`, `worlds.py`, `profiles.py`, `budgets.py`, `server_health.py` and `scenarios/storage_runner.py` patterns without copying command-specific behavior.

**Interfaces:** Add `navigation_runner.run_case(case: CaseSpec, root: Path) -> CaseResult`; route the three Task 11 families to it. Add `navigation_workload.collect_window(case: CaseSpec, probe, driver: BotDriver, phase: str, output: Path) -> dict`, returning `seconds`, `tickNs`, `offeredActions`, `completedActions`, `actionLatencyNs` and native receipt paths. The runner maps these into existing `CaseResult.observations["repetitions"]` fields consumed by `budgets.evaluate`.

- [x] Write failing tests for routing all three families, unknown variants, wrong runtime, stale bot/probe/artifact bytes, 10-owner readiness, independent world copies, and exactly three fresh process repetitions.
- [x] Add tests that 29-second warm-up, 59-second measurement, missing samples, missing acknowledgements and mismatched latency clocks cannot pass. Offered/completed accounting must exclude fixture setup and count the declared operation; distinguish a deficient bot from actual server saturation.
- [x] Implement a bounded deterministic action schedule for each variant using its existing offered rate. Correlate actions with native receipts, use client-monotonic roundtrip latency, and collect actual native tick processing through the whole measured window. Do not replace expensive lifecycle operations with unrelated cheap movement while labeling them equivalent.
- [ ] Implement prepare/verify/readiness, Task C behavior checks, warm-up, measurement, quiescence, save/reopen where required, and cleanup using the existing owned-process lifecycle. Behavior checks can precede/follow timing; the measured workload must still exercise its named feature. Retain raw bot/probe streams, resource counts, host/load-generator health and all identities.
- [x] Add tests proving a fast tick window plus failed deletion/cleanup is `FAIL`, a watchdog fails independently, known failures do not skip later variants, and interruption retains prior defects and stops owned processes. Missing timing must not overwrite an observed correctness failure.
- [x] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_navigation_runner.py' -v`, then the complete Python suite. Run a short native adapter check first, explicitly labeled smoke. Preserve unsupported runtimes as `BLOCKED` until their native adapters are verified.

Expected: the CLI executes real navigation/terrain/protection cases instead of the inventory fallback, while incomplete or incorrect evidence still fails closed. Do not add an alternate result schema or relax budgets.

November 12 correction: the lifecycle implementation checkbox stays open because
the prepared runner still lacks the required terrain save/reopen comparisons
and disconnected-inventory/world-item observations. It now records actual JFR
and ten-tick post-disconnect navigation counters. The online-only item counter
does not establish item cleanup. See
[the diagnostic continuation](../validation/2026-11-12-task11-diagnostics.md).

## Task E: Run and report original Paper Task 11

**Files:** Create the continuation validation report named in Task A; update `validation/progress.md`, `validation/coverage-ledger.md`, the master plan's Task 11 checkpoints and feature README from actual evidence only.

- [x] Prepare a fresh root after all executable/config changes. Verify hashes, the full 28-case selection, 1/10 owner counts, rates, sample requirements and deadlines. Repreparation is required after changes to hashed inputs.
- [x] Execute all small/medium variants with three repetitions, serially. Keep independent cases running after known original failures. A valid defect finding exits 1; setup-only/incomplete coverage exits 2; neither is release acceptance.
- [ ] Retain separate local profiles for navigation, terrain and protection and distinct investigated failures, plus save/reopen comparisons and post-quiescence item/session/task/entity/map counts. Record actual elapsed time and host class.
- [x] Reconcile every variant with raw evidence. Close Task 11's implementation/execution checkboxes only when its required scope executed; report coverage and outcome separately. Preserve incomplete cases, failed attempts and original findings, and link the report from the README.

After assigning `SW_PERF_TASK11_ROOT` a fresh absolute directory outside the checkout:

```sh
rtk proxy python3 tools/server-performance/run.py prepare --release-manifest tools/server-performance/config/releases/4.0.0.json --runtime-lock tools/server-performance/config/runtimes.json --output "$SW_PERF_TASK11_ROOT" --targets paper-1.21.11 --suite all --scenarios navigation,map-terrain,protection --tiers small,medium
rtk proxy python3 tools/server-performance/run.py run --root "$SW_PERF_TASK11_ROOT" --mode audit --suite all --scenarios navigation,map-terrain,protection --tiers small,medium --repeats 3
rtk proxy python3 tools/server-performance/run.py report --root "$SW_PERF_TASK11_ROOT"
```

These commands are for execution after Tasks A–D; they are not evidence that any run occurred during planning. All reports remain partial relative to the comprehensive release audit.

## Following slices of the master plan

The following are ordered handoffs, not claims that their implementation is ready. Detail each slice against the source then current when starting it.

| Order | Work and owning files | Exit evidence |
| --- | --- | --- |
| 1 — Task 12 codec | Extend `common/src/performanceFixtures/java/_959/server_waypoint/perf/ProtocolFixtureExporter.java`, `tools/server-performance/config/wire/paper-4.0.0-vectors.json`, `wire_fixtures.py`, `tests/test_wire_fixtures.py` and the pinned bot's Rust codec/action tests. Export real waypoint upload bodies through immutable published Java classes and verify Rust-to-Java acceptance/rejection. | Valid and invalid payloads carry published class/artifact fingerprints; HEAD-built codec output cannot stand in for 4.0.0. |
| 2 — Task 12 execution | Implement upload body transmission in the pinned bot and attach native execution to `scenarios/sync_upload.py`, with orchestration in a focused runner and `tests/test_sync_upload.py`. | Timed small/medium sync, upload/edit and sharing cases; exact persisted revisions/content; empty leases/buffers after rejection/disconnect; local profiles. Valid stress input is four requests of 4,096, while one 16,384 request remains an oversize test. |
| 3 — Task 13 anchors | Rediscover/provision disposable 1.21.11 Fabric first, then Forge and NeoForge runtimes. Extend `config/runtimes.json`, `runtime_matrix.py`, `tests/test_runtime_matrix.py` and `mods/src/performanceProbe/` against native APIs. | Per-loader boot, fixture save/reopen, actual tick boundaries, channel/configuration negotiation, action and cleanup evidence. An envelope-capable bot is mandatory for Forge/NeoForge; Paper-compatible protocol alone is insufficient. Expand to every published artifact/API anchor afterward. |
| 4 — Tasks 14–15 | Implement Folia region timing and the Velocity/two-backend topology using the master plan's separate tasks. | Actual per-region timing and owner safety; authorized exactly-once A/B/return handoffs, queue/resource recovery and proxy latency metrics. Earlier component measurements do not substitute for this coverage. |
| 5 — Tasks 16–17 | Complete mixed stress, family soaks, coverage aggregation and the frozen original audit. | All applicable coverage executed or explicitly unresolved; comprehensive completion cannot be claimed while required runtime cases remain blocked. Original failures remain visible. |
| 6 — Tasks 19–22 | Reproduce remaining findings on current development, repair only demonstrated defects, validate candidates, then add healthy baselines/JMH/PR selection/release gates. | Paired evidence and complete candidate validation. Task 18's focused repair was already handled under the priority-repair plan; do not reimplement it or undo the later protection policy. |

## Handoff and self-review

The recommended next execution scope is **Tasks A–E only**, using inline execution and a review of the completed change. Task 12 and loader provisioning follow as separate slices; timing runs remain serialized.

This plan covers the immediate Task 11 requirements in spec sections 3–10. Sections 4, 6 and 8–12 retain their wider-platform/protocol/soak/repair gates through the master-plan handoffs above. No runtime acceptance checkbox is checked by this planning change. Read-only checks confirmed routing, current case cardinality, tier sizing and two local hash mismatches; no native workload or test suite was run during planning.
