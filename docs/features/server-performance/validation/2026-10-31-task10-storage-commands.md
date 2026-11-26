# Task 10 — storage and command audit

Task 10 workload implementation and selected native execution are complete.
Six Paper 1.21.11 cases each completed three fresh-process 30/60-second
warm-up/measurement repetitions, with exact save/reopen checks: **18 complete
windows**. All four storage cases PASS; both command tiers FAIL their retained
local-teleport feedback assertions. This is an original-release audit result,
not a claim that the published release passes its correctness requirements.

Executed October 31–November 1, 2026 against the immutable published Paper 4.0.0
JAR. Medium commands: `/Users/mini/.codex/tmp/sw-task10-20261101-command-final/`.
Storage and small commands: `/Users/mini/.codex/tmp/sw-task10-20261101-final/`.
The [machine-readable summary](2026-11-01-task10-evidence.json) records exact
per-repetition metrics, identities, assertions and attempt paths.

This is selected Task 10 evidence. It does not close M1, the complete release
matrix, candidate validation or baseline promotion. Native correctness failures
remain failures even when all scheduled traffic and persistence checks complete.

## Final selected results

Ranges below are the minimum/maximum of each repetition's nearest-rank
percentile, not pooled samples or confidence intervals. Allocation is total Java
allocation during each measured window, not resident memory or per-command cost.
The separate setup/tool identities recorded below prevent paired optimization conclusions.

| Case | Result | Measured + exact reopen | Native tick p95 ms | Native tick p99 ms | Bot ACK RTT p95 ms | All-thread allocation GiB |
| --- | --- | --- | --- | --- | --- | --- |
| Commands, medium | FAIL | 3 / 3 | 1.524–2.233 | 4.996–5.304 | 38.635–40.013 | 249.456–250.011 |
| Commands, small | FAIL | 3 / 3 | 3.253–3.480 | 3.878–6.789 | 41.020–41.191 | 51.148–52.231 |
| Normal storage, medium | PASS | 3 / 3 | 3.199–3.579 | 3.734–5.809 | 39.906–41.828 | 7.292–8.426 |
| Normal storage, small | PASS | 3 / 3 | 1.785–3.229 | 2.345–5.747 | 39.028–41.067 | 0.993–1.046 |
| Skewed storage, medium | PASS | 3 / 3 | 2.857–3.515 | 4.229–7.744 | 40.837–41.081 | 20.168–21.457 |
| Skewed storage, small | PASS | 3 / 3 | 2.563–3.903 | 3.145–4.682 | 40.910–41.053 | 2.108–2.187 |

All medium native tick windows meet the absolute p95 <40 ms / p99 <50 ms budgets.
Command correctness still fails. Each small command window records 20 edit,
20 teleport and 20 reload callbacks plus 30 direct-console and 30 execute-as
callbacks. Medium records 100 of each mutation family and 150 of each console
family. The JSON summary retains their individual native duration/allocation
distributions and all separate suggestion completions. Every selected attempt
has exact saved/reopened properties and zero retained command resources after
disconnect.

## Coverage and proof boundaries

The installed release tree has 10,663 executable paths: 5,295 read paths,
43 local mutation/config paths, two local teleport paths, 25 navigation paths,
21 upload/download paths and 5,277 remote/key-generation paths. All 62 custom
suggestion paths have separate native provider receipts. Timing groups never
substitute for per-leaf behavior evidence.

Every final exhaustive small/medium repetition has native callbacks for all 10,663
paths and delivered feedback for 10,662 distinct paths. The missing feedback
leaf is `wp tp <dimension> <list identifier> <waypoint identifier>`. Direct
player and console execute-as routes both fail their feedback assertion, while
native final world/coordinates match the fixture. These are observations of the
instrumented published release, not proof about the current development branch.

Navigation, upload/download and cross-server leaves exercise their explicit
console/client-required or unpaired-server outcomes here. Successful navigation,
upload and remote effects remain required in Tasks 11, 12 and 15. Ruling: close
Task 10's command-dispatch work independently of those feature effects. This
refines the earlier dependency checkpoint. Cost if wrong: successful feature
failures remain undiscovered until their owning workloads execute; the full
release audit must remain incomplete.

Each local mutation leaf has its own initial properties and exact revision/field
oracle, including rename, removal and restoration. Successful restore consumes
its actual token; replay must deliver explicit invalid/expired-token feedback
and leave state unchanged. A compatible Azalea actor submits a real stale edit
with revision 6 against revision 7. Full native property snapshots, public
storage reload, saved JSON and a fresh JVM reopening check committed contents.

The permission matrix covers four configurations for each of eleven native
gates: non-operator level-two denial, operator fallback, explicit non-operator
grant and explicit operator denial (44 configurations). Denials use real player
packets, native preprocess receipts, delivered feedback and unchanged full
storage snapshots. Explicit granted edit also executes and commits exactly once.
Restore uses add permission; upload deletion grants its parent upload gate to
isolate the delete gate. The independent four-point semantic fixture checks ten
sort/search/paging/layout/distance cases and concrete details/help feedback.

## Timed workload

The manifest declares 10/50/100 reads, 1/5/10 mutations and 10/50/100 native
suggestions per second for small/medium/stress. Command tiers use 1/10/50 real
actors. One tenth of command reads use the native console, alternating direct
and execute-as sources. Mutations cycle through edit/save, local teleport and
config/language reload. The measured queries scan the tier-sized catalog;
the four-point fixture serves independent semantic oracles.

Small command measurements offer 600 bot commands, 60 console commands and
600 native suggestions; medium offers 3,000, 300 and 3,000 respectively.
Console reads are included in the declared read rate. Each command callback
family has separate native duration/allocation samples. Asynchronous edit,
teleport and reload action-log completions must match exactly before the phase
finishes. Bot acknowledgement RTT excludes asynchronous completion delay;
console callbacks and suggestion providers use the server monotonic clock.

Normal storage measures reads and color edits. Skewed storage adds suggestions
and places 90% of the 1,000/10,000/50,000-point fixture in one list. Each complete
repetition requires 30 seconds warm-up, 60 seconds measurement, exact actor
counts, contiguous native tick IDs, fulfilled demand and exact save/reopen.
Whole-JVM Java allocation deltas include all threads, runtime and probe work;
they are separate from command-thread allocation and do not measure native
malloc or Rust bot allocations. Failed/incomplete windows retain diagnostic
allocation snapshots but do not become valid timing repetitions.

## Artifact and setup identities

Production revision: `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`.

| Artifact | SHA-256 |
| --- | --- |
| Published Server Waypoint | `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57` |
| Paper 1.21.11 | `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3` |
| Original final-selection probe | `6f6fb4723aad11234eed281cced9065591ae0389c2dfd2eaa1aa1af176e61d35` |
| Original final-selection bot | `e3c1ec04c5a9bd9b1a325c3d7643c255009f05985e4596ded0498f68e3326ba6` |
| Medium command recovery probe | `588192cf705de2e10f722385759e4dd7593df016d608f6a5492573d11d7b337d` |
| Medium command recovery bot | `7a197abfac1962b9666a6dd7ab3d676291d0c78168817123cfa3ff663e300bff` |

Both selected roots retain 134 archived executable source inputs, fixture
recipes, environment/Java identity, runtime locks, raw telemetry and per-attempt
records. Exact bot/probe binaries are retained under `runtime-binaries/`.
The server uses pinned JDK 21.0.11 with `-Xms1G -Xmx2G -XX:+UseG1GC` on the
recorded Apple M4 / 16 GiB host. Active Stonecutter targets remain
`26.1.2-fabric` and `26.2-paper`.

Final selections use internal APFS, as recorded in `host-storage.json`.
Earlier discovery used the external SSD. The medium recovery selection has
updated join/ACK setup and a peaceful world; its tool identities differ from
the earlier storage/small-command selection. Do not treat those selections as
a paired optimization comparison or pool them into a healthy baseline.
No compilation or other timed workload runs during measurement.

## Harness review and recovery

Independent review found missing distinct command timing, tier-independent
four-point scans, restart-error masking and inadequate restore replay evidence.
The fixes add measured mixed families/tier-sized scans, clear stopped handles
before restart, and check consumed tokens plus explicit rejection. Native
failure injection under `/Volumes/ssd/sw-task10-restart-{before,after}/` proves
the old cleanup masked a serialization failure and the fixed cleanup preserves
it; all owned groups stop. The proof is in
`/Volumes/ssd/sw-task10-restart-proof.json`.

Final review found another checkpoint window: a known bootstrap, saved-state,
reopened-state or reopen-log failure could disappear if a later operation threw.
After all native collection finished, checkpoint writes were added immediately
after these validations and from nested reopen teardown. Four tests drive the
real case runner with injected boot/snapshot/identity errors: all four reproduce
BLOCKED before the fix and preserve FAIL afterward. The patch changes result
persistence only. Workload definitions, clocks, rates and metric calculations
are unchanged; collection source hashes matched at completion before this patch.
The archived source delta is in the final command root's
`diagnostics/post-collection-checkpoint-fix.json` and `analysis-sources/`.

Full-wire polling and unbounded Python history caused excessive retained data.
The bot now provides an effects-only draining snapshot without materializing
wire bodies; Python retains at most 1,024 recent events and one latest summary.
Durable JSONL remains complete. Bounded queues apply backpressure while draining
acknowledgements. Unfulfilled demand is INCONCLUSIVE; independent failures remain
FAIL. Offered rates, queue capacity and measured lateness budgets were not
relaxed.

First-ACK diagnostics distinguish native preprocess receipts from actual
Brigadier callback completion. `commands.tree` installs the callback wrappers;
smokes without it retain empty callbacks. Smokes 15 and 16, with instrumentation,
pass native `/wp help` acknowledgement for ten compatible actors. The runner
waits for 100 healthy join ticks and a real acknowledged command, grants
operator admission sequentially, then primes before warm-up. Only a native ACK
deadline is retryable during this setup. Python's 120-second result wait covers
the bot's 90-second ACK wait; measured demand/lateness checks stay strict. Late
ACKs for canceled actions cannot complete another action. Prepared worlds use
`difficulty peaceful`; the diagnostic `gamerule doMobSpawning` path that raised
a native command exception is retained in its smoke root and is not reused.

The older `/Users/mini/.codex/tmp/sw-task10-20261101-commands/` selection completed
two full medium repetitions, retaining both TP-feedback failures, before repeat
three hit ENOSPC. A setup source changed after its preparation; it is not pooled
with the fresh final three-repeat selection. The failed attempt and report
remain intact.

## Log-health correction and retained failures

One earlier internal control retained a 10.96-second native tick and watchdog:
`/Users/mini/.codex/tmp/sw-task10-20261101-control/attempts/563d7543-cdf4-45ef-8e31-72cc1861648a/server/logs/latest.log`.
Its server thread was RUNNABLE in recursive Brigadier `CommandNode.equals` /
`AbstractMap.equals` from `ClientboundCommandsPacket.enumerateNodes`. This is
consistent with the [earlier command-tree investigation](2026-10-27-command-tree.md),
but these instrumented results alone do not isolate production versus probe
attribution or establish current-development behavior.

The original control report said BLOCKED: exception cleanup skipped health
assessment, and raw terminal color/prompt escapes also evaded the severe-log
matcher. Cleanup now checkpoints independent health results on exceptions;
the parser removes terminal formatting before testing severity. Regression
checks fail before the fixes and pass afterward. The actual raw watchdog log
reproduces PASS with the old parser and FAIL with the corrected parser, retained
in `sw-task10-20261101-final/diagnostics/log-parser-proof.json`.

Corrected post-collection health decisions are retained separately in the final
command root's `diagnostics/server-health-replay.json`. Raw result files and
timing evidence are not overwritten. Replaying log classification does not
recollect or modify measurements.

Earlier discovery remains under `/Volumes/ssd/sw-task10-final-20261031b/`:
small commands completed two measured/save/reopen repetitions before a later
TP action failed; medium commands failed feedback and warm-up queue checks;
small skewed storage completed three repetitions; medium skewed storage did not
fulfill demand. `/Volumes/ssd/sw-task10-controls-20261031b/` retained two normal
medium repetitions before a queue limit. None is promoted as a healthy baseline.
Other owned `sw-task10-behavior-*`, `sw-task10-boundaries-green1` and ACK smoke
roots retain their failed diagnostic assumptions and recovery evidence.

Disk exhaustion is retained. Byte-identical JARs in stopped task-owned roots
were replaced by APFS copy-on-write clones, and older large logs were
transparently compressed with logical hashes checked. No raw evidence was
deleted. Records under `sw-task10-final-20261031b/diagnostics/` include
`storage-reclamation-{clones,compression}.json` and
`internal-storage-reclamation-clones-20261101.json`; the latter reclaims roughly
9 GB of duplicate internal JAR storage before the final medium rerun.

## Tool verification

158 Python tests and 22 pinned Rust tests pass; the optimized Rust build passes. The fully qualified
`rtk ./gradlew :paper:1.21.11-paper:performanceProbeJar --console=plain --max-workers=2`
passes. There is no active-project switch. The probe archive contains 17 performance support classes with zero class-name
overlap with the audited release; no probe code is added to production JARs.
No production code, translations, commit, push, publication or deployment is
included in Task 10. Cleanup verification finds no active owned native server
or bot workload.
