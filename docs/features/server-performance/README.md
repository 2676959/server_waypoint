# Server performance

Status: M0 (Tasks 1–7) implemented and validated on 2026-10-24. The published
Paper 1.21.11 inventory defect reproduces in all three measured pairs; the medium
physical hopper case passes all three pairs. The selected report is complete and
failing. The comprehensive audit, remaining platforms/features, repairs and release
gates remain later milestones. See the execution progress for evidence and limits.

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
- [Tool commands](../../../tools/server-performance/README.md)

The plan first delivers a Paper zero-player reproduction, then covers remaining features
and platforms and freezes the original-release audit before repairs. Component benchmarks,
targeted future PR checks, and release gates follow the audit/repair cycle.

Implementation plans belong in `plans/`; execution reports and reviewed performance
budgets belong in `validation/`. Scope metadata and unchecked checklists are not passing
performance evidence.
