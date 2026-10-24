# M0 execution progress

Plan: `docs/features/server-performance/plans/2026-10-24-server-performance.md`.
Scope: M0, Tasks 1–7. Started 2026-10-24. Each task has a local commit as requested.
Baseline: clean checkout, `feature/private-waypoint`; work proceeds on
`codex/server-performance-m0`. Active projects remain Fabric 26.1.2 / Paper 26.2.

Ruling: use a dedicated branch in the existing clean checkout. All changes are scoped
to tooling, test source sets, build wiring and this feature's documentation; no existing
Private implementation is modified. The checkout was clean; concurrent work in this checkout would still need
coordination.

Interface review: Task 1 contracts feed 2/3/6/7; Task 2 identities feed 3/5/7;
Task 3 ownership/stream IDs feed 5/6/7; Task 4 manifests feed 5/7; Task 5 tick
processing and physical assertions feed 6/7. No conflicting interfaces found.

- Task 1: complete. RED: contracts module missing. GREEN: 8/8 contract tests; inventory expands 240 cases per runtime without launching a server. The release ledger records 293 source registrations/settings. Removing an entrypoint family mapping raises ValueError. Runtime command-tree expansion is reserved for Task 10.
- Task 2: contracts/staging/preflight complete; 15/15 accumulated Python tests. All 44 immutable assets mapped to explicit native requirements, plus Folia. First release asset staged and rehashed. Initial doctor exits 2 for missing native fixture/timing/probe proofs; see runtime-evidence.md. Querz binary resolution is tracked with Task 4.
- Task 3: complete. RED: lifecycle modules missing. GREEN: 21/21 accumulated tests, including independent sentinel survival, stale reply rejection, collision detection and interrupted report retention. Native Paper starts/stops in owned external roots; injected STARTING and MEASURING interruptions preserve exit code 130 in attempt.json. Startup interruption required bounded SIGTERM (process 143); ready-server interruption stopped cleanly (process 0). Logs and both roots retained under `/Volumes/ssd/sw-performance-m0-lifecycle-{starting,measuring}`.
- Task 4: complete. RED: StructureWriter absent (compileTestJava failure). GREEN: 5/5 generator tests; two independently generated outputs match exactly and pass manifest verification. Querz 6.1 binary hash recorded. Recipes cover nine fixture families at three tiers; native state validation remains Task 5. Test temporary roots are canonicalized because macOS /var is a symlink.
- Task 5: complete. RED: Python worlds module and Java ProbeCommand absent. GREEN: 27 Python contract/lifecycle/provenance/probe tests and 2 Java contract tests; fully qualified contract/Paper probe tasks pass. Native no-plugin place/save/reopen and nested item validation pass; production/probe archives have no overlapping classes. Timed overhead controls follow Task 6.
- Task 6: complete. RED: metrics module absent. GREEN: 10 focused measurement tests (37 accumulated before Task 7). Native idle and separately profiled idle each use 30s warmup + 60s measurement, retaining 1,201 and 1,200 native tick samples. JFR start/stop both succeed and the local recording exists. These single-repetition calibration samples are not a three-repeat gate result. Whole-process CPU/RSS snapshots and GC logs are separate from named-thread allocations in subsequent runs.
- Task 7: complete. RED: scenario module absent; review regressions also reproduced before fixes. GREEN: 56 Python tests; all named Java/build checks pass. Three fresh matched medium synthetic pairs reproduce FAIL, three matched medium 128-cell physical pairs PASS, and a following one-repeat idle case PASS. The aggregate report correctly remains FAIL with complete coverage within its partial selection. A separate 1,000-event JFR pair reproduces a native tick FAIL; both profiles save locally. All 12 physical fixture size/variant combinations and 14 synthetic protection/dispatch smoke variants pass. See `2026-10-24-m0-paper.md` for measured values, commands, hashes, retained paths and scope.

Runtime acceptance is tracked separately; simulated processes and contract tests
do not establish native fixture validity or performance.

## Final review and rulings

The executing-plans/requesting-code-review workflow requested one independent read-only reviewer. It reported the issues below; its session hit its usage limit before returning a formal final verdict. Findings were checked against source and addressed with regressions. No second review was dispatched.

- Retry aggregation: a subsequent result could hide an earlier failure. RED/GREEN: `test_successful_retry_cannot_erase_independent_failure`; persisted runs retain all attempts and combine their decisions.
- Partial numeric failures: later setup/profile/cleanup errors could erase measured failures. RED/GREEN: `test_later_setup_error_retains_already_measured_failure`; partial cases are saved before quiescence/profiler teardown. A ready native crash is FAIL (`test_native_measurement_crash_is_failure`).
- Fixture/config binding: frozen world hashes did not bind the manifest/config bytes actually consumed. RED/GREEN: `test_changed_fixture_or_copied_config_is_rejected`; generated manifests/templates and copied runtime/release/budget/selection files are sealed.
- Owned descendants: a descendant ignoring SIGTERM could outlive its parent. RED/GREEN: `test_owned_descendant_cannot_survive_parent_stop`; bounded group escalation now covers descendants.

Ruling: treat lexical `..` root aliases and nonfinite helper inputs as important correctness issues despite the reviewer's lower severity: they could bypass writable-root scope or produce a numeric pass through public helpers. RED/GREEN regressions reject both. Cost if wrong: stricter rejection of malformed inputs.

Ruling: navigation-session synthetic variants require an actual player and are excluded from the explicitly zero-player selection; their recorded requirements remain for M1. Dataset/settings idle variants retain BLOCKED status pending Task 10 rather than running an empty world under those names. Cost if wrong: those variants have no M0 native execution evidence.

Ruling: physical throughput uses a provisional 75% of matched active control threshold (the initial 25% regression calibration); 40 consecutive native ticks with no movement invalidate a control and fail an active candidate. These are discovery calibration inputs, not a production SLA. Cost if wrong: physical case classification needs recalibration; raw counters/windows remain available.

Final static validation also corrected declared M0 offered-demand metadata (native counters and dispatch were already correct) and 24 client-only coverage mappings. Both regressions failed before the metadata/registry fixes and now pass. The original measured JSON remains immutable; the metadata correction is explained in `2026-10-24-m0-paper.md`.

## Completed local commits

| Scope | Commit |
| --- | --- |
| Task 1 contracts/coverage | `4689459c` |
| Task 2 immutable inputs/preflight | `30a8404e` |
| Task 3 process/attempt ownership | `ab846026` |
| Task 4 Querz generator | `b3cfb23a` |
| Task 5 native Paper probe/worlds | `183a329f` |
| Task 6 metrics/JFR/reporting | `f2c689d5` |
| Task 7 paired workloads and review fixes | `74a018f` |
| Final metadata/coverage correction | `861e1f0` |

Final runtime evidence is recorded in the documentation commit containing this ledger. No changes were pushed.

Verification totals: 56 Python tests; 677 common Java tests; 115 cross-server Java tests; 2 shared probe-contract Java tests; 5 Querz generator Java tests. Probe and production Paper 1.21.11 archives build; the latter excludes probe classes. The rebuilt development artifact has a different hash from the immutable published asset. All retained native test servers are stopped.

Final CLI checks: inventory expands 10,800 requirements over 44 immutable assets and 45 anchors; Paper zero-player doctor passes. Full Paper doctor remains BLOCKED for unverified Azalea actions and wire protocol, as required for M1.
