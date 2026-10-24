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
- Task 2: contracts/staging/preflight complete; 15/15 accumulated Python tests. All 44 immutable assets mapped to explicit native requirements, plus Folia. First release asset staged and rehashed. Initial doctor exits 2 for missing native fixture/timing/probe proofs; see runtime-evidence.md. Querz binary resolution is tracked with Task 4.
- Task 3: complete. RED: lifecycle modules missing. GREEN: 21/21 accumulated tests, including independent sentinel survival, stale reply rejection, collision detection and interrupted report retention. Native Paper starts/stops in owned external roots; injected STARTING and MEASURING interruptions preserve exit code 130 in attempt.json. Startup interruption required bounded SIGTERM (process 143); ready-server interruption stopped cleanly (process 0). Logs and both roots retained under `/Volumes/ssd/sw-performance-m0-lifecycle-{starting,measuring}`.
- Task 4: complete. RED: StructureWriter absent (compileTestJava failure). GREEN: 5/5 generator tests; two independently generated outputs match exactly and pass manifest verification. Querz 6.1 binary hash recorded. Recipes cover nine fixture families at three tiers; native state validation remains Task 5. Test temporary roots are canonicalized because macOS /var is a symlink.
- Tasks 5–7: pending.

Runtime acceptance is tracked separately; simulated processes and contract tests
do not establish native fixture validity or performance.
