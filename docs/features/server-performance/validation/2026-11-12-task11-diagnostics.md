# Task 11 local profiles and post-disconnect observations

Task 11 remains open. This continuation adds actual JFR capture and native
post-disconnect resource observations to the prepared Paper runner. It does
not replace the [three-repeat original-release run](2026-11-09-task11-continuation.md)
or clear its failures.

## Inputs and scope

- Source base: `a84acfd826c11adeb152ba4fc1a06c18b03a1015`, initially clean
  `codex/experimental-world-marker`; the harness changes are uncommitted.
- Published Paper 4.0.0:
  `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57`.
- Paper 1.21.11 build 111:
  `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3`.
- Probe: `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9`.
- Bot: `95f5bb03dde1b4b6efe7c6275572be0d1f47c25398e4c4a4b97c62bfc41bf887`.
- JDK 21 executable:
  `04005388bac0c272ea914210ca519ce94b2f873ea3962b9874a6859f74d7f279`.

All five actual input hashes matched the working runtime/release locks before
native execution. No production, localization, Java probe, bot, or runtime-lock
change belongs to this continuation. No commit or push was made.

## Harness changes and regression evidence

`navigation_runner.py` previously copied the `profiledDiagnostic` flag into a
result without attaching JFR. It now starts a recording before native behavior
and retains it through warm-up, measurement and disconnect. Finalization runs
on workload errors and interruption. An attach/stop failure retains a blocker
alongside independent product failures. A recording dumped during server exit
is discovered after shutdown and linked from the result.

After fixture quiescence, the runner snapshots navigation state, closes the
real bot, waits until no owners remain online, and observes at least ten
additional native ticks. Sessions, tasks, displays, bossbars, map targets,
target-index entries and tick counters must be zero. The per-owner map cache
is deliberately retained at its pre-disconnect size. Raw before/after snapshots
and the first disconnected tick are saved as `quiescence.json`.

The native snapshot's `items` field counts only online inventories. Its zero
after quit does **not** establish saved-inventory or world-item cleanup. That
coverage stays explicitly `BLOCKED`, even when the observed resource counters
match. This continuation does not assert ordinary-item conservation from that
zero.

The runner transfers bot closure to a callback that clears its owned handle,
avoiding a second stop of an already released process. Partial results evaluate
measurements as soon as they exist, so a later interruption cannot hide a
measured tick-budget failure.

Regression tests were observed failing before the fixes: missing diagnostic
module, absent JFR capture/quiescence, actual `jcmd` timeout exception handling,
double bot closure, interrupted slow measurements, a profile written only
during server shutdown, and the false inference from zero online items.
The complete Python suite passes **237 tests**. A fresh static review identified
the four closure/accounting gaps; their regressions pass after correction.

## Retained preliminary attempts

`/Volumes/ssd/sw-performance-task11-diagnostics-smoke-20261112` is a short
one-owner native adapter smoke. It selected all five published navigation
methods, disabled navigation, disconnected, and retained a readable seven-second
JFR. At tick 124, eleven ticks after the first empty-player observation at tick
113, `tickCounters` remained 1. Sessions, tasks, entities, bossbars, items,
map targets and target-index entries were zero; the deliberate map cache held
one entry. This reproduces the original stop-then-quit counter failure. Its
outer smoke cleanup also exposed the now-fixed second bot shutdown; the
original traceback and raw snapshots remain.

`/Volumes/ssd/sw-performance-task11-profiles-20261112` is the first prepared
diagnostic attempt. It was interrupted with SIGINT while correcting that
closure issue, exited 130, and retained its partial result and JFR. It is not
a completed diagnostic selection.

## Fresh diagnostic selection

Root: `/Volumes/ssd/sw-performance-task11-profiles-20261112-2`.
The sealed selection has six medium cases, ten connected owners, aggregate
offered rate 50 actions/second, 30 seconds warm-up and 60 seconds measurement.
Each diagnostic uses one fresh process repetition. These profiled samples are
separate from unprofiled three-repeat acceptance evidence.

```sh
rtk proxy python3 tools/server-performance/run.py prepare \
    --output /Volumes/ssd/sw-performance-task11-profiles-20261112-2 \
    --targets paper-1.21.11 --suite all \
    --scenarios navigation,map-terrain,protection \
    --variants methods,warm-cold,inventory-click-drag-open-close,boundary-dimension,death-respawn-world-change,death-reconcile \
    --tiers medium --profile
rtk proxy python3 tools/server-performance/run.py run \
    --root /Volumes/ssd/sw-performance-task11-profiles-20261112-2 \
    --mode audit --suite all --tiers medium --repeats 1
```

Run ID: `551fe590-c1ee-41f6-ba62-58398145e4db`. The six-attempt filesystem
timestamp envelope is `2026-11-12T19:04:08.969Z` to
`2026-11-12T19:17:57.373Z`: **13 minutes 48 seconds**, excluding fixture
preparation and subsequent profile analysis. Host: Mac16,10, Apple M4,
10 physical/logical cores, 16 GiB RAM, macOS 26.5 build 25F71, JDK 21.0.11,
1–2 GiB G1 heap. The bot runs on the same host.

The report exits **1: three FAIL, three BLOCKED, no PASS**. All six attempts
reached `REPORTED`, and all six JFR files pass `jfr summary` and JSON event
extraction. Raw streams, hashes and local analyses remain under the owned root.
`source-snapshot/` and `source-snapshot.json` preserve the exact diagnostic
harness before the subsequent receipt correction below.

| Medium catalog case | Decision | Old collector completed/offered | Native tick samples | p95 / p99 ms | Tick counters after quit |
| --- | --- | --- | --- | --- | --- |
| Terrain boundary/dimension | FAIL | 3000/3000 | 1015 | 3.85 / 6.03 | 8 |
| Terrain warm/cold | BLOCKED | 3000/3000 | 1184 | 4.44 / 5.61 | 0 |
| Navigation death/respawn/world | FAIL | 3000/3000 | 100 | 4372.51 / 5179.18 | 9 |
| Navigation methods | BLOCKED | 3000/3000 | 1182 | 4.20 / 5.51 | 0 |
| Protection death | FAIL | 3000/3000 | 94 | 5147.36 / 5266.13 | 7 |
| Protection container/drag | BLOCKED | 7/3000 | 1124 | 6.03 / 7.66 | Unobserved |

These are **profiled diagnostic** timings, not acceptance measurements. The
completed-action column preserves what that version of the collector recorded;
it does not prove acknowledged native work. The correction below rejects its
client-issued command receipts as roundtrip evidence. Missing behavior judges,
unarmed lifecycle observations and unsupported boundary operations remain
blockers. The two `/kill` cases exceed the tick budget independently of those
blockers. Boundary/dimension fails its observed quit-time counter contract; the
watchdog from the earlier unprofiled run was not reproduced in this diagnostic.

Container setup recorded its behavior checks, but timed opens repeatedly report
`other container did not open` / `another container already open`. Bot shutdown
retains those prior action errors, and the post-disconnect observation is
unavailable for that case. The runner still stops its owned server and retains
the JFR; it does not manufacture zero resource counts. Container throughput
and cleanup remain unaccepted.

## Local profile interpretation

`summarize_profiles.py`, the six `profiles/server.samples.json` exports,
`profiles/server.summary.txt` outputs and `profile-summary.json` are retained
locally. The JFR files are approximately 4.8–6.5 MB each. They cover native
behavior, warm-up, measurement and disconnect, rather than only the measured
minute.

For navigation death/respawn, `CommandNode.equals` appears in 3,920 of 5,795
server-thread execution samples; for protection death, in 4,032 of 5,923.
`AbstractMap.equals`, `String.equals` and Brigadier argument-node equality are
prominent leaf frames. This locates a sampled hot path under these diagnostics;
it does not establish exclusive CPU time, an exact allocation budget, or the
plugin's incremental contribution. There is no matched no-plugin control.
The decision is **further measurement** before selecting another production
repair. No source repair or baseline promotion follows from these profiles.

## Command receipt correction after profiling

Inspection of the pinned bot showed that catalog commands without
`acknowledged: true` return `completionScope: client-issued` after a short
client delay. The old collector accepted any `client-monotonic` elapsed value
and relabeled it `client-roundtrip`. This affects the old Task 11 action and
latency interpretation; native tick samples and recorded defects remain.

The schedule now requests acknowledgements for waypoint commands, and setup
instruments their real native leaves using `commands.tree`. Completion/latency
count only when action ID, actor, command, native `command-completed` stage,
`serverObserved`, completion scope and client clock all match. Marker-only,
client-issued, wrong-owner, failed and stale receipts cannot count. Unacknowledged
terminal receipts remain a load-generator evidence gap, not evidence of server
saturation. Vanilla `/kill` and `/tp`, movement and physical menu actions still
need their own completion adapters; the existing probe instruments waypoint
leaves only.

The new regression first demonstrated an issued-only receipt being counted as
one fulfilled roundtrip, then passed with zero fulfilled actions and no latency
sample. These profiled roots are retained unchanged and cannot be rerun using
the changed executable inputs.

Both interrupted and ordinary-error exits after a slow measured window have
regressions that retain the tick-budget FAIL and the later error. The ordinary
error-exit correction was verified by regression after the native check below.

## Three-process native check after the correction

`/Volumes/ssd/sw-performance-task11-repetition-smoke-20261112` was prepared
fresh after the receipt correction. The retained
`run-repetition-smoke.py` under the profile root executes three independent
one-owner processes with one-second warm-up and one-second measurement,
offering two waypoint commands per second. `adapter-smoke.json` explicitly
labels these shortened phases as adapter evidence, not performance acceptance.

All three repetitions reached measurement and post-disconnect observation.
Each measurement retained **2/2 native command roundtrips**, matched to actor,
command and action ID; each retained two corresponding latency samples. Bot
closure did not trigger the unowned-process guard. Profiling was disabled,
`paths.profiles` is empty, and there are no JFR files anywhere in that root.
The scenario decision remains `BLOCKED` for its unimplemented behavior judge
and disconnected-item coverage. All owned server/bot processes stopped.

Terrain eviction, exact save/reopen comparisons, disconnected inventory/world-item
observations and remaining variant behavior/delivery gaps still belong to Task
11. Its master implementation/execution checkboxes remain open.
