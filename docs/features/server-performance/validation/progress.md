# M0 execution progress

Plan: `docs/features/server-performance/plans/2026-10-24-server-performance.md`.
Scope: M0, Tasks 1–7. Started 2026-10-24. Each task has a local commit as requested.
Baseline: clean checkout, `feature/private-waypoint`; work proceeds on
`codex/server-performance-m0`. Active projects remain Fabric 26.1.2 / Paper 26.2.

Ruling: use a dedicated branch in the existing clean checkout. All changes are scoped
to tooling, test source sets, build wiring and this feature's documentation; no existing
Private implementation is modified. This avoids a worktree consent prompt; concurrent
work in this checkout would still need coordination.

Interface review: Task 1 contracts feed 2/3/6/7; Task 2 identities feed 3/5/7;
Task 3 ownership/stream IDs feed 5/6/7; Task 4 manifests feed 5/7; Task 5 tick
processing and physical assertions feed 6/7. No conflicting interfaces found.

- Task 1: complete. RED: contracts module missing. GREEN: 8/8 contract tests; inventory expands 240 cases per runtime without launching a server. The release ledger records 293 source registrations/settings. Removing an entrypoint family mapping raises ValueError. Runtime command-tree expansion is reserved for Task 10.
- Tasks 2–7: pending.

Runtime acceptance is tracked separately; simulated processes and contract tests
do not establish native fixture validity or performance.
