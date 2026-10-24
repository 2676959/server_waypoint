# Server Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution, or superpowers:subagent-driven-development if the user chooses task-by-task agents. Steps use checkbox (`- [ ]`) syntax for tracking. Do not start implementation during the documentation-only request.

**Goal:** Audit all implemented server-side features in published 4.0.0 across tiered real-world and bot workloads, repair demonstrated defects, and reuse the evidence for future regressions.

**Architecture:** A Python CLI controls immutable release assets, disposable native server worlds, Java telemetry probes and Rust Azalea bots. Querz/NBT generates structure fixtures which each target Minecraft runtime places and saves. Original-release discovery precedes source repairs, component benchmarks and CI/publishing enforcement.

**Tech Stack:** Python 3.11+ standard library, Java 17-compatible source, target-specific runtime JDKs, Gradle Kotlin DSL, GSON, Querz/NBT, Rust/Azalea, JUnit 5, later JMH, spark and JFR.

**Spec:** [Implementation specification](../specs/2026-10-24-server-performance-spec.md), derived from the [approved design](../specs/2026-10-24-server-performance-design.md).

## Global Constraints

- First audit the 44 published 4.0.0 JARs recorded in the verified asset inventory; rehash actual bytes. Rebuilding HEAD is not a published-release audit.
- Java code uses Java 17-compatible language features; runtime JDKs remain target-specific. Gradle scripts use Kotlin DSL; Java JSON uses GSON; indentation is four spaces.
- Azalea is required for real connected bots; Querz/NBT is required for world fixtures.
- Both player-driven and zero-player world workloads require multiple load tiers.
- Defaults: 30 seconds warm-up, 60 seconds measurement, three independent fresh-process repetitions; 30-minute mixed soak per loader/API family.
- Medium tick processing: p95 <40 ms and p99 <50 ms, including each active Folia region. Synthetic inventory-100: incremental p95 <=5 ms and allocation <=2,097,152 bytes per workload tick.
- No commits without an explicit user request. Each task ends with reviewable changes and validation evidence, not an automatic commit.
- No pushes, uploads, publishing, production-server changes or product compatibility layers. New tooling and probes stay out of release JARs.
- Preserve unrelated Private work, Java API contracts, permissions, item protection and inactive Stonecutter branches. Fully qualify version tasks; preserve current active projects.
- Initial source repair base is the current development branch; distinguish its artifacts from immutable published 4.0.0 and repaired candidates.

## Review Focus

1. A paused/unticked zero-player world must never pass as a cheap active workload; Task 7 proves activity and distinguishes fixture failure from server saturation.
2. A protocol/version/channel mismatch or stalled Azalea swarm must never count as connected-player load; Tasks 8, 9 and 13 prove server-side readiness and achieved actions.
3. Stale JARs, altered fixtures, source-built codecs, or a partial report must never masquerade as published/full-release evidence; Tasks 1, 2, 9, 17 and 22 reject them.
4. Unsupported Folia timing, profiler failure, NaN/empty samples, or mismatched clocks must never create a numeric pass; Tasks 5, 6 and 14 exercise those failures.
5. Interruption, reconnect and cleanup races must not leak processes/items/sessions or erase independent failures; Tasks 3, 11, 15 and 16 retain evidence and assert ownership/cleanup.

## File structure and shared interfaces

Use `T = tools/server-performance` in this document's prose only; paths in tasks are explicit.
Keep each module focused; no code is created by this plan itself.

| Files to create | Responsibility |
| --- | --- |
| `tools/server-performance/run.py`, `cli.py` | Argument parsing/dispatch only. |
| `tools/server-performance/models.py`, `contracts.py`, `registry.py` | Immutable typed input/results, JSON validation and complete coverage expansion. |
| `tools/server-performance/artifacts.py`, `preflight.py` | Asset staging/hash checks and runtime capability evidence. |
| `tools/server-performance/processes.py`, `controller.py`, `worlds.py` | Owned process groups, attempt state machine and immutable fixture cloning. |
| `tools/server-performance/metrics.py`, `budgets.py`, `profiles.py` | Raw sample validation/statistics, decisions and local diagnostic capture. |
| `tools/server-performance/bot_driver.py`, `probe_driver.py` | Validated JSON protocol streams; no workload algorithms. |
| `tools/server-performance/scenarios/` | `inventory.py`, `storage_commands.py`, `navigation.py`, `sync_upload.py`, `cross_server.py`, `mixed.py`. |
| `tools/server-performance/reporting.py`, `gate.py`, `selection.py` | Reports/findings, evidence validation and later PR selection. |
| `tools/server-performance/config/` | `releases/4.0.0.json`, `runtimes.json`, `features.json`, `scenarios.json`, `budgets.json`, `worlds/*.json`. |
| `tools/server-performance/tests/` | Standard-library unit/contract tests, simulator children and small recorded protocol fixtures. |
| `tools/server-performance/worldgen/` | Standalone `settings.gradle.kts`, `build.gradle.kts`, Java source/tests. |
| `tools/server-performance/bots/` | Pinned Rust projects under `versions/<protocol>/`, shared scenario contract fixtures and JSON protocol documentation. |
| `cross-server/src/performanceProbe/java/_959/server_waypoint/perf/` | Platform-free test-only `ProbeCommand`, `ProbeEvent`, `ProbeWriter`; backend/proxy shared scope. |
| `paper/src/performanceProbe/`, `mods/src/performanceProbe/`, `velocity/src/performanceProbe/` | Platform-native placement, measurement, assertions and trigger adapters. |
| `common/src/performanceFixtures/` | Test-only codec exporter; audit mode loads exact published production classes. |
| `docs/features/server-performance/validation/` | Progress, coverage ledger, original/repaired reports and summaries linking to retained raw evidence. |

`models.py` defines frozen dataclasses `ArtifactSpec`, `RuntimeSpec`, `ScenarioSpec`,
`CaseSpec`, `MetricSample`, `CaseResult`, `Decision`, `AttemptRecord`, `RunRecord` and
`OwnedProcess`. Their JSON fields are the spec's sections 3–10. `Status` has exactly
`PASS`, `FAIL`, `INCONCLUSIVE`, `BLOCKED`, `NOT_APPLICABLE`; unknown schemas are errors.
JSON examples are contract fixtures, not a backward-compatible import format.

Python public functions consumed across tasks:

```python
load_release(path: Path) -> tuple[ArtifactSpec, ...]
load_runtimes(path: Path) -> tuple[RuntimeSpec, ...]
expand_cases(features: Path, scenarios: Path, runtimes: tuple[RuntimeSpec, ...], mode: str, tiers: tuple[str, ...]) -> tuple[CaseSpec, ...]
stage_artifacts(artifacts: tuple[ArtifactSpec, ...], cache: Path, output: Path) -> dict[str, Path]
check_runtime(runtime: RuntimeSpec, output: Path, required_capabilities: frozenset[str]) -> Decision
start_owned(argv: list[str], cwd: Path, log: Path) -> OwnedProcess
stop_owned(process: OwnedProcess, grace_seconds: float) -> None
prepare_world(runtime: RuntimeSpec, recipe: Path, output: Path) -> Path
run_case(case: CaseSpec, root: Path) -> CaseResult
evaluate(result: CaseResult, budgets: Path) -> Decision
write_report(run: RunRecord, root: Path) -> None
verify_release(root: Path, artifacts: Path, profile: str) -> Decision
```

The fixture generator CLI is `generate --recipe FILE --output NEW_DIR` and
`verify --manifest FILE --input DIR`. Its package is `_959.server_waypoint.perf.worldgen`.
The native probe contract is spec section 7; the Azalea driver contract is section 6.

## Milestones and ordering

- **M0, Tasks 1–7:** measurable Paper 1.21.11 reproduction and real zero-player worlds;
  this milestone needs no bots and must not wait for a 50-player swarm.
- **M1, Tasks 8–16:** remaining feature families, Azalea traffic and platform adapters.
- **M2, Task 17:** comprehensive original-release report, including independent failures/blockers.
- **M3, Tasks 18–20:** evidence-backed repairs, candidate validation and healthy baselines.
- **M4, Tasks 21–22:** later component/PR optimization and release enforcement.

Tasks are ordered by consumed interfaces. Do not run multiple timed server workloads on
one worker concurrently. A required proxy/two-backend topology is one recorded workload.
Compile/check independent tools in parallel only if doing so cannot contaminate timing.
Before execution, use the worktree skill if isolation is needed; no worktree is needed
for this documentation-only step.

---

### Task 1: Result contracts and exhaustive coverage ledger

**Files:** Create `tools/server-performance/{run.py,cli.py,models.py,contracts.py,registry.py}`, `config/{features.json,scenarios.json,budgets.json}`, `tests/test_contracts.py`; create `docs/features/server-performance/validation/coverage-ledger.md`.

**Interfaces:** Produces all dataclasses, `Status`, `load_release`, `load_runtimes`, `expand_cases`; CLI `inventory` and result serialization at schema 1.

- [x] Write `test_missing_applicable_case_blocks`, `test_private_is_not_applicable_only_for_release`, `test_unknown_schema_rejected`, `test_nonfinite_samples_rejected`, `test_unmapped_entrypoint_rejected`. Assert `BLOCKED` for absent required cases, and `NOT_APPLICABLE` only with a release/source reason; `float('nan')`/`float('inf')` are invalid.
- [x] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_contracts.py' -v`; confirm the new contracts are missing/failing before implementation.
- [x] Implement the exact interfaces above. Enumerate release command leaves/suggestions, event/mixin registrations, background tasks and server settings into the ledger; map all feature IDs in spec section 8. Expand parameter variants deterministically and show case cardinality before execution.
- [x] Run the same tests; inventory must list all expected cases without starting servers. Check that deliberately removing one coverage mapping fails. Record completion in `validation/progress.md` without checking runtime acceptance boxes.

### Task 2: Immutable release artifacts and runtime capability preflight

**Files:** Create `tools/server-performance/{artifacts.py,preflight.py}`, `config/releases/4.0.0.json`, `config/runtimes.json`, `tests/test_provenance.py`; read the existing published inventory and release-revision build metadata.

**Interfaces:** Consumes `ArtifactSpec`/`RuntimeSpec`; produces `stage_artifacts`, `check_runtime` and CLI `doctor`. Every runtime lock entry identifies its exact Java/server/loader/bot/probe artifacts.

- [ ] Write `test_filename_cannot_override_digest`, `test_cached_bytes_are_rehashed`, `test_partial_matrix_blocks`, `test_unknown_bot_mapping_blocks`, `test_zero_player_selection_does_not_require_bots`, `test_release_and_head_provenance_differ`. Assert one changed byte rejects staging and a missing required native mapping yields `BLOCKED`, never exclusion; the zero-player selection remains runnable without bot binaries.
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_provenance.py' -v`; verify failure before adding the implementation.
- [ ] Implement staging from hash-verified copies/downloads and runtime lock validation. Populate initial Paper 1.21.11 capability proof; add explicit entries for every other asset/runtime requirement, retaining unverified capabilities as blockers. Pin/check Querz/NBT 6.1, the first Azalea candidate and local toolchains without changing global defaults.
- [ ] Run tests and `inventory`/`doctor` against the first runtime. Save runtime class/source evidence and exact blocker list. Test all 44 inventory names are accounted for; do not fetch a replacement release automatically if latest changes.

### Task 3: Owned processes and interruption-safe attempt lifecycle

**Files:** Create `tools/server-performance/{processes.py,controller.py,probe_driver.py}`, `tests/test_lifecycle.py`, `tests/fixtures/child_server.py`.

**Interfaces:** Consumes prepared/runtime identities; produces `start_owned`, `stop_owned`, atomic `AttemptRecord` transitions and `SW_PERF` stream parsing.

- [ ] Write `test_existing_or_symlinked_root_refused`, `test_only_owned_process_group_stopped`, `test_partial_report_survives_interrupt`, `test_stale_probe_reply_rejected`, `test_port_collision_reported`. A sentinel child outside the run must stay alive after teardown; interrupted state must have exit code 130 and retained logs.
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_lifecycle.py' -v`; confirm the new lifecycle is not implemented.
- [ ] Implement spec section 3's state machine, collision-checked ports, monotonic deadlines, graceful stop/kill escalation and atomic result files. Use argv arrays and UUID ownership markers; test child simulators are not runtime performance evidence.
- [ ] Run tests and an isolated native Paper startup/stop. Inject interruption in STARTING and MEASURING; verify both reports survive and no unrelated process or directory changes.

### Task 4: Querz/NBT fixture generator and independent manifest

**Files:** Create `tools/server-performance/worldgen/{settings.gradle.kts,build.gradle.kts}`, `src/main/java/_959/server_waypoint/perf/worldgen/{WorldgenMain.java,FixtureRecipe.java,StructureWriter.java,FixtureManifest.java}`, matching `src/test/.../StructureWriterTest.java`; create `tools/server-performance/config/worlds/*.json`.

**Interfaces:** Consumes spec section 5 recipes; produces generated `.nbt` structures, JSON manifest and hashes via `generate`/`verify`. Generator output is input to native placement, not a complete validated world by itself.

- [ ] Write `sameRecipeProducesSameTemplates`, `hopperFacingAndInventoryMatchRecipe`, `negativeChunkBoundaryCoordinatesPreserved`, `nestedItemMetadataPreserved`, `rejectsLiveOrExistingOutput`. Assert seed `95920261024`, palette positions/facing, expected item identities/counts, and reproducible content hashes.
- [ ] Run `rtk ./gradlew -p tools/server-performance/worldgen test`; confirm generator behavior tests fail before implementing it.
- [ ] Implement Java 17-compatible generic `CompoundTag`/`ListTag` structure serialization with the pinned Querz/NBT API and GSON recipes/manifests. Include idle, four container variants, navigation terrain, A/B and mixed recipes; no high-level legacy MCA conversion or live save edits.
- [ ] Run tests and generate small fixtures twice in distinct new roots. Independently inspect expected palette/block/entity inventories. Record dependency artifact hash. Native load/save/reopen validation remains Task 5/7 and must stay unchecked here.

### Task 5: Paper probe, native fixture validation and tick boundaries

**Files:** Modify `cross-server/build.gradle.kts`, `paper/build.gradle.kts`; create `cross-server/src/performanceProbe/java/_959/server_waypoint/perf/{ProbeCommand.java,ProbeEvent.java,ProbeWriter.java}`, `paper/src/performanceProbe/java/_959/server_waypoint/perf/paper/{PaperPerformanceProbe.java,PaperFixtureAdapter.java,PaperTickProbe.java}`, `paper/src/performanceProbe/resources/plugin.yml`, `tools/server-performance/worlds.py`, `tests/test_probe_contract.py`.

**Interfaces:** Produces `performanceProbeContractJar`, Paper `performanceProbeJar`, spec section 7 probe commands/events and `prepare_world`. Shared contract classes contain no Minecraft/Adventure types and ship only in test probes.

- [ ] Write `test_wrong_run_or_sequence_rejected`, `test_readiness_requires_native_fixture_verification`, `test_callback_interval_is_not_tick_duration`, `test_missing_timing_capability_blocks`, `test_probe_boots_without_sw_in_declared_control`, `test_candidate_cannot_omit_sw`. Assert actual fixture/duration capabilities in `hello`; reject a probe reporting only task intervals.
- [ ] Run the Python tests and contract-source Java tests before implementing handlers. Verify target Paper API/source tick timing and structure placement signatures; record those signatures rather than inventing them.
- [ ] Implement native placement/state validation, real tick start/end collection, cheap counters and save/reopen control. Bind audited classes from the installed release plugin through an optional adapter; the base fixture/telemetry probe must boot identically in no-plugin controls. Keep contract/Querz/bot code outside production packaging; native probe may consume NBT templates, not generate fixture semantics again.
- [ ] Build `rtk ./gradlew :cross-server:performanceProbeContractJar :paper:1.21.11-paper:performanceProbeJar --console=plain --max-workers=2`. Boot an isolated server, validate a fixture, save/stop/reopen and validate again. Inspect both production and probe archives for class duplication; record exact timing capability and overhead control.

### Task 6: Metric decisions and local diagnostic capture

**Files:** Create `tools/server-performance/{metrics.py,budgets.py,profiles.py,reporting.py}`, `tests/test_measurements.py`; extend `config/budgets.json`.

**Interfaces:** Produces `evaluate`, `write_report`, nearest-rank sample summaries and local spark/JFR evidence. `run_case` returns a typed `CaseResult` even when capture or sampling fails.

- [ ] Write `test_p99_not_callback_spacing`, `test_bad_repeat_not_averaged_away`, `test_thread_allocations_not_process_allocations`, `test_profile_failure_retains_measured_failure`, `test_empty_or_wrong_unit_samples_inconclusive`, `test_complete_failing_audit_is_not_incomplete`. Assert p95=40 ms violates strict `<40`; 5 ms/2,097,152 bytes satisfy the inclusive inventory limits.
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_measurements.py' -v`; confirm missing calculations/decisions fail.
- [ ] Implement sample validation, 30/60-second phases, three independent repetitions, absolute/relative decisions, scope/clock metadata and provisional-budget classification. Capture local profiles with capability-checked save/cancel paths; never auto-upload. Preserve raw samples and incompatible pair reasons.
- [ ] Run tests, then an idle Paper sample plus profiled diagnostic rerun. Verify measured processing vs wall spacing, profile files exist and no report claims an unprofiled or unsupported metric. Inject a missing profiler/NaN/empty sample and confirm honest status.

### Task 7: Known inventory reproduction and zero-player physical worlds

**Files:** Create `tools/server-performance/scenarios/inventory.py`, `tests/test_inventory_scenarios.py`; create `paper/src/performanceProbe/java/_959/server_waypoint/perf/paper/InventoryWorkload.java`; extend `config/{scenarios.json,worlds/*.json}`.

**Interfaces:** Consumes validated world/probe/metrics; implements `run_case` variants for `idle`, `world-container`, `inventory-regression`. Reports synthetic dispatch and physical throughput with different counters/units.

- [ ] Write `test_zero_clients_required`, `test_inactive_control_world_invalid`, `test_slow_candidate_throughput_is_failure`, `test_synthetic_count_not_physical_count`, `test_known_bad_result_kept_while_next_case_runs`. Assert a lagging candidate with active mechanisms is not discarded as invalid setup.
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_inventory_scenarios.py' -v`; confirm the runner/decision behavior fails before implementation.
- [ ] Implement real circulation fixtures at 16/128/512 cells, activity/pause/region checks, conservation and control pairing. Port the historical synthetic reproduction into bounded per-tick workloads with warm-up and actual server-created items. Include 0/100/1,000 stages, nested/direct positives and no-session negatives; do not repair production yet.
- [ ] Run the original hash-verified Paper 1.21.11 JAR with zero clients through synthetic and real-world cases. Verify recorded zero player counts and sustained physical transfers, and retain the expected original defect as `FAIL` if reproduced. Capture spark/JFR; run a following independent idle case to prove discovery continues. If not reproduced, retain that result and investigate runtime differences honestly.

### Task 8: First Azalea swarm and observed gameplay actions

**Files:** Create `tools/server-performance/bots/versions/mc1.21.11/{Cargo.toml,Cargo.lock,rust-toolchain.toml,src/main.rs,src/driver.rs,src/actions.rs,tests/driver_contract.rs}`, `tools/server-performance/bot_driver.py`, `tests/test_bot_driver.py`.

**Interfaces:** Consumes section 6 JSON commands; produces readiness/action/rate events and 1/10/50 actual connected-player sessions. First native pin is the verified Azalea 1.21.11 candidate, with its compatible local Rust pin.

- [ ] Write `test_ready_requires_server_online_count`, `test_stalled_driver_cannot_pass`, `test_reconnect_does_not_hide_disconnect`, plus Rust `rejects_stale_action_ids` and `barrier_waits_for_acknowledged_actions`. Assert each bot's server UUID and completed action count, not just Rust swarm membership.
- [ ] Run Python tests and `rtk proxy cargo test --manifest-path tools/server-performance/bots/versions/mc1.21.11/Cargo.toml --locked`; document dependency/setup failure separately from a behavior failure.
- [ ] Implement argv/stdin/stdout control with stderr logs, unique offline roles, staggered joins, bounded queues and normal movement/container/command APIs. Keep monotonic client latency local; record driver CPU/RSS/action lag. Disable automatic reconnect during explicit disconnect cases.
- [ ] Build optimized/locked bots, connect 1 then 10 then 50 to the isolated native server, and verify server-side counts, movement, inventory actions and orderly disconnect. Compare offered/completed rates and driver capacity; this proves only the recorded native version.

### Task 9: Release codec fixtures and Server Waypoint bot protocol

**Files:** Modify `common/build.gradle.kts`; create `common/src/performanceFixtures/java/_959/server_waypoint/perf/ProtocolFixtureExporter.java`, `tools/server-performance/bots/versions/mc1.21.11/src/waypoint_protocol.rs`, `tools/server-performance/tests/test_wire_fixtures.py`, `tools/server-performance/config/wire/`.

**Interfaces:** Produces `:common:exportPerformanceFixtures -PperformanceArtifact=<published-jar> -PperformanceFixtureOutput=<new-dir>`; schema-identified golden vectors feed Rust compatible/incompatible modes.

- [ ] Write Java/Rust cross-language tests for exact handshake protocol 2, channel registration, compressed chunks, upload/edit/catalog values and invalid header/checksum/sequence/limits. `test_exporter_rejects_wrong_production_class_hash` must reject current build outputs substituted for the release classes.
- [ ] Run exporter and Rust tests before implementation; verify failure is an absent codec/vector/handshake behavior, rather than ignoring missing fixtures.
- [ ] Implement audit exporter compilation/runtime classpath against the supplied published JAR plus pinned external dependencies, excluding current `main`/project output classes. Fingerprint actual loaded production classes. Add payload handling through Azalea packet APIs and retain wire fixture round-trip evidence in both directions.
- [ ] On native Paper, run vanilla, compatible and incompatible joins; require accepted protocol 2, exact downloaded revisions, expected denial for incompatible messages and released buffers on disconnect. Do not claim client Xaero/VoxelMap rendering or Forge envelope coverage here.

### Task 10: Storage, command leaves, permissions and feedback workloads

**Files:** Create `tools/server-performance/scenarios/storage_commands.py`, `tests/test_storage_commands.py`; extend `config/scenarios.json`, release coverage ledger and Paper probe state assertions.

**Interfaces:** Produces `storage`/`commands` cases using Azalea and console paths; consumes manifest operation rates and independent saved-state assertions.

- [ ] Write `test_each_command_leaf_has_execution_evidence`, `test_denied_mutation_preserves_revision`, `test_restore_is_single_use`, `test_stale_edit_rejected`, `test_persisted_state_matches_committed_operations`. Assert exact names/revisions/counts and no mutation on permission denial.
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p 'test_storage_commands.py' -v`; verify new dispatch/assertions fail first.
- [ ] Implement read/suggestion rates 10/50/100 and mutation rates 1/5/10 per second; all release command leaves, permission configurations, layout/search/sort/paging/details/help and console/execute-as paths. Add skewed-list fixtures, snapshots/revisions, rename/remove/restore, local teleport, config/language reload and action-log cost cases.
- [ ] Execute small/medium Paper cases with exact disk validation after save/restart, recording latency and process allocation. Use source-backed command-family grouping only for timing; require separate behavior evidence for each leaf/suggestion and denial branch.

### Task 11: Navigation methods, terrain maps and protection correctness

**Files:** Create `tools/server-performance/scenarios/navigation.py`, `tests/test_navigation_scenarios.py`; create `paper/src/performanceProbe/java/_959/server_waypoint/perf/paper/NavigationAssertions.java`; extend recipes/registry.

**Interfaces:** Produces `navigation`, `map-terrain`, `protection` cases; exposes before/after session/task/entity/item/map snapshots. Consumes real bot actions and native owner-thread assertions.

- [ ] Write `test_all_supported_methods_and_combinations_executed`, `test_retarget_updates_items_and_displays`, `test_nested_item_cannot_escape`, `test_plain_items_not_deleted`, `test_disconnect_cleanup_returns_to_expected_counts`. Assert individual compass/map/bossbar/actionbar/text-display and all supported methods together; enumerate smaller combinations for correctness.
- [ ] Run focused tests before case implementation. Read the actual release protection handlers and versioned Minecraft API sources before selecting native triggers; add every discovered applicable event/mixin to the ledger.
- [ ] Implement movement/arrival/edit/delete, stop/start/retarget/death/respawn/world-change/rejoin/session restore; warm/cold map regions/dimensions, persistent terrain, eviction and multiple viewers. Cover inventory, crafting/result/cartography, container/entity/equipment/death protections where implemented; distinguish legitimate cleanup from item loss.
- [ ] Run small/medium original Paper cases, including save/restart terrain validation and post-quiescence resource counts. Verify ordinary item handling, owner isolation and protected items independently of timing; retain failures without repairing them yet.

### Task 12: Synchronization, upload/edit traffic and chat sharing

**Files:** Create `tools/server-performance/scenarios/sync_upload.py`, `tests/test_sync_upload.py`; extend Azalea protocol actions and production-codec vectors.

**Interfaces:** Produces `sync`, `upload-edit`, `sharing-icons` cases with full/incremental revisions, expected acknowledgements, transfer-resource snapshots and save assertions.

- [ ] Write `test_download_matches_exact_revision`, `test_invalid_upload_never_commits`, `test_partial_disconnect_releases_lease`, `test_backpressure_recovers`, `test_unrelated_chat_does_not_mutate`. Check client/server ownership and that forced refresh still delivers unchanged revisions.
- [ ] Run focused Python/Rust codec/action tests before implementing scenario execution.
- [ ] Implement compression on/off, concurrent 1/2/8 clients and valid 1,000/4,096/16,384 fixtures within target limits, merge/conflict/delete policies, malformed/oversize/timeout/cancel cases, broadcast fan-out and channel/world identity reconnects. Add valid/invalid/long/duplicate sharing messages, denied permissions and icon registration checks.
- [ ] Run original Paper small/medium cases with exact persisted contents and empty lease/buffer queues after recovery. Profile compression/encoding/merge and unrelated-chat paths; keep correct rejection outcomes separate from fulfilled valid-upload throughput.

### Task 13: Native Fabric, Forge and NeoForge coverage

**Files:** Modify `mods/{fabric.gradle.kts,fabric-unobfuscated.gradle.kts,forge.gradle.kts,neoforge.gradle.kts,neogradle.gradle.kts}` as needed only for test source sets; create `mods/src/performanceProbe/java/_959/server_waypoint/perf/mods/{ModPerformanceProbe.java,ModFixtureAdapter.java,ModTickProbe.java}`, per-loader entry points/metadata, `tools/server-performance/tests/test_runtime_matrix.py`; extend pinned Azalea builds.

**Interfaces:** Produces Fabric `remapPerformanceProbeJar`, other loader `performanceProbeJar`, verified native runtime/channel mappings and the same probe contract/feature registry as Paper.

- [ ] Write `test_all_published_mod_artifacts_have_native_anchors`, `test_channel_envelope_capability_required`, `test_unverified_azalea_version_blocks`, `test_probe_classes_absent_from_production_jars`. Missing setup cannot produce `NOT_APPLICABLE` for a supported feature.
- [ ] Run matrix tests before adapter/config implementation. Verify native Minecraft/loader placement, tick, item component and channel APIs from target sources; read applicable Stonecutter skills and preserve alternate branches.
- [ ] Implement one native Fabric, Forge and NeoForge anchor first, including configuration/channel handshake acceptance; expand to every release artifact's required anchors. Use exact Azalea revisions/builds per native protocol and ledger every unresolved mapping. Trigger production loader hooks/mixins, not copied server logic.
- [ ] Build fully qualified probe tasks and run native readiness/placement/codec/action/cleanup checks for each anchor. Check balanced Stonecutter markers and compare production archive contents; record build-only and runtime results separately. Preserve blocked targets rather than substituting another loader.

### Task 14: Folia region timing and unattended activity

**Files:** Create `paper/src/performanceProbe/java/_959/server_waypoint/perf/paper/FoliaRegionProbe.java`, `tools/server-performance/tests/test_folia_measurements.py`; extend `config/runtimes.json` and `worlds.py`.

**Interfaces:** Produces native region IDs, processing samples and owner-scheduler assertions; consumes the existing probe contract and zero-player fixture conditions.

- [ ] Write `test_one_slow_region_fails_even_if_global_average_passes`, `test_global_task_interval_rejected`, `test_unattended_region_must_tick`, `test_wrong_region_access_reported`. Assert every active region's required samples and timing capability.
- [ ] Run focused decision tests; inspect pinned Folia source/runtime metrics and prove actual region processing boundaries before implementing the adapter.
- [ ] Implement region-owned placement/counters/player actions with independent region sampling and zero-client ticking controls. If valid native timing cannot be obtained, record `BLOCKED` with evidence rather than inventing a global approximation.
- [ ] Run separated region fixtures and simultaneous bot/transfer cases, plus zero-player real hoppers; verify one overloaded region is visible and other regions remain independently observable. Save/reopen and compare owner-safe state before accepting coverage.

### Task 15: Cross-server transport, catalogs and Velocity handoffs

**Files:** Modify `velocity/build.gradle.kts`; create `velocity/src/performanceProbe/java/_959/server_waypoint/perf/velocity/{VelocityPerformanceProbe.java,ProxyAssertions.java}`, test-only metadata, `tools/server-performance/scenarios/cross_server.py`, `tests/test_cross_server_scenarios.py`.

**Interfaces:** Produces Velocity `performanceProbeJar`, real A/B topology cases for `transport`, `catalog`, `handoff`; consumes pinned backend/proxy artifacts, Azalea connections and shared probe events.

- [ ] Write `test_only_one_authorized_handoff_completes`, `test_denied_destination_never_teleports`, `test_delta_revision_and_visibility_match`, `test_reconnect_releases_pending_state`, `test_proxy_metrics_do_not_use_tps`. Assert source/destination player route, final coordinates and cleaned request/queue state.
- [ ] Run focused tests before implementing topology/cases. Inspect existing backend handoff services, proxy registry/transport and Velocity runtime tests for actual limits and routing contracts.
- [ ] Implement distinct world/catalog A/B fixtures, real pairing/Noise sessions, publication/deltas/fan-out, remote suggestions/details, offline/stale peers, rotation/reconnect and transfer/return. Add bounded valid/malformed/partial/replayed transport and concurrent handoff/expiry/cancel/rejection cases without blocking game/region threads.
- [ ] Execute original release through Velocity with two real backends; test same-server destinations and A/B return. Retain all three processes' CPU/GC/queue/latency and local profiles, exact authorized outcomes and cleanup after disconnect/restart.

### Task 16: Mixed stress, leak cycles and consolidated findings

**Files:** Create `tools/server-performance/scenarios/mixed.py`, `tests/test_soak_reporting.py`; extend `reporting.py`, `config/scenarios.json`; create native resource snapshots where missing.

**Interfaces:** Produces `mixed-soak`, 30-minute family-level soak results, `coverage.json`, `findings.json`, `report.json` and `report.md` with separate coverage/outcome axes.

- [ ] Write `test_failed_family_does_not_abort_independent_family`, `test_cleanup_leak_fails_without_tps_loss`, `test_steady_heap_noise_is_not_a_leak_claim`, `test_expected_saturation_rejects_and_recovers`, `test_interrupted_soak_not_pass`. Assert leaked sessions/items/tasks/buffers are failures even with fast average ticks.
- [ ] Run focused tests before implementing mixed scheduling and report aggregation.
- [ ] Implement declared-rate mixed navigation/container/mutation/upload/catalog/save cycles, disconnect/rejoin and recovery. Compare post-quiescence resource counts and repeated retained-heap trends; keep dropped workload, fixture failure, server saturation and bot overload distinct. Generate per-finding reproduction commands and evidence links.
- [ ] Run stress and 30-minute soaks per verified loader/API family plus Folia and Velocity; collect profiles for each family/distinct investigated failure. Confirm original failures and blockers persist in the aggregate report and all owned processes stop after interruption.

### Task 17: Execute and freeze the comprehensive original-release audit

**Files:** Create `docs/features/server-performance/validation/original-4.0.0/README.md`, `coverage.json`, `findings.json`, `report.md`; update `validation/progress.md` and acceptance checklist. Raw evidence stays in the owned run root.

**Interfaces:** Consumes Tasks 1–16; produces immutable original-release audit evidence and a prioritized, reproducible findings list. No source repair belongs in this task.

- [ ] Add `test_full_audit_rejects_missing_runtime_family` and `test_report_is_bound_to_original_artifacts` in `tests/test_audit_completion.py`. Assert filtered runs cannot satisfy comprehensive coverage and one stale fixture/JAR invalidates the corresponding case.
- [ ] Run focused tests, then implement complete audit scheduling/report binding. Calculate case count and compute requirements from the expanded matrix; record actual elapsed time and host class rather than promising an unmeasured duration.
- [ ] Run `rtk proxy python3 tools/server-performance/run.py prepare --release-manifest tools/server-performance/config/releases/4.0.0.json --runtime-lock tools/server-performance/config/runtimes.json --output "$SW_PERF_RUN_ROOT"`, with `SW_PERF_RUN_ROOT` set to a fresh absolute path; then run `run --root "$SW_PERF_RUN_ROOT" --mode audit --suite all --tiers small,medium,stress --repeats 3`.
- [ ] Review actual original-release coverage, every performance/correctness failure and setup blocker. Preserve failing exit status and original files. Do not call the audit comprehensive with unexecuted applicable cases; continue resolvable setup work and state remaining blockers explicitly. No baseline promotion is allowed from the defective release.

### Task 18: Repair the demonstrated Paper inventory hot path

**Files:** Modify `paper/src/main/java/_959/server_waypoint/navigation/PaperNavigationItemManager.java` and `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java` only where proven necessary; create `paper/src/test/java/_959/server_waypoint/navigation/PaperNavigationItemProtectionTest.java`; extend the native performance cases and finding evidence.

**Interfaces:** Consumes original-release failing case IDs/control evidence; produces a focused development-source repair with unchanged protection semantics and a separately hashed candidate artifact.

- [ ] Write focused behavior cases for plain/meta-heavy items, direct/nested tags, shulker/bundle depth boundaries, mutations between repeated checks, denied moves, innocent-item preservation and duplicate cleanup. Retain the native failing performance reproduction before changing production code.
- [ ] Run `rtk ./gradlew :paper:1.21.11-paper:test --tests '*PaperNavigationItemProtectionTest' --console=plain --max-workers=2` and the native workload against unrepaired development and original release separately. Record differences rather than assuming equal behavior.
- [ ] Verify direct read-only tag APIs on each supported Paper target; minimize metadata reconstruction, avoid duplicate conversions and inspect container types before reconstruction. Do not introduce unvalidated caches, skip nested protection, or add product compatibility handling. Use readable Stonecutter predicates where APIs differ.
- [ ] Run focused behavior tests plus the same unprofiled/profiled synthetic and physical workloads and neighboring navigation cleanup cases. Confirm the candidate meets the unchanged inventory budget and preserve per-target build/runtime limitations. Record localization impact explicitly; normally none for this hot-path repair. No commit without a user request.

### Task 19: Repair additional demonstrated findings without speculative changes

**Files:** Update `docs/features/server-performance/validation/findings/` and only the production/test files identified by each confirmed finding; create a focused follow-up plan under `plans/` if a finding requires a substantial independent change.

**Interfaces:** Consumes finding IDs with exact reproduction, responsible source and expected behavior; produces one reviewed repair/evidence set per finding. This is an evidence-driven work queue, not permission to invent defects.

- [ ] For each confirmed finding, add its minimal failing correctness/performance case with original/repaired identities and input rates; write the exact owned file paths and verification commands in that finding's repair record before editing.
- [ ] Run it to confirm the defect exists in the source candidate being repaired. If the development branch already resolves it, demonstrate that fact and retain the original-release failure rather than creating an unnecessary patch.
- [ ] Implement the smallest source repair at the owning module; verify Minecraft/API/mixin/Stonecutter contracts as applicable. Preserve permissions, storage lifecycle and item semantics. Do not relax budgets to close findings.
- [ ] Rerun the reproduction and relevant neighboring tests; classify each finding fixed, still failing or externally blocked with evidence. Continue until all reproducible in-scope findings from the audit have verified outcomes; never claim all possible performance defects are eliminated.

### Task 20: Candidate matrix, development-only checks and healthy baselines

**Files:** Create `docs/features/server-performance/validation/candidate/` reports and `tools/server-performance/baselines/` metadata; extend `tests/test_baselines.py` and development registry cases for `PrivateWaypointStore`/collection identity.

**Interfaces:** Consumes reviewed repairs and exact candidate JAR hashes; produces approved healthy baseline candidates, full candidate outcomes and original-to-candidate comparisons.

- [ ] Write `test_failed_candidate_cannot_replace_baseline`, `test_mismatched_host_or_fixture_rejected`, `test_private_owner_isolation_required_for_development`, `test_conflicting_repetitions_inconclusive`. Assert a baseline cannot be promoted by a successful average hiding a failing repeat.
- [ ] Run focused tests before implementing baseline storage/promotion checks and development-only variants.
- [ ] Run the same small/medium matrix and family stress/soak against final candidate copies. Include Private owner quotas/isolation, eviction and concurrent flush/reload/shutdown because repaired development artifacts contain that newer code; keep it absent from original 4.0.0 applicability.
- [ ] Compare paired results on the controlled host, confirm absolute/correctness budgets and inspect variance. Record provisional non-inventory latency/allocation noise floors from repeatable healthy evidence, never from failed candidates. Present baseline promotion as a visible reviewed metadata change; do not auto-promote or commit it.

### Task 21: Later JMH benchmarks and relevant future PR selection

**Files:** Modify `common/build.gradle.kts`, `cross-server/build.gradle.kts`, `proxy-common/build.gradle.kts`; create their `src/jmh/java/.../perf/` benchmarks, `tools/server-performance/selection.py`, `tests/test_selection.py`; add quick-check CI only after audit/repair evidence exists.

**Interfaces:** Produces independent-fork component measurements and `select_scenarios(changed_paths: tuple[str, ...]) -> tuple[str, ...]`. Consumes the same registry; PR selections remain explicitly partial.

- [ ] Write `test_unknown_shared_change_selects_full_relevant_suite`, `test_item_path_always_selects_idle_container_regression`, `test_docs_only_change_does_not_claim_runtime_pass`, `test_pr_smoke_cannot_satisfy_release_gate`.
- [ ] Run selection tests before implementation; pin the benchmark dependency and demonstrate consumed benchmark outputs so dead-code elimination cannot create false speedups.
- [ ] Add JMH codec/query/navigation/storage/transport/catalog/handoff benchmarks in owning modules, with warm-up/forks/allocation and separate disk workloads. Implement conservative changed-path dependency expansion and secret-free PR jobs.
- [ ] Run selected benchmark/PR checks and a mutation that removes a required path mapping. Verify failed/missing selections fail closed and every report identifies source-built components versus published binary measurements.

### Task 22: Full release/publishing gate and operator handoff

**Files:** Create `tools/server-performance/gate.py`, `tests/test_gate.py`, `tools/server-performance/README.md`, `.github/workflows/performance.yml`; modify `.github/workflows/{build.yml,release-draft.yml}`, root `build.gradle.kts` publishing task prerequisites and feature documentation indexes.

**Interfaces:** Produces `verify_release` and `gate` CLI; exact artifact/report validation before draft creation or root upload tasks. Does not itself publish or grant upload authorization.

- [ ] Write `test_changed_final_jar_rejected`, `test_missing_case_or_metric_rejected`, `test_stale_budget_or_fixture_rejected`, `test_inconclusive_or_partial_report_rejected`, `test_untrusted_pr_report_cannot_authorize_release`, `test_dry_run_is_not_performance_evidence`.
- [ ] Run gate tests before implementation. Bind reports to required same-workflow trusted-worker jobs, source/dirty-tree fingerprints, final artifact hashes and registry/budget/fixture versions; do not treat arbitrary supplied JSON as trusted CI evidence.
- [ ] Implement artifact-build -> full matrix -> evidence validation -> draft/upload prerequisites. Preserve logs/reports on failure. Write operator commands for initial audit, zero-player cases, bot tiers, reruns, calibration, profiles and evidence interpretation; document manual-upload boundaries and actual runtime requirements.
- [ ] Test failed gate paths with tampered/stale/missing evidence, then a valid fixture report with no network publication. Run the final required candidate matrix after the final relevant source changes, inspect production JAR contents for test code, update the acceptance checklist only from actual evidence, and report remaining issues/blockers and uncommitted status. Do not push, upload or create a release.

## Self-review and execution handoff

- Spec sections 1–4 -> Tasks 1–3, 13, 17 and 22.
- Spec section 5 -> Tasks 4, 5 and 7; section 6 -> Tasks 8, 9 and 13.
- Spec section 7 -> Tasks 5, 6, 13–15; section 8 -> Tasks 1 and 7–17.
- Spec sections 9–10 -> Tasks 6, 16, 17 and 20; section 11 -> Tasks 18–22.
- Review Focus inputs have named rejection/behavior tests in the owning tasks.
- Public names, phase/status values, units, load defaults and source-set task names must
  remain identical across the specification, contracts and consumers.

Implementation has not started. On a later implementation request, review the plan and
preserve the user's chosen execution method. Inline execution is the default recommendation
because native runtime/protocol/fixture capabilities are sequentially coupled and timed
measurements must remain controlled; task-by-task subagents require the user's choice.
