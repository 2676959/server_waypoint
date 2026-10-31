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
4. Complete: owner-only protocol and client collections (verified 2026-10-28).
5. Complete: local profiles (verified 2026-10-28).
6. Complete: client operations and teleport templates (verified 2026-10-28).
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

## Task 4 execution (2026-10-28)

- Started from clean `feature/private-waypoint` at `c48a4397`; Tasks 1-3 confirmed in history and this ledger.
- Scope: protocol 3, owner-only server synchronization, separate client server caches and generations.
- Pre-flight: Task 3 owner gates persist before deferred feedback; Task 4 must retain those gates and keep all map integration producers Shared-only. Task 5 consumes the selection and generation contracts; Local persistence remains Task 5.
- Execution scope: only the requested Task 4, on the existing feature branch and ledger. No new worktree, delegation, commit, or push.

## Task 4 evidence (2026-10-28)

- Protocol 3 carries explicit Shared/Private scope on snapshots, updates/tombstones, list renames,
  edit requests/results, and revision requests. Registered collection-state message ID 8 separates
  authorization from readiness. Invalid scope bytes and Private upload constructors/wire data fail
  before mutation. The cross-server protocol and catalog model are unchanged.
- Private packet ownership comes from the authenticated sender UUID. Both packet and command
  delivery use one read gate; private broadcasts are rejected. Private edit permission is checked
  before borrowing, and success/deltas are delivered only after the owner persistence gate succeeds.
  Failed saves return `PERSISTENCE_FAILED`; Shared edit behavior remains covered by existing tests.
- Queued Private frames recheck read permission at transmission time. Paper evaluates this guard
  on the player's owning region after scheduling. Observed denial sends unreadable state; no denied
  Private delta is transmitted. Protocol-incompatible peers remain outside custom synchronization.
- Added source-specific stores, readiness, trackers, pending edit identities, and selection contracts.
  Equal names/revisions and equal request IDs cannot cross collections. Authorization alone does
  not mark Private ready; denial clears its model/pending edits without changing Shared or Local.
- Private disk caches are keyed by account UUID, address digest, and backend ID. Current-format
  loading has no migration reader; a failed cache read requests authoritative server data instead.
  Loaded data remains unavailable until the current connection authorizes and synchronizes it.
- Connection/account/backend/profile rebinding invalidates captured selections and pending results.
  Loader handlers capture the generation before their own client-thread queue; outgoing batches and
  deferred upload outcomes also reject obsolete generations. Integrated Private access uses the
  authenticated local player's network path and detached client cache, never a server owner-manager map.
- RED/GREEN evidence covers absent scope/client contracts, authenticated owner routing, queued
  revocation, cache rebinding, and the review-found collision of equal Shared/Private edit request IDs.
  Full-suite translation audits exposed missing persistence-result strings and overlong chat copy;
  both were fixed. Strict cache loading exposed an unnamespaced test dimension, corrected to the
  actual namespaced format. The expanded Forge compilation exposed an ambiguous callback overload;
  using a void block lambda resolved it.
- Updated all six common and all six client locales for persistence failures. The incompatible-client
  text reports protocol 3 because no feature release number has been assigned. Placeholder coverage
  and chat-width audits pass; human translation review remains pending.
- Full verification: common 718, active Fabric client 979, Paper 58, cross-server 115, proxy-common 167;
  zero failures/errors/skips. Older Fabric focused tests: 26, zero failures/errors/skips.
- Representative compilation: Fabric 1.20.1 and 26.1.2; Forge 1.20.1 and 26.1.2; NeoForge 1.20.2,
  1.20.4 and 26.3; Paper 1.21 and 26.2. Active Stonecutter targets remain unchanged.
- Final review: inline self-review, per this plan's no-delegation instruction. Checked all current
  waypoint producers, scoped codecs, persistence/delivery ordering, source-specific recovery, cache
  roots, integrated dispatch, queued work, map-sync calls, and cross-server boundaries. No remaining
  actionable findings. The historical performance exporter remains unchanged because it deliberately
  compiles against a separately supplied published artifact; it is not a producer in this build.
- The exact focused protocol/handler and active-client plan checks also passed. `git diff --check`,
  changed-document link checks, locale JSON parsing, and new-Java whitespace checks passed.
- Task 4 complete. No commit or push. Tasks 5–8 remain pending. No live joins, multiplayer isolation,
  rendering, physical input, vanilla-client behavior, or teleport arrival is claimed.
- See [synchronization validation](../2026-10-28-private-synchronization.md) and
  [retained logs and counts](task-4/).

## Task 4 local commit authorization (2026-10-28)

- The user requested a local commit of Task 4. Preserved the intervening documentation-only commit
  `73769368` and followed its Conventional Commits guidance.
- Re-ran `rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test :paper:26.2-paper:test
  :cross-server:test :proxy-common:test --console=plain --max-workers=2` successfully.
- Reviewed Task 4 implementation, fixtures, six common/client locales, feature documentation, and
  retained validation logs as one coherent commit scope. Protocol 3 is an intentional breaking change.
- Earlier uncommitted/no-commit notes describe their original verification checkpoints. No push.

## Task 5: independent Local profiles and persistence (2026-10-28)

- Started from clean `feature/private-waypoint` at `dc6e94d8`; verified the earlier Task 1–4
  completion records and current client collection contracts before implementation.
- Added `LocalWaypointProfiles`: independent account/address/save roots, normalized address
  digest, UUID-backed directories, GSON index, per-profile command settings, create/rename/select/
  delete operations, and the specified fallback/backend/manual selection algorithm.
- Profile/disk work is synchronous and owner-serialized. Save, target read, and index persistence
  succeed before a switch is published. Save/index failures retain outgoing state. Corrupt indexes,
  missing directories, invalid bindings and malformed files fail closed. No migration or merge.
- Connected client join/proxy rejoin, compatible identity discovery, dimension, leave, and shutdown.
  Incompatible handshakes retain Local; actual disconnect unbinds it. Retained unsaved stores cannot
  be reactivated on another connection. Standalone client initialization preserves Local visibility
  and expansion settings through the existing file serializer.
- RED/GREEN evidence includes absent contracts and regressions for detached-store reactivation,
  list visibility persistence, and inactive deletion suppressing automatic backend selection.
  Added profile/corruption/persistence/concurrency tests and client lifecycle/cache-isolation checks.
- Final verification: common **718**, full active Fabric **1,000**, older Fabric focused **41**,
  exact active Task 5 focused **24**; zero failures/errors/skips. Forge 1.20.1 and NeoForge 26.3
  compilation pass. Active targets remain Fabric 26.1.2 and Paper 26.2 without switching.
- All six client locales include a storage-error message. Translation JSON/key/placeholder audits,
  changed-document links, Java whitespace and `git diff --check` pass. Human language review is pending.
- Final review was inline, per the plan's no-delegation instruction. No unresolved findings. Rulings:
  stay in the existing clean feature checkout; serialize profile/disk work synchronously; fail closed
  on missing indexes beside data; leave source-aware manager/query/render integration to Tasks 6–7.
- Task 5 complete. No commit or push. Tasks 6–8 remain pending. No live Minecraft/proxy transfer,
  graphical controls, rendering, physical input or teleport behavior is claimed.
- See [Local profile validation](../2026-10-28-local-profiles.md) and [retained logs](task-5/).

## Task 5 local commit authorization (2026-10-28)

- The user requested a local Task 5 commit. Rechecked the intended implementation, tests, six client
  locales, feature status/checklists, and validation logs as one coherent scope.
- Re-ran `rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test --console=plain --max-workers=2`
  successfully; common 718 and active Fabric 1,000 tests remain green.
- Earlier no-commit notes describe their original checkpoints. No push or publication is authorized.
- See [pre-commit suite log](task-5/precommit-suites.log).

## Task 6 execution (2026-10-28)

- Started clean on `feature/private-waypoint` at `730096ea`; Tasks 1–5 are present. Only Task 6 is authorized. No commit, push, new worktree, or delegation.
- Pre-flight: Task 5's serialized mutation/persistence boundary and Task 4's generation/scoped-result boundary are the Task 6 routing contracts. Task 7 supplies view/destination/profile controls.
- Ruling: keep execution artifacts in this feature's existing validation/execution directory, and review inline — repository rules and the plan prohibit root artifacts and delegation without user choice.
- Ruling: add an optional display_name tail to the existing atomic add command — add currently discards copied display overrides; a second edit would violate copy atomicity on permission/quota failure. Cost: a small shared/private grammar extension, covered by command tests.

- Client access captures source/generation/profile for add, waypoint edits, list rename/display edits,
  waypoint/empty-list removal, copy drafts/submission, and teleport. Local uses strict profile
  persistence without network or Shared-map events. Server edits complete only on matching scoped
  results; command operations wait for authoritative collection changes and never mutate caches.
- Forms use captured-source validation/suggestions, retain stale drafts, and refuse replacement
  stores. Copy Add preserves display-name overrides and full metadata; source read and destination
  add permissions are rechecked at submission. Runtime dispatch tests exercise Shared/Private replies.
- Teleport uses named literal tokens, locale-independent integer values, live target/profile/connection
  validation and per-version command limits. Rejections include localized explanations; sending is
  only COMMAND_SUBMITTED. World dimension choices use vanilla login data and the captured store.
- RED: absent access/parser/lookup/draft contracts failed compilation; subsequent assertion failures
  covered dimension catalog omission and accidentally applying display-name limits to identifiers.
  Initial command-test fixtures used a native dimension/position shape instead of this suite's
  `overworld`/`position` arguments; corrected those fixture errors before validating the grammar.
- Ruling: keep existing identifier rules separate from display-text limits — copied identifiers can
  exceed 256 characters, while explicit display overrides retain their existing validation. Cost:
  server additions can still be rejected by that client's packet-length limit, reported explicitly.
- Ruling: server add/remove/list commands retain their existing synchronization-confirmation contract;
  waypoint property edits use correlated result futures. No new creation protocol or optimistic
  writes. Cost: command rejection remains normal server chat feedback plus the existing Add timeout.
- Final review: self-review, as required by this plan's no-delegation rule. Reviewed equal identifiers,
  scope/owner routing, generation changes, permissions, quota atomicity, pending cleanup, detached
  metadata, persistence errors, templates, translations, marker balance and unchanged active targets.
  Restored edit error-field highlighting; no unresolved review findings remain.
- Full verification: `rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test --console=plain
  --max-workers=2` passed: Common 720, Fabric 1022; zero failures/errors/skips.
- Compatibility: focused client/template/form tests on Fabric 1.20.1 passed 102 tests; Forge 1.20.1,
  NeoForge 26.3 and Paper 26.2 compilation passed. No active-project switch was used.
- Exact focused Task 6 command, counts, retained logs and review limits are in the
  [validation report](../2026-10-28-client-operations.md). No commit or push. Tasks 7–8 remain pending;
  no live UI, physical input, proxy-session or teleport-arrival evidence is claimed.
- Task 6: complete (uncommitted changes from `730096ea`; exact focused command
  `rtk proxy ./gradlew :mods:26.1.2-fabric:test --tests '*ClientWaypointAccessTest'
  --tests '*LocalTeleportCommandTest' --tests '*WaypointForm*Test' --console=plain --max-workers=2`
  passed 105 tests, zero failures/errors/skips). Final whitespace, feature links, duplicate locale
  keys, and active Stonecutter target checks passed.

## Task 6 local commit authorization (2026-10-28)

- The user requested a local commit of the completed Task 6 implementation, tests, translations,
  GUI guidance, plan status and retained validation evidence. No push is authorized.
- Reviewed the current scope and successful verification records; no implementation changes were
  made after final verification. Earlier uncommitted references describe the verification checkpoint.

## Task 7 execution (2026-10-29)

- Started clean on `feature/private-waypoint` at `48793922`; Tasks 1–6 are present. Only Task 7 is authorized. No commit or push.
- Pre-flight: Task 4 collection identity/readiness and Task 6 captured operations feed the query and scene composition; Task 5 provides UUID-backed profiles.
- Ruling: work in the existing feature checkout, retain execution artifacts in this feature directory and review inline — the user requested the next task here and the plan explicitly prohibits delegation without the user's choice. No subagents or additional worktrees. Removed the newly generated ignored root scratch directory after moving its Task 7 records here.
- Ruling: keep Task 8 live acceptance separate; automated checks cannot prove visible rendering, physical input, translated layout fluency or teleport arrival.
- RED: missing query/view/scene APIs failed compilation; translation tests failed on absent collection/profile keys. Added the scoped implementations and all six client locales.
- RED→GREEN: a real Shared waypoint download re-enabled a hidden marker; removed the remaining direct Shared render writes in favor of the source-owned scene. Shared world resync, Private tombstone, Local preservation and no Private map events now pass through real dispatch/renderer tests.
- RED→GREEN: unchanged scene refresh emitted unnecessary marker updates; retain presentation snapshots so unchanged marker instances keep their handles and avoid text/icon rebuilds.
- Compatibility inspection caught Stonecutter swap calls whose invocation arguments shared the swapped method-name line; separated method names from arguments as required by the GUI guide.
- Real add-form tests retain entered fields across explicit destination choices, reject revoked add permissions, permit an explicit Local choice after Private read revocation in the same generation, and reject retargeting after a profile change. Their missing selection API failed before implementation; the registry-backed fixture needed a test camera before running.
- Final review: inline self-review, following the plan's explicit no-delegation requirement. Reviewed equal-name stores, `WaypointList` name equality versus identity maps, source/generation/profile routing, copy cancellation and source permission checks, source-only render refreshes, integrated Shared hooks, constrained controls, popup suppression, source-status rendering, stale confirmations, locales and generated version branches. No unresolved findings.
- Task 7: complete (uncommitted changes from `48793922`; final verification command in [Task 7 report](../2026-10-29-collection-manager.md) passed: Fabric 26.1.2 full suite 1,039; Fabric 1.20.1 focused suite 175; zero failures/errors/skips. Forge 1.20.1 and NeoForge 26.3 compile. Common task up-to-date with 720 passing results).
- Ruling: retain changes on this feature branch without the branch-finishing integration menu — the user and plan explicitly require no commit/push and only Task 7 completion, so integration is outside this request. No worktree was created or removed.
- Task 8 remains pending. No live UI, physical input, translation-fluency or teleport-arrival evidence is claimed. See the report and retained logs for exact commands and boundaries.

## Local profile GUI redesign (2026-10-29)

- User requested minimalist-ui aesthetics, profile selection without remembering separators/IDs, and clearer layout guidance. Reused native theme/widgets with name-based matching, explicit Use profile, responsive selector/settings columns, independent rename/command areas, and dedicated creation/deletion dialogs.
- Kept helpers screen-owned. UUID identity is independent of labels; duplicate names and prefix collisions remain unambiguous. Saving one property retains the other field's draft. Stale screens/dialogs cannot target replacement profiles.
- RED: missing profile-choice/layout contracts failed compilation before implementation. Focused matching/geometry and actual profile-screen callback tests subsequently passed.
- Final verification: Fabric 26.1.2 full suite 1,048 tests; Fabric 1.20.1 focused 16 tests; no failures/errors/skips. Forge 1.20.1 and NeoForge 26.3 compile. Existing Gradle/dependency/Unsafe warnings remain.
- Updated six client locales and GUI APIs; inline review, whitespace, locale duplicate, feature link and Stonecutter scope/target checks passed. No commit, delegation or new worktree.
- See [redesign validation](../2026-10-29-local-profile-layout.md). In-game visual/physical-input and translation-fluency checks remain unexecuted. Task 8 remains pending.

## Config-style profile rows (2026-10-30)

- User requested imitation of ClientConfigScreen using hybrid rows. Replaced split columns with one centered settings panel, native title/button scales and section headers; labelled control/action rows handle picker/name actions and full-width rows retain command space. Guidance moved into native row tooltips.
- Preserved name-based matching, explicit switching, isolated creation/deletion and stale-selection/draft handling. Updated geometry contracts and added actual widget/action alignment and registration checks.
- Verified Fabric 26.1.2 full suite 1,049 and Fabric 1.20.1 focused 17 tests, zero failures/errors/skips; Forge 1.20.1 and NeoForge 26.3 compile. Reviewed inline, no commit; original uncommitted Task 7 changes retained.
- See [validation](../2026-10-30-profile-hybrid-rows.md). In-game visual/physical input acceptance remains pending.

## Local profile list (2026-10-30)

- The user asked to complete and improve `LocalWaypointProfilesScreen`, approved the
  [list design](../../specs/2026-10-30-local-profile-list-design.md) and chose inline execution of its
  [plan](../../plans/2026-10-30-local-profile-list.md). No commit, push, delegation or worktree.
- RED→GREEN per task: the template scanner, profile place and reason, the three widget hooks, the
  placeholder palette, the screen rewrite and command editing each failed first (logs in
  [profile-list](profile-list/)).
- Rulings are in the plan's ledger and the final report; the largest widens
  `SettingsListWidget.Row.action` to composites so a row can carry the rename and delete icons.
- Final review (one fresh reviewer): one Important finding, fixed RED→GREEN: the Rename dialog
  opened by mouse lost the name's selection. Six Minor findings are deferred and listed in the
  validation record.
- Verified after the fix: Fabric 26.1.2 full suite 1,080 tests; Fabric 1.20.1 focused 130 tests;
  zero failures, errors or skips. Forge 1.20.1, NeoForge 1.21.7 and NeoForge 26.3 compile.
- The user then asked for the six Minor findings to be fixed. Each fix failed first, and the
  coverage tests were checked by breaking the code they guard. Verified: Fabric 26.1.2 full suite
  1,086 tests; Fabric 1.20.1 focused 136 tests; zero failures, errors or skips. The loaders compile.
- See [validation](../2026-10-30-local-profile-list.md). No live in-game check ran (the user's
  decision); Task 8 remains pending.

## Manager control placement (2026-10-31)

- The user asked for better places for the manager's floating Local profile status and the Add, Copy
  and profile icons above the details panel, and approved a short in-chat design. No spec, plan,
  commit or delegation.
- Add now ends the search row, Copy waypoint is a text button at the bottom of the details panel
  while a waypoint is selected, and the status label became the Local profile button above the
  panels' left edge. The layout and button-text tests failed first (logs in
  [manager-control-placement](manager-control-placement/)).
- Verified: Fabric 26.1.2 full suite 1,094 tests and Fabric 1.20.1 full suite 1,082 tests, zero
  failures, errors or skips; all 41 mods targets compile with their tests. A live probe on the
  26.1.2 Fabric test client checked placement, tooltips and the three buttons at 427×240 and 854×480.
- See [validation](../2026-10-31-manager-control-placement.md).
- The frames showed the details panel's Collection row without its value: it reused the form's label,
  which has no argument placeholder. The user asked for the fix: a `waypoint.details.collection`
  string in all six locales. A panel text test and a translation test failed first; verified with
  Fabric 26.1.2 (1,096 tests) and Fabric 1.20.1 (1,084 tests) full suites, all 41 targets compiling
  and a live frame reading "Collection: Local".
- The user then asked for the details panel's other strings in the four locales that had none of them.
  Spanish, Hebrew, Hong Kong and Taiwan Chinese gained its 14 other strings in each locale's existing
  wording. A translation test over all 15 strings failed first; verified with Fabric 26.1.2 (1,105
  tests) and Fabric 1.20.1 (1,093 tests) full suites, counts that include another session's concurrent
  form-subtitle tests. Twelve manager control strings and one network error remain English-only there.
- Finally the user asked for the remaining strings and the removal of the unused
  `waypoint.details.none`. The four locales gained 12 manager control strings and the network error,
  worded like the server's `/wp list` translations, and the key left English and Simplified Chinese;
  all six client locales now hold the same 316 keys. The manager key, retired key and network message
  tests failed first; verified with Fabric 26.1.2 (1,110 tests) and Fabric 1.20.1 (1,098 tests).
