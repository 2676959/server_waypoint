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
3. Pending: private commands and permissions.
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
