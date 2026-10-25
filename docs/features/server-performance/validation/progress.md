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

## M1 execution (2026-10-24)

Scope: Tasks 8–16, continuing in the clean `codex/server-performance-m0` checkout.
Task-sized local commits are authorized; no pushes or production changes.

- Task 8: complete. Python RED: absent bot driver; subsequent regressions reject absent native observations, mixed stderr, zero-demand capacity and stale identities. Native RED: unsupported player snapshot. GREEN: 8 bot-driver tests (64 accumulated), 2 Rust action-ledger tests, optimized locked bot build and Paper probe build. Native 1/10/50 players complete 3/30/150 actions with server-observed UUIDs, movement, inventory transfer/conservation, commands and zero online after stop. Evidence: `/Volumes/ssd/sw-performance-m1-bots-2/`. The first smoke retained a fixture failure caused by persisted offline inventory between tiers; fresh setup now clears before seeding. These are action smoke checks, not 30/60-second performance repetitions or wire coverage.

Ruling: pin DER/PKCS prereleases to the published Azalea revision's lockfile after current resolution produced RSA API compilation errors. Keep the dated nightly override in the bot project and preserve the global Rust default. Cost if wrong: refreshing these pins requires a new locked build and native smoke.

Localization impact: none; no production messages or translations changed.

- Task 9: complete on the first Paper anchor. Final reviewed binary passes all three native modes and the overload/checksum regressions under `/Volumes/ssd/sw-performance-m1-task9-final/`. Java checks 9 Rust-generated valid/invalid/semantic/multi-frame groups; 2 exporter tests and 8 Rust tests pass. Exporter is isolated from current production outputs and binds loaded release class bytes. Java validates Rust-generated valid, invalid and multi-frame requests; Rust validates Java frames, compression/checksums, upload fields, edit revision/patch fields and the initial empty catalog replacement. Seeded native modes match list `perf` revision 2 / waypoint `origin`; wrong-protocol clients send a real snapshot request and receive no download. Later tasks expand nonempty catalogs and valid uploads.
- Task 10: in progress. Named assertion/schedule tests failed before implementation; storage/command evidence validators and bounded 10/50/100 read plus 1/5/10 mutation schedules now pass. Native dispatch, complete command-tree evidence and small/medium timed save/restart cases remain unchecked.

Independent Task 8/9 review found seven important issues; fixes add sticky error delivery, failed-transfer release/bounds/expiry, retained reply queues, incompatible request traffic, enqueue-based lag, nonblocking bounded writes/cleanup and semantic multi-frame codec checks. Native overload regression first failed against the old driver; the repaired run reports 6.03s queue lag and correctly rejects a checksum-corrupt payload after a prior valid download. Final evidence will record the exact final binary hashes.

Deferred minor (before Task 16 soak acceptance): stream or bound action-ID/event/packet history, which currently grows over the run. Small smoke checks do not establish sustained driver capacity.

Task 8 commit: `8c4672e9`. Task 9 commit is the local commit containing its checked plan steps and runtime pins. No production source, GUI API, translation or active Stonecutter target changed.

Task 9 review fix evidence: `test_error_after_matching_event_is_not_lost`, `test_unmatched_replies_remain_available`, `test_stalled_stdin_has_deadline_and_owned_cleanup`, `failed_complete_transfer_releases_bytes_and_cannot_grow`, `conflicting_metadata_releases_the_transfer`; semantic/multi-frame vectors verified both directions by the immutable release codecs. The native overload test failed before enqueue timing was fixed; the native corrupt-checksum case now emits a sticky error and disconnects. Every owned process in these roots stopped.

### Task 10 foundation checkpoint

Seven focused storage/command tests pass, including deterministic 1,000-waypoint/10-list/3-dimension fixtures and the separate 90%-in-one-list fixture. The native small dataset loads with every declared name count and revision 7. The registered tree contains 19,762 nodes, 10,663 executable paths and 62 custom suggestion providers. This inventory is a coverage requirement, not execution coverage.

Native callback smoke `/Volumes/ssd/sw-performance-m1-command-callback-3/` observes actual `wp help` and `wp list <dimension> <list identifier> limit <page limit>` callbacks, with server-monotonic duration and server-thread allocations. Earlier roots preserve absent-callback failures.

Ruling: observe the native Minecraft dispatcher and acknowledge console batches on the next owner tick. Paper's lifecycle dispatcher maps nodes, and commands nested inside `swperf` queue feature work until that command returns; timing `Bukkit.dispatchCommand` alone measured enqueue/parse cost. Actual callback timing is recorded separately from dispatch submission. Cost if wrong: native callback coverage must remain blocked rather than accepting submission timing. Pinned Minecraft source and Paper bytecode were inspected before this change.

Task 10 remains in progress: complete leaf/suggestion/denial behavior evidence, timed small/medium workloads and save/restart assertions are not checked. This foundation checkpoint does not mark M1 complete.

### Task 10 behavior/persistence checkpoint

Native small and medium fixture behavior passes under `/Volumes/ssd/sw-performance-m1-persistence-small-3/` and `/Volumes/ssd/sw-performance-m1-persistence-medium-3/`. Each uses one actual compatible Azalea actor (not a claim of medium ten-player load), a hash-verified published JAR, and 1,000 or 10,000 initial waypoints over three dimensions. Permission level 2 is explicitly configured; denial preserves all revisions, a wire edit declaring revision 6 receives STALE_REVISION at actual revision 7, and rename/remove/restore leaves the new list at revision 5. Replaying the restore token changes nothing.

Every saved JSON field/value is compared against the independent seeded dataset plus the committed operations, with only map iteration order canonicalized. Both servers stop/reopen and retain all expected names/revisions. Earlier roots retain fixture failures: an unquoted `#` colour is invalid Brigadier string syntax, and raw map iteration order is not a persistence identity. Neither is classified as a production defect. No source repair was made.

Task 10 timed 30/60-second, three-process repetitions and exhaustive leaf/suggestion coverage remain pending. The 10,663-path tree includes 5,276 remote paths that require Task 15's topology; those requirements remain visible rather than being marked not applicable. Native callback duration/allocation is distinct from dispatch submission duration and process-wide allocation.

### Task 10 concurrency and scheduled-traffic checkpoint

The driver now uses bounded FIFO queues per actor with concurrent independent actors. RED: ten 500ms actions took 5.04s in the serial driver. GREEN: they complete in 0.522s, and retained native snapshots independently show movement for all ten players (`/Volumes/ssd/sw-performance-m1-capacity/`). Failed actions resolve their ledger slots without permitting ID reuse. Barriers wait for all prior work; failed counts cannot hide behind completed totals. Protocol/error/overload checks and 1/10/50-player smoke pass again under `/Volumes/ssd/sw-performance-m1-parallel-wire/` and `/Volumes/ssd/sw-performance-m1-parallel-swarm/`.

Five-second scheduled command smokes exercise 10 reads + 1 mutation/s with one actor and 50 reads + 5 mutations/s with ten actors. All 55 / 275 actual native callbacks are matched by actor and command, and exact revision deltas and full saved JSON match the independent fixture. Roots: `/Volumes/ssd/sw-performance-m1-traffic-small/` and `/Volumes/ssd/sw-performance-m1-traffic-medium-3/`. Operator permission is explicitly declared for these throughput actors to exempt vanilla chat-spam admission; separate non-operator denial behavior is retained. This does not prove non-operator admission at those rates.

The medium run observed 195 callbacks at its first drain and needed 3.34s of bounded draining for all 275. That backlog is retained; it is not averaged away or relabeled a fixture failure. The first medium attempt retains its incomplete observations. A stale-edit response check now waits for an actual correlated outcome by deadline rather than assuming a fixed sleep is enough.

**Performance acceptance remains INCONCLUSIVE.** These are short functional/rate smokes, not the required 30s warm-up, 60s measurement and three fresh repetitions. Client-issued timing and native callback processing timing are not acknowledged client roundtrip latency. A new regression and budget guard prevent those timings (or missing action samples/counts) from producing an action-latency pass. No budget was relaxed and no production source was repaired.

Current verification: 83 Python tests; 9 Rust tests; the common/cross-server/probe-contract/exporter Gradle checks and Paper 1.21.11 probe archive pass. Native helper test servers are stopped. Task 10 still needs full runner integration, acknowledged client latency, required timed repetitions and exhaustive leaf/suggestion coverage; Tasks 11–16 remain pending. M1 is not complete.

Local M1 checkpoints so far: `8c4672e9` (Task 8), `b424945d` (Task 9), `62f69665` (Task 10 foundations), `0cd7a33` (Task 10 behavior/persistence). The following local commit contains concurrency/rate-smoke work and this checkpoint.
