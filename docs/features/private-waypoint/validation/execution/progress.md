# Execution ledger — plan: docs/features/private-waypoint/plans/2026-10-16-private-waypoint.md

Started 2026-10-23 on `feature/private-waypoint`, base `8c6c2fb9`.
The user authorized implementation and a separate local commit for each verified step.

## Decisions and pre-flight

- Work in the existing clean feature branch, preserving the configured Stonecutter targets.
- Keep execution artifacts here under the owning feature, as required by AGENTS.md.
- Execute inline: the plan explicitly excludes subagents unless the user chooses delegation.
- Tasks 1 → 2–4: collection keys and an explicit collection-to-manager resolver carry owner identity through navigation; private storage is wired in Task 2.
- Tasks 2 → 3–4: authenticated-owner mutation gates must hold through persistence; commands and packets must use these gates.
- Tasks 4 → 5–7: source readiness and selection generations precede profile rebinding, client routing, and composed queries/rendering.
- Tasks 5–6 → 7: forms capture profile/connection generation; view changes never redirect an operation.
- Task 8 consumes all preceding behavior and records only executed evidence; unit tests do not establish live acceptance.

## Task status

1. Complete: collection identity and scoped navigation (`20cd599`).
2. Complete: owner storage, quotas, lifecycle (`f80b062`, plus the shutdown review fix).
3. Complete: private commands, permissions, feedback, and scoped restore (verified 2026-10-28).
4. Pending: owner-only protocol and client collections.
5. Pending: local profiles.
6. Pending: client operations and teleport templates.
7. Pending: manager and composed rendering.
8. Pending: platform/live validation and player documentation.

## Task 1 evidence

- Baseline: `./gradlew :common:test --console=plain --max-workers=2` succeeded (up to date).
- RED: focused identity/navigation tests could not compile against the absent collection contracts.
- RED: `NavigationScreensTest.privatePanelAndStoppedResumeKeepThePrivatePrefix` then failed against the existing panel links.
- GREEN: focused identity/navigation tests and full `:common:test` succeeded.
- Full common suite: {'tests': 666, 'failures': 0, 'errors': 0, 'skipped': 0}.
- `./gradlew :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava --console=plain --max-workers=2` succeeded.
- Navigation session format is now 3; old format has no compatibility reader. Minecraft waypoint wire protocol remains unchanged until Task 4.
- All existing navigation producers explicitly select Shared; platform restore resolvers reject Private until Task 2 wires owner storage.
- Reviewed target construction, list/target indexes, owner rejection, scoped links, and removal cleanup. No localization keys changed.
- Live multiplayer, rendering, and input acceptance remain unexecuted.

## Task 2 evidence

- Added isolated per-UUID managers and `WaypointServerCore.withCollection` as the borrowing boundary. Owner gates span quota checks, dimension mutations, callbacks, and persistence. Callers must wait for the operation to return before reporting durable success.
- Eviction tracks queued borrowers, waits for mutations, and retains dirty data on save failure. Borrowed managers reject mutations outside an active owner operation.
- New stores accept current JSON arrays only, without migration readers. Shared loading retains its existing behavior.
- Dedicated root: `<config>/private-waypoints/<uuid>`; integrated root: `<save>/server_waypoint/private-waypoints/<uuid>`. Private managers never enter Shared snapshots or the Shared manager map.
- Added validated, reloadable `privateWaypoints` configuration with defaults 1000/100 and zero unlimited. Invalid configuration leaves the previous published configuration intact.
- RED: new storage and lifecycle tests failed against absent contracts. The first full suite exposed `HelpScreenTest.reloadHelpSaysWhetherEachConfigSettingReloads`; added the setting name to reload help in all six common locales.
- GREEN: focused storage/concurrency/navigation/lifecycle suites, then `./gradlew :common:test :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava --console=plain --max-workers=2` succeeded.
- Full common suite: {'tests': 676, 'failures': 0, 'errors': 0, 'skipped': 0}.
- Lifecycle wiring covers mod/Paper disconnect, server save/shutdown, integrated-world rebinding, and navigation restore under the owner gate. Failed navigation reads preserve saved identity for retry.
- No new locale keys or placeholders; only the literal configuration name was added to existing reload-help text.
- Self-reviewed isolation, lock ordering, eviction races, failed reads/saves, reduced quotas, and Shared behavior. Live acceptance remains pending.

## Task 2 review fix: shutdown lock ordering

- RED: `PrivateWaypointLifecycleTest.shutdownDoesNotHoldSharedLocksWhileWaitingForAnOwner` reproduced a timeout when shutdown held the Shared lifecycle write lock while waiting for a Private operation that read Shared data.
- Close/flush owner stores before acquiring Shared lifecycle locks during shutdown and waypoint reload. Shared persistence remains inside the Shared lifecycle boundary.
- GREEN: `./gradlew :common:test :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava --console=plain --max-workers=2` succeeded; 677 common tests, zero failures/errors/skips.
- Review was performed inline, consistent with the plan's instruction not to dispatch subagents without the user's choice. Tasks 3–8 and all live feature acceptance remain pending.

## Task 3 evidence (2026-10-28)

- Resumed the clean `feature/private-waypoint` branch at `e8f6eae8`. Tasks 1–2 are present in
  the current history (`1d051593`, `fdce04d2`, `e8f6eae8`); the older hashes above are pre-rebase evidence.
- Reused the command grammar for `/wp private`: menu/help, add/list/details/edit/remove/restore,
  navigate, and tp. Private has no upload/download/remote/reload subtree or other-owner selector.
- `WaypointCommandScope` binds the authenticated collection and borrowed manager. Synchronous
  Brigadier handlers and suggestions run with a bounded invocation binding, removed in `finally`.
  Scheduled command work captures owner/player before scheduling, rechecks action permission,
  and borrows only when it executes; no borrowed manager escapes to an async callback.
- Private mutations retain the owner gate through saving. Normal feedback is delivered after the
  borrowing operation succeeds; private changes emit no Shared payload or player announcement.
  Task 4 will introduce owner-only scoped synchronization.
- Restore identity includes collection and executor UUID. A commit hook consumes a successful
  token after persistence while still holding the owner gate. Quota, collision, wrong-owner,
  wrong-collection, expiry, single-use, and failed-save cases are covered. The hook blocks another
  owner operation until it completes and does not run on failed persistence.
- Added six reloadable `CommandPermission.private*` levels and six corresponding permission nodes.
  List/add/edit/remove/navigate default to 0; tp defaults to 2. Existing Fabric/Forge/NeoForge/Paper
  adapters consume the new keys. Paper descriptor defaults remain false so its configured fallback
  levels continue to apply; adapter fixtures cover defaults, reload levels, and supported node grants.
- Paper executes source-aware command work on Brigadier's owning thread; its existing delayed async
  persistence executor is kept separate from command handlers that inspect source/player state.
- Menu/help/action/suggestion links and plain-text command continuations retain their scope. The click
  length guard runs after prefix expansion. Navigation target scope selects both prefix and teleport
  permission, including when viewed through the other command prefix.
- RED: six initial private-command tests failed because `/wp private` was absent. Further tests
  reproduced denied-list menu/help links, cross-prefix navigation links, and cross-prefix teleport
  permission presentation; each was corrected and included in the final passing suite.
- Added all applicable strings to all six common locales. New strings have no substitution arguments;
  translation coverage and chat tests passed. Human translation review remains unexecuted.
- Focused plan command passed: `rtk proxy ./gradlew :common:test --tests '*CoreWaypointCommand*Test'
  --tests '*StringCommandBuilderTest' --tests '*WaypointRestoreRegistryTest'
  --tests '*Platform*Permission*Test' --tests '*HelpScreenTest' --console=plain --max-workers=2`.
- Final verification passed: `rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:compileJava
  :paper:26.2-paper:test --console=plain --max-workers=2`. Common: 706 tests; Paper: 58 tests;
  zero failures, errors, or skips. Active Stonecutter targets were not switched.
- Final review: self-review, following this plan's instruction not to dispatch subagents without
  the user's choice. Checked owner routing, all remaining Shared-only server calls, generated links,
  commit-hook ordering, platform executor behavior, descriptors, and failure feedback.
- `git diff --check` passed. No commit or push. Concurrent changes to `docs/tips/README.md` and
  `docs/tips/command-tree-performance.md` belong to other work and were preserved.
- Tasks 4–8 remain pending. No live multiplayer, vanilla-client, rendering, physical-input, or
  teleport-arrival evidence is claimed. See [validation report](../2026-10-28-private-commands.md).

## Task 3 follow-up: command performance guide (2026-10-28)

- The user requested a refactor against `docs/tips/command-tree-performance.md` after review found
  masked syntax errors, early owner-store access, and missing Private/full-tree performance gates.
- RED: invalid enum options failed to throw at their original argument cursor; nested redirect
  execution showed the same problem; a bounded option completion created owner storage; a Private
  list-tree walk repeated its branch permission lookup for every node.
- GREEN: `PreparableCommand` composes pure option validation with existing scoped execution.
  `ListCommandOptions` resolves native argument values first; Private prepares before UUID
  resolution/scheduling/borrowing. Invalid values reach Brigadier with their original cursor.
- Collection-dependent suggestion providers explicitly borrow the owner's files. Bounded, format,
  and player-state providers use the parsed arguments/live source directly, with permission rechecks.
- Permission requirements on action roots prune denied branches during command copy. Descendants
  retain their own original requirements; the same action permission is no longer fetched at every
  argument node. Execution and suggestions still recheck permissions before private access.
- Added valid/invalid argument and nested redirect tests, completion-range assertions, a one-lookup
  branch-walk regression, and identity-based registered/expanded budgets for Private and the full
  empty-catalog dispatcher with all navigation methods enabled.
- Budgets: 1536 Private-list nodes, 1536 Private-subtree nodes, 4608 complete-dispatcher nodes.
  Observed: 1327, 1469, and 4305 respectively; expanded copies have the same counts.
- Native 26.3 before/after uses the same fixture and actual `Commands.fillUsableCommands`, packet
  construction/indexing, codec, and redirect reconstruction. Both variants: 4307 registered nodes,
  4306 copied/decoded nodes, 73381 bytes. The extra aliases and filtered console-only command explain
  the difference from unit-fixture counts. Raw samples, host context, and source hashes are retained.
- Native medians (copy/packet construction): baseline 12.042/30.602 ms; candidate 10.711/28.275 ms.
  These small isolated sample sets do not establish a live login speedup or production attribution.
- Final verification: `rtk proxy ./gradlew :common:test :cross-server:test :proxy-common:test
  :mods:26.1.2-fabric:compileJava :paper:26.2-paper:test --console=plain --max-workers=2` passed.
  Common 711, cross-server 115, proxy-common 167, Paper 58; zero failures/errors/skips. The full-tree
  native probe and existing issue35 probe also passed. Active Stonecutter targets were not switched.
- Self-reviewed validation order, ownership, inherited and execution-time permission checks, native
  argument serializability, all-navigation budgets, packet round trips, and retained evidence.
  No localization keys or placeholders changed in this follow-up. No commit or push.
- See [performance validation](../2026-10-28-command-performance.md). Tasks 4–8 and live acceptance
  remain pending; this work does not claim live joins, multiplayer isolation, or teleport arrival.

## Task 3 local commit authorization (2026-10-28)

- The user requested a local commit of the completed Task 3 implementation and performance refactor.
- Reviewed the implementation, tests, six locales, feature documents, isolated native probe, and raw
  validation evidence as one coherent scope. Candidate source hashes still match the native run.
- Re-ran `:common:test :cross-server:test :proxy-common:test :mods:26.1.2-fabric:compileJava
  :paper:26.2-paper:test --console=plain --max-workers=2` successfully before staging.
- No push is authorized. Earlier references to uncommitted work describe their verification checkpoints.
