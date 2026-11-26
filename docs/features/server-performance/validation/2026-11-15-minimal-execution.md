# Minimal critical suite execution — November 15, 2026

Plan: [authoritative minimal plan](../plans/2026-10-24-server-performance.md).

Status at the Step 1 checkpoint: candidate binding and fixture preparation complete.
Step 2 subsequently completed with [four native functional groups PASS](2026-11-16-minimal-functional.md).
Steps 3–5 remain pending; no candidate timing result yet.

## Execution rulings

- The current user request authorizes implementing and executing the plan; the
  plan's prohibition on starting runtime execution applied to the earlier documentation edit.
- Work in the current feature checkout to preserve the user's uncommitted revised
  plan. No commits, production repairs or baseline promotion.
- Keep seven separately identified cases: four functional cases, command-tree
  delivery (three processes), and idle/retarget (three processes each). Functional
  cases have no timing windows. Unsupported adapters must remain BLOCKED.
- The revised master plan supersedes the historical specification. No broader
  platform or original-release checks enter this delivery.

## Interface review

Selection feeds preparation, routing and reporting. Historical runners assume audit
mode and timed cases; minimal cases must never fall through to those adapters.
Candidate provenance must survive manifest loading and prepared evidence sealing.

## Progress

- Baseline Python suite: 247 tests passed.
- Step 1: complete. Distinct selection, candidate/tool provenance, native fixture identities and the five-group check map are recorded.
- Step 2: subsequently completed; see the [functional report](2026-11-16-minimal-functional.md).
- Steps 3–5: pending.

## Native check map and remaining gaps

No historical runtime result is reused as a candidate PASS.

| Minimal group | Existing checks to reuse | Missing integration/observations |
| --- | --- | --- |
| Storage/permissions | `bots/task10_smoke.py`, `scenarios/command_behavior.py:exercise_semantics`, canonical storage snapshots | Select only create/edit/remove/restore and one denied mutation; bind candidate without recomputing tool identities; retain exact save/reopen comparison. |
| Navigation lifecycle | `bots/navigation_smoke.py`, `scenarios/navigation.py` target/method/conservation/cleanup assertions, `navigation_diagnostics.py` | Remove hard-coded published artifact; select all-methods once rather than all 31 combinations; add current delete-target semantics and stop/disconnect evidence. |
| Terrain | `bots/terrain_smoke.py`, exact native/client pixel assertions and reopen checks | Candidate binding, two-owner isolation after one retarget, minimal two-region selection. Forced eviction is excluded. |
| Direct protection | `bots/protection_smoke.py`, `scenarios/inventory_container.py`, independent ordinary-item and physical event assertions | Bind candidate and select one inventory/chest/drop-pickup path; validate top-level semantics rather than historical recursive expectations. |
| Command-tree delivery | `CommandAssertions.java` tree inspection, existing Azalea protocol client | Native send-entry to connection-handoff instrumentation, encoded size, client installed-tree capture, role/phase/sequence correlation, permission grant/revoke trigger and independent visible-tree assertions. Existing `commands.tree` inventories registered commands; it is not send-duration evidence. |
| Idle/retarget timing | `scenarios/inventory.py` tick collection; `navigation_workload.py` native command ACK matching | Idle currently executes matched control/release pairs, so cannot be reused unchanged for three candidate-only idle processes. Retarget requires per-owner A/B alternation and all methods, pre/post target/item checks, candidate acceptance and BLOCKED delivery gaps. |

The minimal IDs deliberately stop at an explicit BLOCKED dispatch until these
adapters are implemented. The selection is not an executable passing suite yet.

## Candidate/tool binding

Retained inputs: `/Volumes/ssd/sw-performance-minimal-20261115-inputs/`.

- Source revision: `0ea2d348` (full revision and per-source hashes in `build-identity.json`).
- Candidate SHA-256: `a9b21e37d2deaddcea29c22e8763e6ac5b8ab65abf946b201fd50c3602c77915`.
- Probe SHA-256: `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9`.
- Bot SHA-256: `95f5bb03dde1b4b6efe7c6275572be0d1f47c25398e4c4a4b97c62bfc41bf887`.
- Runtime: Paper 1.21.11 build 111; executable/server hashes retained in `runtime-lock.json`.
- Build: `./gradlew :paper:1.21.11-paper:shadowJar :paper:1.21.11-paper:performanceProbeJar --console=plain` passed. No active-version switch.
- Production archive inspection found no performance-probe classes.
- Native capability doctor passed. This validates tool availability/identities, not candidate behavior.

The candidate manifest explicitly records `provenance: candidate`; publication
identity is not inferred from the JAR filename. Its bytes are copied to a unique
hash-named cache entry. Prepared roots preserve the selected mode and per-case
repetitions and reject attempts to relabel historical cases as minimal.

## Review

One independent read-only review found three issues: historical roots could be
requested as minimal, minimal doctor selected unrelated platforms, and run mode
could relabel sealed candidate/audit selections. All three were reproduced by
focused regression tests and fixed. No native acceptance claim follows from these
tests. The report remains in progress until Steps 2–5 have retained judged evidence.

## Verification and fixture preparation

- Python: `python3 -m unittest discover -s tools/server-performance/tests` —
  **259 tests passed**, including 12 new selection/provenance regressions.
- `git diff --check` passed.
- Prepared root: `/Volumes/ssd/sw-performance-minimal-20261115-prepared/`.
- All four native fixtures completed placement, save and reopen checks. The eight
  owned fixture server logs record shutdown and all-dimensions-saved messages.
- Prepared evidence seal and executable input hashes verified after preparation.
- No timed candidate workload or candidate functional group has run.

| Fixture | Baseline JSON SHA-256 |
| --- | --- |
| idle-small | `26bb181acbb4449fcc815b53bbde39ef04020ec1dea1ae5ae7e31433716fea12` |
| navigation-terrain-small | `afbea5ebd0a63687fd6cfd0ed4446d5c9407ca663dfacddf27affd2188037a8f` |
| idle-medium | `4ac2ebf0edcc6db708e07558f6fed891632b3d388daec6ad778364f3f4d197ff` |
| navigation-terrain-medium | `21b24e598347ecb857029e1b124b29e10f316fc3df74abe1740f39f3ee3be777` |

At this checkpoint the next step was the four minimal functional adapters; those
are now complete in the linked Step 2 report. The current
prepared root is retained evidence; later tooling changes require a new prepared
root under the existing input seal rules. This avoids claiming unchanged harness
identity after adapter edits. Production source was not changed and no commit was
created by this execution.
