# World marker promotion execution

Plan: [2026-11-12-world-marker-promotion.md](../plans/2026-11-12-world-marker-promotion.md).
Plan and contract commit: `55d0dd6c`.

## Rulings and pre-flight

- Continue in the existing `codex/experimental-world-marker` checkout: this is ongoing work on its feature branch, with pending feature docs to preserve. No new worktree or branch is required for the authorized continuation.
- Initial commit authorization covered the plan and its referenced contract. The user subsequently authorized committing the promotion implementation, tests and documentation. No push is authorized.
- The execution ledger lives in this feature's `validation/` folder to comply with the repository artifact rule; no root-level Superpowers artifacts are created.
- Task 1 produces the native settings oracle and evidence consumed by Task 2. Task 3 checks the unchanged versioned adapters; Task 4 consumes all evidence and preserves unperformed acceptance gates. No conflicting interfaces were found.

## Task 1: Native size and projection acceptance

Status: complete. Starting revision: `55d0dd6c`.

The oracle tests use literal fixtures to catch incorrect scaling, offsets, projection, non-finite values, missing/duplicate rows, changed IDs and world insertion. Their expectations are independent of the production projection implementation.

- Baseline Python suite: 237 tests passed (`/tmp/world-marker-promotion-python-baseline.log`).
- RED: eight new tests failed because `assert_marker_configuration` was absent (`/tmp/world-marker-promotion-red.log`).
- GREEN: all eight tests passed with the oracle; full Python suite passed 245 tests (`/tmp/world-marker-promotion-python-green.log`). The suite's printed `BLOCKED` report is an expected fixture test result, not a failed test.
- Added native runner phases for setting endpoints/fractional values, stable IDs, observer isolation, near/far retargets, configuration while disabled and default reset. Native execution is Task 2; Task 1's green unit tests do not establish that those phases have passed on a server.

## Task 2: Current Paper native acceptance

Status: complete.

- Production Paper 1.21.11 shadow JAR and performance probe built successfully in 11 seconds (`/tmp/world-marker-promotion-paper-build.log`).
- Added same-identity reconnect restoration, zero-resource disconnect checks, new-session defaults and Private target/outsider rejection phases. Reconnect uses a new owned bot-log directory so the original process receipts are preserved.
- Attempt A (`/Volumes/ssd/sw-world-marker-promotion-2026-11-12-a`) passed all ten setting/reconnect cases, then failed on Private fixture creation. The harness command copied `position` from a Java test double, where it stands in for the position argument; the real command requires three coordinates. Replaced that token with the documented `320 -61 320` input and retained attempt A's failure. No product fix was inferred from this invalid input; the next native run must confirm the harness correction.
- Attempt B (`/Volumes/ssd/sw-world-marker-promotion-2026-11-12-b`) passed all twelve setting cases, Private owner isolation, motion/cadence/HUD checks and cleanup. All recorded resources were zero after disconnect; the server log contained no ERROR/SEVERE/FATAL records. The correction was confirmed without modifying product Java. See the [native report](2026-11-12-promotion-native.md) and [compact retained receipt](2026-11-12-promotion-native.json).
- Full Python suite after reconnect/Private additions: 245 tests passed (`/tmp/world-marker-promotion-python-task2.log`).

## Task 3: Platform/API boundaries

Status: complete for the planned compile selection; other native runtime coverage remains pending.

- Seven selected platform compile tasks and the common suite passed: 734 common tests, zero failures/errors/skips. [Exact targets, command and proof limits](2026-11-12-platform-checks.md).
- Generated 1.20.1/1.20.2 and current mod adapters preserve the interpolation and teleport-packet boundaries. Active Stonecutter targets and product source files were unchanged.

## Task 4: Rendered acceptance and review

Status: in progress; rendered vanilla-client acceptance is pending.

- Installed-client inventory found 50 loader-based profiles under `/Volumes/ssd/minecraft_clients/versions`, including 1.21.11 Fabric and Forge, but no vanilla profile (`net.minecraft.client.main.Main` without a loader). The installed-store lock had no open holder.
- The repository's rendered live-test harness currently targets isolated singleplayer map/editor scenarios and rejects remote/Quick Play launch exports; it has no prepared vanilla marker acceptance scenario. HeadlessMC with LWJGL stubs cannot establish pixel appearance. No unrelated client profiles, worlds or launcher accounts were modified.
- Ruling: keep rendered acceptance explicitly pending rather than adapting an unrelated loader-based singleplayer suite or treating protocol bot data as rendered evidence. Completing it needs a vanilla client profile and a disposable rendered multiplayer test scenario.

## Independent review and fix pass

- Read-only review found two P2 acceptance gaps in attempt B, with no product defect inferred: the maximum radius used a target only about 28.3 blocks away, so even a radius incorrectly clamped to 30 passed; disable/re-enable preservation was masked by overwriting both settings while disabled.
- Reproduced the maximum-radius gap against B's real snapshot: the configuration oracle accepted requested radius 30 as well as 32. A fresh native run must distinguish those values with a target beyond 32 blocks.
- Added a farther fixture and a projection-active maximum case, plus disable/re-enable at fractional settings without intervening setters. Added client-observed query boundaries after queued-feedback barriers and assertions on fresh size/distance query responses; older setter messages cannot satisfy these readback checks.
- RED: two new readback tests failed while `assert_setting_feedback` was absent. GREEN: all ten focused tests and all 247 Python tests passed (`/tmp/world-marker-promotion-review-red.log`, `/tmp/world-marker-promotion-review-green.log`).
- Review boundaries ruled explicitly: rendered appearance remains pending; broader modded/Folia native acceptance and capacity/performance are not claimed; server-restart durability was not exercised or claimed; only the seven documented platform compile targets are certified as compile checks. No further product requirements were inferred from those unperformed conditions.
- Post-fix attempt C (`/Volumes/ssd/sw-world-marker-promotion-2026-11-12-c`) passed all thirteen setting cases and all other native phases. Its maximum snapshot rejects radius 30, while all thirteen readbacks occur after their client-observed query boundaries. Native report/receipt now reference C. No ERROR/SEVERE/FATAL records appeared in the passing server log; owned processes stopped successfully.
- Final checks: 90 relative documentation links resolve; changed Markdown/Python/JSON have no tabs or trailing whitespace; the retained receipt's runner, raw-evidence and artifact hashes match current files. Both the uncommitted diff and plan commit pass whitespace checks. The two review findings were addressed and verified without another review round. Product runtime code and translations remain unchanged. Rendered acceptance and broader native backend coverage remain pending; the overall acceptance plan is not marked complete.
