# Server performance

> **Current required scope (November 15):** [Minimal critical Paper suite](plans/2026-11-15-minimal-critical-scope.md) supersedes the comprehensive delivery and earlier Task 11 gate. Broader unfinished tasks are optional future work; historical results remain unchanged.


Status: M1 is in progress. M0 and Tasks 8–9 are implemented and validated on the
first Paper 1.21.11 anchor; Task 10 has native command, permission and persistence
checkpoints. Task 11 has native method/terrain/session-restoration checkpoints,
a 2026-11-10 Paper small/medium timed run (3 FAIL, 25 BLOCKED, no PASS),
and retained stop-then-quit cleanup and depth-nine nested-item escape failures. Real 1/10/50-player actions and
protocol rejection have functional evidence; a retained 50-player run emits
watchdog nonresponse reports. Remaining feature/platform workloads, complete
stability checks and soaks remain unchecked. The original
M0 inventory defect remains in the original-release evidence. Focused development
performance repairs now have Paper/Fabric measurements. Original nested-protection
failures remain in historical evidence; current protection checks top-level tags
only. See the execution progress and priority repair report for exact hashes,
retained roots and proof boundaries.

Automatic container-to-container transfer protection has since been removed from
the shared Paper/Folia build at the user's request. The earlier enabled/disabled
comparison remains evidence for the pre-removal build; see the removal validation.
The mod backend's continuous allay and dropped-item protection scans have also
been removed; event-driven protection remains in the shared mod sources.
Ordinary modded menu clicks now skip full cleanup when their involved stacks have
no navigation items; relevant clicks and menu-close cleanup retain validation.
Recursive detection and cleanup have since been removed from both backends at the
user's request. Validation checks direct slot/cursor tags only; existing nested
contents are preserved. Normal bundle/shulker insertion guards remain enabled.

The required delivery is now the [minimal critical Paper 1.21.11 suite](plans/2026-10-24-server-performance.md): storage/permissions, navigation lifecycle,
terrain maps, direct item protection and command-tree delivery timing. It uses a
current development candidate, six idle/retarget runs and three command-tree
sessions. The broader original-release/platform audit is optional future work.

Test bots will use [Azalea](https://github.com/azalea-rs/azalea), as selected by the user,
with real protocol connections and verified version-specific dependency pins. The design
includes a Server Waypoint protocol extension for sync/upload/edit traffic.

Deterministic disposable test worlds also exercise idle and active workloads with zero
connected players, including real hopper/container traffic. Fixtures will be created
with [Querz/NBT](https://github.com/Querz/NBT), as selected by the user, and placed/saved
by the target Minecraft runtime. Measurements must verify that the world mechanisms
continue running throughout the sample.

## Documents

- [Step 3 complete: command-tree delivery timings and permission checks](validation/2026-11-16-command-tree-delivery.md)

- [Step 2 complete: four native functional groups PASS](validation/2026-11-16-minimal-functional.md)

- [Minimal suite implementation, candidate identities and remaining adapter gaps](validation/2026-11-15-minimal-execution.md)

- [Superseded Task 11 gate: focused correctness and three representative workloads](plans/2026-11-15-task11-simplified.md)

- [Next steps: Task 11 timed execution, then protocol and loader coverage](plans/2026-11-09-server-performance-next-steps.md)
- [Task 11 tool lock, behavior contracts, and timed Paper run](validation/2026-11-09-task11-continuation.md)
- [Task 11 local profiles and post-disconnect observations](validation/2026-11-12-task11-diagnostics.md)
- [Published 4.0.1 Task 10 remasurement with patched decoder](validation/2026-11-01-4.0.1-task10-decoder.md)
- [Published 4.0.1 Task 10 revalidation](validation/2026-11-01-4.0.1-task10.md)
- [Task 10 storage/command validation and proof boundaries](validation/2026-10-31-task10-storage-commands.md)
- [Per-player Paper reconciliation merging and verification](validation/2026-10-27-player-reconciliation.md)
- [System build reference captured October 27, 2026](validation/2026-10-27-system-build.md)
- [Issue #35 command-tree reduction and native 26.3 measurements](validation/2026-10-27-command-tree.md)
- [Approved design](specs/2026-10-24-server-performance-design.md)
- [Implementation specification](specs/2026-10-24-server-performance-spec.md)
- [Current minimal implementation plan](plans/2026-10-24-server-performance.md)
- [Historical comprehensive plan](plans/2026-10-24-server-performance-comprehensive-historical.md)
- [Acceptance checklist — not execution evidence](validation/2026-10-24-acceptance-checklist.md)
- [Verified current-release scope](validation/2026-10-24-release-scope.md)
- [Published artifact inventory and hashes](validation/2026-10-24-release-assets.json)
- [Execution progress](validation/progress.md)
- [Coverage requirements ledger](validation/coverage-ledger.md)
- [Runtime evidence](validation/runtime-evidence.md)
- [M0 Paper results and retained evidence](validation/2026-10-24-m0-paper.md)
- [M1 Paper storage concurrency results](validation/2026-10-24-m1-storage.md)
- [Additional Paper/Fabric component measurements and repair decisions](validation/2026-10-25-additional-components.md)
- [Folia region measurements and Paper comparison](validation/2026-10-25-folia-regions.md)
- [Priority repair plan](plans/2026-10-26-priority-repairs.md)
- [Priority repairs and Paper/Fabric before/after measurements](validation/2026-10-26-priority-repairs.md)
- [Paper transfer protection versus no-check baseline](validation/2026-10-26-protection-cost.md)
- [Hopper transfer-check removal and native validation](validation/2026-10-26-hopper-transfer-removal.md)
- [Modded continuous protection-scan removal and native validation](validation/2026-10-26-modded-tick-scan-removal.md)
- [Ordinary modded menu-click fast path and measurements](validation/2026-10-26-menu-click-fast-path.md)
- [Top-level protection policy and Paper/Fabric validation](validation/2026-10-27-top-level-protection.md)
- [Non-copying Paper/modded marker reads and measurements](validation/2026-10-27-marker-read.md)
- [Removal-event reconciliation removal and native verification](validation/2026-10-27-removal-reconciliation.md)
- [Original Paper navigation/protection execution requirements](validation/2026-10-24-navigation-requirements.md)
- [M1 navigation method observations and retained cleanup failure](validation/2026-10-25-m1-navigation.md)
- [Tool commands](../../../tools/server-performance/README.md)

The original harness and audit results remain reusable evidence with their original
identities. Follow the revised master plan for the remaining required work; no
full-matrix audit, soak, JMH suite or publishing gate is required for this delivery.

Implementation plans belong in `plans/`; execution reports and reviewed performance
budgets belong in `validation/`. Scope metadata and unchecked checklists are not passing
performance evidence.
