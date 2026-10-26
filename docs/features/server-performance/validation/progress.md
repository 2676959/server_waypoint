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


### Task 10 acknowledged command measurement checkpoint

`ded974f` committed the preceding concurrency/traffic work. Command measurements now require two distinct probe messages: native marker registration, followed by the actual command callback acknowledgement. The client starts the command clock after marker registration and stops it at callback acknowledgement. Marker registration overhead is retained separately. Actor, action ID, command, stage and native callback origin must all match; marker-only or client-issued outcomes cannot complete an action-latency sample.

Native `/Volumes/ssd/sw-performance-m1-ack-medium-4/` completes 275 scheduled commands with all matching callbacks, no pending/failed actions, exact revision deltas and full disk validation. The short run's command RTT p50/p95/max are 38.966/40.416/57.974ms; these are smoke diagnostics, not a performance gate. Driver maximum enqueue-to-completion lag is 0.174s. Earlier `ack-medium`, `ack-medium-2` and `ack-medium-3` roots preserve queue/acknowledgement failures while the marker and command could race. The marker-registration acknowledgement resolves that test-harness ordering issue; no release source was changed.

The prepared CLI now routes the explicitly supported `storage:concurrent` case through a native bot workload, declares 1/10/50 tier actors, drains bounded native callbacks during phases, retains raw ticks/action RTT/command-thread allocation and separate server/driver process samples, and validates exact saved/reopened contents. Other command/storage variants remain explicit blockers until their own behavior adapters exist. Full native timing verification is in progress under `/Volumes/ssd/sw-performance-m1-storage-timed/`; do not infer passing repetitions from runner implementation.


### Task 10 full storage-concurrency repetitions

`97d2288` commits acknowledged command measurement and prepared-run integration. The selected small/medium `storage:concurrent` report is PASS under `/Volumes/ssd/sw-performance-m1-storage-timed/`: three fresh process repetitions per tier, 30s warm-up plus at least 60s measurement, 660 / 3,300 acknowledged measurement actions each, and exact saved/reopened contents. See `2026-10-24-m1-storage.md` for per-repeat percentiles, hashes and proof boundaries. All twelve full windows independently pass exact native online counts, consecutive tick identifiers and available command-thread allocation checks. Those checks are now explicit runner regressions/guards, with structured correctness assertions and per-repetition bot-byte checks. The original raw inputs/results remain immutable and keep their original suite identity.

Verification: 89 Python tests pass, including new RED/GREEN rejection of changed actor counts, tick gaps and unavailable command allocation. The separate JFR diagnostic is preparing under `/Volumes/ssd/sw-performance-m1-storage-profile/`. No complete Task 10 or M1 claim: exhaustive command/suggestion/denial coverage, remaining variants and Tasks 11–16 are pending.


The separate 2026-10-25 medium JFR diagnostic also passes its selected case, all structured assertions and exact saved/reopened state. Recording: 5,229,630 bytes / 96s, with 22,810 allocation samples and 24 GC events; raw root `/Volumes/ssd/sw-performance-m1-storage-profile/`. Sample weights are estimated whole-JVM allocation, not exact plugin bytes or leak evidence. The native inputs match the unprofiled runs; validation-code refinements have a separate sealed input identity. Both completed roots have no owned live processes.


### Task 10 console leaves and local suggestion providers (2026-10-25)

Native `/Volumes/ssd/sw-performance-m1-read-suggestions-full/` executes all 5,295 read-command leaves (root/help/details/list) from the installed 4.0.0 command tree. Every requested input matches its actual native callback path, each has delivered console feedback, no exception is observed, and the complete saved dataset remains exact. The matching callback and output count is 5,295. This is console execution/feedback evidence, not complete player UI semantics or a timed performance gate.

All 51 local custom suggestion paths also invoke their expected native Brigadier provider, with matching input/provider identity, nonempty results and no dropped callbacks. Eleven remote custom suggestion paths remain Task 15 topology requirements. The complete tree still contains 10,663 executable paths: the 5,276 remote paths and 92 remaining local non-read paths have separate pending behavior work.

RED: native feedback proof is absent in `read-leaves-red`; the first observation attempt (`read-leaves-green`) retains a failure because console messages arrive after the callback. Source inspection confirms `PaperMessageSender` schedules console delivery through `PaperScheduler.executeGlobal`. The repaired probe brackets actual delivery on that same scheduler, then uses an explicit global-scheduler barrier and the native file log. It neither copies feedback text nor replaces the real sender. A multiline-log regression preserves full delivered text. Native `suggestions-red` retains missing provider identities; wrappers now observe actual native provider invocation/completion while preserving the dispatcher argument graph.

Verification: 94 Python tests and the Paper 1.21.11 probe build pass; production/probe archives have no overlapping classes. The runtime lock records the new probe bytes and evidence root. All helper servers stopped. No production code, localization or active Stonecutter target changed. Task 10 remains in progress; semantic/player/permission branches, other local command families, navigation/upload/remote dependencies and the rest of M1 remain open.


### Task 10 storage mutation leaves and reload outcomes (2026-10-25)

`e78ddfd` commits the preceding read/provider checkpoint. Native `/Volumes/ssd/sw-performance-m1-mutation-leaves-final/` now executes all 43 console storage-related leaves (add/edit/remove/restore/reload). Each leaf has its own seeded collection and an independent expected operation ledger. Actual property snapshots read the installed release through its public APIs, including one synchronized waypoint copy; the probe bundles no current production classes. Exact fields, names, revisions and unrelated data match after every selected operation, then full saved JSON and native reopened contents match. The two quick-add leaves declare generated RGB colors: only that field is learned from the legitimate random outcome after validating its domain; other properties remain independent expectations.

The run also verifies single-use restore replay and nonempty-list removal rejection with all contents/revisions preserved. Config/language reload is positively observed: a unique temporary external language override is delivered by the asynchronous reload response, and changing the default page size produces exactly one expected waypoint row. Temporary language/config files stay inside the owned test server. No production translations changed.

RED: `mutation-leaves-red` reports the absent native property-snapshot operation. The first green attempt retains a missing-feedback result for reload because its actual work runs asynchronously after command return; it passed all 43 state oracles but stopped before that response. The repaired runner waits for the actual unique response in an exclusive command window, with regressions rejecting a mismatched token or intervening command. `mutation-leaves-green-2` and the final run pass exact save/reopen and real reload outcomes.

Verification: 100 Python tests and Paper probe build pass; production/probe class isolation is verified. All helper servers stopped. These are console behavior results, with separate pending player/permission/semantic cases. Remaining executable feature leaves are navigation/teleport (Task 11), upload/download (Task 12), and remote/keygen (Task 15). Task 10 stays open while those actual feature adapters are implemented; inventory or console-only rejection does not substitute for successful native feature behavior. The cost of this dependency ruling is that Task 10 cannot be checked complete until those requirements return with evidence. M1 remains in progress; no source repair or push was made.

### Task 11 native methods, targets and cleanup checkpoint (2026-10-25)

`8803c649` commits the preceding storage mutation/reload checkpoint. Task 11 now has its five planned contract tests plus a stale bossbar/actionbar regression, read-only snapshots of the installed release and bounded normal client packet observations. Native one- and ten-player runs execute all 31 nonempty method combinations. Compass lodestones, actual map renderer cursors/captions, bossbars, actionbars and network-only text displays match their independent expected targets. Movement, cross-region retargeting, display-name/color/position/identifier edits and actual local waypoint teleport arrival also pass. See `2026-10-25-m1-navigation.md` for retained roots and proof boundaries.

The ten-player isolation run additionally retargets one owner while the other nine remain on their previous target, independently verifies all ten item/display targets, and preserves each actor's seeded 64 stone and three diamonds by full ordinary-item identity/count. All requested method combinations pass for every actor.

**Original-release cleanup FAIL retained:** after stopping navigation, disconnecting and waiting at least ten native ticks, the one-/ten-player target runs retain 1/10 navigation tick counters. Sessions, owner tasks, bossbars, text-display state, tagged online items, target indices and renderer targets clear. Owner map caches legitimately retain 1/10 entries. Source inspection confirms `removePlayer` returns early without clearing tick counters when a prior disable already removed the session. This is a retained-state observation, not a measured heap-growth slope. No production repair was made.

Ruling: assert that actionbar updates stop across at least five native ticks after disable, while allowing its last text to fade. The installed handler deliberately avoids an empty message that could erase another plugin's bar. Requiring an immediate empty packet would misclassify the documented behavior. Native and client observations remain separate.

Verification: 106 Python tests, 17 Rust tests and the Paper 1.21.11 probe build pass. The native helpers intentionally exit 1 for the fuller cleanup assertion; earlier incomplete cleanup declarations remain visible and cannot override that result. Native test servers are stopped. Production/probe archives remain isolated; no production localization or active Stonecutter target changed. Task 11's first checklist step is checked; full protections, death/respawn/world/rejoin/restart/deletion, persistent terrain and timed native cases remain open. Task 10 and M1 are still in progress.

### Task 11 terrain and persisted all-method restart checkpoint (2026-10-25)

`01a34625` commits the preceding navigation-method checkpoint. Native `/Volumes/ssd/sw-performance-m1-terrain-small/` passes one-owner map terrain switching and restart; `/Volumes/ssd/sw-performance-m1-terrain-medium-all/` passes ten-owner cold/warm terrain and restores all five methods after restart. Actual native map pixels are copied on the owner thread, compared byte-for-byte to independent outgoing region references and actual saved files, and correlated with complete native client patches/checksums. The cold destination replaces explored pixels with all zeroes. The ten-owner run hashes 30 saved terrain files and preserves exact owner map IDs, UUIDs, targets, methods, compass destinations, map cursor/captions and live native/client displays on join, before issuing a new navigation command. The real `all` leaf is positively executed.

Active-session quits clear tick counters and other active resources to zero after ten further native ticks and retain legitimate owner caches. This isolates the preceding retained-state failure to the stop-then-quit path; it does not override it. No original release repairs were made. Native RED retains absent pixel observation in `sw-performance-m1-terrain-red`.

Verification: 108 Python tests, unchanged 17 Rust tests and Paper probe build pass; production/probe class isolation is checked. Helper processes stopped. The full Task 11 checklist remains open: physical and nested protections, death/respawn/world/deletion, cross-dimension terrain/eviction/shared-map viewers, full CLI integration and timed cases are pending. Task 10 and M1 remain in progress.

### Task 11 actual death/respawn/world checkpoint (2026-10-25)

`4e70c1cc` commits terrain and all-method restart work. Native one-/ten-player lifecycle runs pass at `/Volumes/ssd/sw-performance-m1-lifecycle-small-final/` and `/Volumes/ssd/sw-performance-m1-lifecycle-medium-2/`. Actual deaths and Azalea's normal automatic respawn preserve all method targets, exact item counts/map IDs and live native/client effects. Ordinary death drops retain their independent full identities/counts; no tagged navigation item reaches drops/world. Ten players produce ten death, ten respawn and twenty actual Nether/Overworld transition receipts, with zero dropped observations. The destination is a native loaded/force-loaded and verified enclosure, and actors use distinct safe positions. See the navigation validation document for all retained earlier harness/fixture failures.

Source verification corrected the observer's same-world respawn assumption: vanilla retains ClientLevel and the reused player entity ID permits the native display ID to remain. The Rust observer now clears displays only when the dimension actually changes, with a RED/GREEN regression. The ten-player console landing failure was also retained: unloaded chunks caused console fill to do no work and players to fall; a native read-back-verified fixture resolves it without modifying the release.

Verification: 109 Python tests, 18 Rust tests and the Paper probe build pass; probe/production class isolation is checked. Test processes stopped, no production/localization changes or push. Task 11's source-discovery/RED-test step is checked; full protections, deletion, cross-dimension terrain/eviction/shared viewers, timed cases and full CLI remain open. Tasks 10–11 and M1 remain in progress.

### Task 11 selected physical protection checkpoint (2026-10-25)

`18c51d65` commits lifecycle work. Native one-/ten-owner physical cases pass at `/Volumes/ssd/sw-performance-m1-protection-small-final/` and `/Volumes/ssd/sw-performance-m1-protection-medium/`. Nine normal-protocol cases cover protected compass/map drop denial, allowed offhand swap/back, allowed own-storage movement, denied armor/empty-result-slot attempts, ordinary storage movement and an actual ordinary drop. Native selected-slot confirmation precedes actions; per-case actor/slot/cancellation receipts, exact owner methods/targets/map IDs/items, clean cursor/top inventory, valid locations and atomic full ordinary identity/count conservation all pass. Ten actors produce 30 drop, 20 swap, 80 click, 40 close and 170 slot-change receipts with no drops; all 90 owner checks pass. Ordinary diamond-drop UUIDs match both uncancelled native spawn receipts and ten actual world entities (30 diamonds total).

RED roots retain absent physical actions and native receipts. The Rust input parser now requires explicit valid menu slots 0–45, rejects malformed inputs and validates drop modes. The earlier native bot smoke caller was updated accordingly. Its peaceful-world regression completes 1/10/50 identities/actions/disconnects, but the fifty-player phase emits three watchdog nonresponse reports during native command-tree serialization (134 ERROR log entries including stack lines). Logger stability is FAIL; functional action completion cannot override it. The number of reports does not establish distinct stalls or attribute a production incident. The preceding default-difficulty 50-player attempt retains a slime-caused death that invalidated that fixture's inventory check. No production repair was made. Vanilla give-animation spawns are explicitly distinguished from actual transferred world items by UUID correlation.

Verification: 115 Python tests, 20 Rust tests, Paper probe build, production/probe isolation and clean physical-protection native logs pass. A new streaming native-log guard has RED/GREEN tests for watchdog/error rejection independent of completed actions, ignores ERROR words inside INFO payloads and blocks missing logs. Bot smoke writes its separate health result and fails stability on errors; physical helper checks it too. Propagation to every prepared case remains Task 16 work. No production/localization change or push. The original stop-then-quit counter FAIL remains retained. Full protection variants, deletion/terrain edge cases, CLI integration and timing are pending; Tasks 10–11 and M1 remain in progress.

The isolated `/Volumes/ssd/sw-performance-m1-action-health-guard/` rerun completes all 1/10/50 action checks with native logger health PASS. The earlier watchdog-emitting run shared the host with another functional helper; its three nonresponse reports remain a FAIL. Neither run is a paired timing experiment or establishes a reproducible production cause. The native-log checker independently evaluates the retained raw log as FAIL without modifying that root.

`/Volumes/ssd/sw-performance-m1-protection-health/` also completes the nine one-owner physical cases with its new native logger-health field explicitly PASS.
