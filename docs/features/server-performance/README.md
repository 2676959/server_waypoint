# Server performance

Status: M1 is in progress. M0 and Tasks 8–9 are implemented and validated on the
first Paper 1.21.11 anchor; Task 10 has native command, permission and persistence
checkpoints. Task 11 has native method/terrain/session-restoration checkpoints
and a retained stop-then-quit cleanup failure. Real 1/10/50-player smoke and protocol rejection checks pass, while
full remaining-feature/platform workloads and soaks remain unchecked. The original
M0 inventory defect still reproduces; no source repair has been made. See the
execution progress for exact hashes, retained roots and proof boundaries.

The first delivery is a comprehensive audit of the published 4.0.0 release across
different load tiers, followed by repairs and repeated measurements. Future PRs can
select relevant scenarios from the resulting suite. Release enforcement follows the
initial audit rather than replacing it.

Test bots will use [Azalea](https://github.com/azalea-rs/azalea), as selected by the user,
with real protocol connections and verified version-specific dependency pins. The design
includes a Server Waypoint protocol extension for sync/upload/edit traffic.

Deterministic disposable test worlds also exercise idle and active workloads with zero
connected players, including real hopper/container traffic. Fixtures will be created
with [Querz/NBT](https://github.com/Querz/NBT), as selected by the user, and placed/saved
by the target Minecraft runtime. Measurements must verify that the world mechanisms
continue running throughout the sample.

## Documents

- [Approved design](specs/2026-10-24-server-performance-design.md)
- [Implementation specification](specs/2026-10-24-server-performance-spec.md)
- [Implementation plan](plans/2026-10-24-server-performance.md)
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
- [Original Paper navigation/protection execution requirements](validation/2026-10-24-navigation-requirements.md)
- [M1 navigation method observations and retained cleanup failure](validation/2026-10-25-m1-navigation.md)
- [Tool commands](../../../tools/server-performance/README.md)

The plan first delivers a Paper zero-player reproduction, then covers remaining features
and platforms and freezes the original-release audit before repairs. Component benchmarks,
targeted future PR checks, and release gates follow the audit/repair cycle.

Implementation plans belong in `plans/`; execution reports and reviewed performance
budgets belong in `validation/`. Scope metadata and unchecked checklists are not passing
performance evidence.
