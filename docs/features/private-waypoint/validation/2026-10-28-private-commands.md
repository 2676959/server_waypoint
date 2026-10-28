# Private commands validation — 2026-10-28

Task 3 is implemented and verified with automated tests. Live acceptance remains pending.

## Implemented behavior

- `/wp private` reuses the Shared command grammar against the authenticated player's own store.
  It includes help, list creation, full waypoint properties, browsing, property edits, removal,
  restore, navigation, and teleport. The ordinary menu exposes Private when permitted.
- Dedicated permission nodes are `server_waypoint.command.private.list`, `.add`, `.edit`,
  `.remove`, `.navigate`, and `.tp`. Corresponding `CommandPermission` fields are `privateList`,
  `privateAdd`, `privateEdit`, `privateRemove`, `privateNavigate`, and `privateTp`. Teleport defaults
  to level 2; the other five default to 0. Paper leaves these nodes unset by default, preserving
  configurable level fallback and explicit permission-provider grants.
- No-player sources fail before storage access. Privileged execute-as uses its executing player's
  UUID and retains the original source for platform commander feedback.
- Quotas and persistence use the owner gate. Failed saves cannot send durable success. Restore
  tokens retain ten-minute expiry, owner/collection identity, retry after failed restoration, and
  single-use consumption after a successful save under the owner gate.
- Private feedback links retain `/wp private`. Navigation links use the actual target's collection
  and permission even when the panel is opened through the other prefix. Private changes produce
  no Shared client update or player broadcast. Owner-only synchronization belongs to Task 4.

## Executed verification

```sh
rtk proxy ./gradlew :common:test --tests '*CoreWaypointCommand*Test' --tests '*StringCommandBuilderTest' --tests '*WaypointRestoreRegistryTest' --tests '*Platform*Permission*Test' --tests '*HelpScreenTest' --console=plain --max-workers=2
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:compileJava :paper:26.2-paper:test --console=plain --max-workers=2
rtk git diff --check
```

All succeeded. The final common suite ran 706 tests; Paper ran 58 tests, with zero failures,
errors, or skips. Fabric 26.1.2 compilation succeeded. Permission adapter fixtures also exercised
Fabric, Forge, NeoForge, modern Paper, and the existing legacy Paper branch. These fixtures are
not a substitute for loading every platform/version. Active Stonecutter targets remain unchanged.

New command tests first failed on the absent subtree. Regression tests reproduced denied-list
feedback, navigation prefix crossover, and private teleport links using Shared permission; the
final suite passed after their fixes. Persistence tests verify that the post-save commit hook
holds the owner gate and is skipped on save failure.

All six common locales include the new strings. Coverage and chat-layout tests passed; fluent
human-language review was not performed. Runtime dependency warnings about native access,
Unsafe, and Gradle deprecations were present without test failures.

## Boundaries

No live vanilla-client, multiplayer privacy, physical-input, rendering, or successful teleport
arrival checks were executed. No whole-version build matrix or publication was performed.
Tasks 4–8 and their live acceptance checklist remain pending. The final diff was self-reviewed;
no commit or push was made.

See the [execution ledger](execution/progress.md) and [implementation plan](../plans/2026-10-16-private-waypoint.md).

## Command performance follow-up

The subsequent [performance refactor](2026-10-28-command-performance.md) moves bounded option
validation ahead of scheduling/storage, preserves Brigadier error cursors, avoids owner-store loads
for bounded completion, and removes repeated descendant permission lookups during command copying.
It adds complete-tree budgets and native copy/packet evidence. Its final common suite has 711 tests;
the earlier 706-test result above records the initial Task 3 implementation.
