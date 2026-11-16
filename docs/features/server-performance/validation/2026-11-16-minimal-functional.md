# Minimal critical functional checks — Step 2

Completed November 16, 2026, against the [authoritative minimal plan](../plans/2026-10-24-server-performance.md).

**Result: all four required functional groups PASS. Step 2 is complete.**
Command-tree delivery (Step 3), bounded timing (Step 4), and the combined final
report (Step 5) remain pending. This is functional regression evidence, not a
performance-budget or release-certification result.

## Retained evidence and identities

Final root: `/Volumes/ssd/sw-performance-minimal-step2-20261115-r4/`.
The root contains `report.md`, sealed manifests, per-case results, source snapshots,
raw native/client receipts, expected values, fixture records and server logs.
Run ID: `cb60e4cd-10e3-4485-9fe5-bc215a13f873`.

- Candidate source revision: `0ea2d348df673cb9495a8f45b1d7e19a620a790f`.
- Candidate SHA-256: `a9b21e37d2deaddcea29c22e8763e6ac5b8ab65abf946b201fd50c3602c77915`.
- Probe SHA-256: `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9`.
- Bot SHA-256: `95f5bb03dde1b4b6efe7c6275572be0d1f47c25398e4c4a4b97c62bfc41bf887`.
- Runtime: Paper 1.21.11 build 111, pinned Java/server hashes in `runtime-lock.json`.
- Prepared `navigation-terrain-small/baseline.json` SHA-256:
  `28ecb974b73e623484c56286fd497d213f29f0e4e4347aa7a170a9615c5dd06d`.
- Build provenance and cached candidate:
  `/Volumes/ssd/sw-performance-minimal-20261115-inputs/`.

All 287 recorded production/build source hashes matched the candidate build identity.
The production JAR contains no performance-probe classes. No production repair,
probe/bot rebuild, commit, push or baseline promotion was performed.

## Functional outcomes

| Group | Result | Retained attempt under `functional/` |
| --- | --- | --- |
| Storage/permissions | PASS | `bc0563e9-2b7e-4de9-8407-1b7d557ab507` |
| Navigation lifecycle | PASS | `3076a908-0a69-44d4-bdca-7e167ff620ee` |
| Terrain maps | PASS | `ee8ee26c-02e5-4368-a5cc-e2368110bc3a` |
| Direct item protection | PASS | `ce5911a7-c0b1-402b-bbe4-2483ffedfb85` |

The four cases have 24 named PASS checks, including constituent chest/pickup
checks. Each required group reports complete coverage. Functional runs have no
warm-up/measurement phases or repetitions for timing statistics.

### Storage and permissions

Four selected native command leaves create, edit, remove and restore waypoint
contents, with independent expected fields and revisions. Each mutation uses its
own fixture list. A real protocol player with an explicit edit denial cannot alter
committed state. Exact saved JSON and reopened native contents match the expected
catalog. Three processes cover initial inspection, mutation fixtures and reopening.

### Navigation lifecycle

One owner enables all six supported methods once: compass, map, bossbar, actionbar,
text display and world marker. Checks cover native methods/items, decoded client
effects, A-to-B retargeting, deletion of active B and removal of its persisted
resume state, restarting A, stopping, and active disconnect. Ordinary items remain
unchanged. World-marker badge/distance entity identities and text are observed on
both server and client; marker-motion cleanup is checked.

Stopping leaves zero sessions, displays, protected items, map targets, target-index
entries, tick counters and marker-motion tracks. One login-scoped task remains
while the owner is connected, matching `NavigationProtectionListener.startNavigationTick`.
After disconnect that task is gone. One owner map-cache entry is intentionally
retained in both states. The connected baseline and both cleanup states are saved.

### Terrain maps

Two owners have distinct map IDs and independent, nonzero 16,384-byte fixture
rasters for each of two regions. `expected.json` records the arrays before any
candidate comparison. Owners stay outside both regions to prevent vanilla map
exploration from modifying the test fixture. Native arrays are compared byte for
byte; client reconstruction requires all 16,384 known pixels and the expected
scale/CRC32. Owner/region patterns are different, making substitution observable.

A/B/A switches validate both regions, then only one owner retargets to B while the
other remains on A. Stale on-disk pixels are deliberately written for the active
region before switching away: the native save must overwrite them with the exact
active raster. Saved files and native/client state after stop/reopen match the
same independently prepared expectations. Both server processes stop cleanly.

This verifies prepared terrain restoration, delivery, isolation and persistence.
It does not validate vanilla geographic block-to-map-color rendering. Client
coverage uses a reconstructed checksum; full byte equality is checked natively.

### Direct item protection

One owner performs protected/ordinary inventory moves and drops, direct protected
and ordinary chest interactions, and protected/ordinary pickup paths. Native event
receipts establish cancellation/allowance, valid direct locations and ordinary-item
conservation. No recursive nested cleanup or hopper-transfer requirement is added.

## Harness safeguards and review

- Minimal cases route to distinct candidate-bound adapters. Command-tree and
  timed adapters remain explicitly pending.
- Changed candidate, bot, probe, Java or server bytes are rejected. Prepared input
  seals cannot be silently refreshed, and each attempt retains its source snapshot.
- All required named checks are needed for PASS. Observed FAIL survives missing
  coverage; missing receipts/setup evidence remain BLOCKED.
- Native state mismatches use typed decisions through polling and persistence
  checks. Missing client effects and insufficient quiescence remain BLOCKED.
- Independent review found candidate-derived terrain expectations and ambiguous
  exception classification. Both were corrected with regression tests; scoped
  follow-up review confirmed closure. No deferred review findings remain.

Final verification: **284 Python tests PASS**, `git diff --check` clean, prepared
input seal still matches current sources. Seven candidate server process logs
record saved shutdown, with no ERROR/SEVERE verdicts. No test server/bot process
remained at completion. The fixture preparation also completed save/reopen checks.

## Earlier attempts and decisions

The initial root `/Volumes/ssd/sw-performance-minimal-step2-20261115/` retains four
incomplete results caused by obsolete five-method assumptions, eager mapping of
excluded command leaves and the insufficient terrain oracle. These are not
candidate acceptance results. Root `...-r3/` passed storage, protection and terrain;
its lifecycle stop failure came from expecting zero tasks while still connected.
Source inspection and the connected/disconnected observations corrected that oracle.
The final r4 execution reran all four groups with the corrected harness.
Roots `...-r2/` and `...-final/` were prepared but never executed after subsequent
source changes required new seals. No earlier evidence was overwritten or relabelled.

Work continued in the existing feature checkout, consistent with the earlier
execution. The revised minimal plan controls scope; historical broader requirements
remain excluded. Prepared terrain patterns and the intentional online-task/map-cache
retention are explicit test decisions and proof boundaries, not hidden relaxations.

**Next step:** Step 3, native Paper command-tree send timing, client receipt
correlation and permission-tree assertions, then three fresh-process sessions.
