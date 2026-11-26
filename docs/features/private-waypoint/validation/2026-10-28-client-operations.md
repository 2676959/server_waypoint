# Task 6: client operation routing and teleport templates

Date: 2026-10-28. Base: `730096ea`, branch `feature/private-waypoint`.
Task 6 is implemented and verified by automated tests. Local commit authorized on 2026-10-28; no push.

## Delivered behavior

`ClientWaypointAccess` routes operations through captured source, generation, identifiers and Local
profile UUID. Local add/edit/remove and list property/removal operations save through the serialized
profile owner and report persistence failures. They send no waypoint command or custom message.
Shared/Private mutations retain server authority: property-edit futures accept only matching
scope/request/generation/dimension/list/previous-waypoint results. Rejected, obsolete, cancelled and
failed-send operations cannot become optimistic cache changes. Add/remove/list commands report
`AWAITING_SERVER`; the add form retains its bounded synchronization wait and normal command errors
remain visible in server chat.

`copyDraft` detaches every supported property. `addCopy` rechecks source read and destination add
access; opening/cancelling creates nothing. The source and destination remain unchanged on name,
metadata, permission or quota rejection. Shared/Private addition accepts an optional `display_name`
tail after metadata and any icon. This keeps the display override inside the existing atomic
permission/quota-checked addition rather than following creation with a second edit. Existing
identifier rules are preserved separately from the 256-character display-text limit.

Add/edit/base forms use explicit access and selection APIs. Validation, list/name suggestions,
expected edit revisions and pending completion refer to the captured collection. Stale forms keep
entered text and cannot submit into a replacement profile/connection. Copy forms preserve deliberately
empty display overrides. Error results still highlight their relevant fields. Dimension choices
combine vanilla connection level keys with the captured store, without waypoint suggestion requests.

`LocalTeleportCommand.expand` accepts a leading slash and substitutes only `{dimension}`, `{x}`,
`{y}`, `{z}` and `{yaw}`. It rejects blank commands, control characters and unknown/unbalanced tokens.
Submission rechecks the live profile/selection, target, connection, dimension rule and packet length
(256 before 1.20.5; 32767 thereafter). Cross-dimension targets require `{dimension}`. Invalid templates,
missing dimension tokens and excessive commands have typed, localized explanations. A successful
send is `COMMAND_SUBMITTED`, never evidence that a teleport succeeded.

The default Nether fixture under a German locale produces exactly:

```text
execute in minecraft:the_nether run tp @s -12 64 30 90 ~
```

GUI APIs are documented in the [GUI guide](../../../tips/gui/local-guide.md). All six client locales
contain the new statuses. Human translation review remains unexecuted.

## Executed checks

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test --console=plain --max-workers=2

rtk proxy ./gradlew :mods:26.1.2-fabric:test \
    --tests '*ClientWaypointAccessTest' --tests '*LocalTeleportCommandTest' \
    --tests '*WaypointForm*Test' --console=plain --max-workers=2

rtk proxy ./gradlew :mods:1.20.1-fabric:test \
    --tests '*ClientWaypointAccessTest' --tests '*LocalTeleportCommandTest' \
    --tests '*WaypointForm*Test' :mods:1.20.1-forge:compileJava \
    :mods:26.3-neoforge:compileJava :paper:26.2-paper:compileJava \
    --console=plain --max-workers=2
```

Full suites passed: Common 720 and active Fabric 1022. The exact active Task 6 focused run passed
105 tests. Older Fabric focused tests passed: 102.
Zero failures/errors/skips. Forge 1.20.1, NeoForge 26.3 and Paper 26.2 compilation passed. Active
targets remain `26.1.2-fabric` and `26.2-paper`. Java 17 compatibility is covered by the older targets.
`git diff --check`, feature-document link checks, duplicate locale-key checks, and touched Java
Stonecutter marker balance checks passed. Existing loader,
map-integration, Gradle and Unsafe-fixture warnings remain visible in the logs.

Coverage includes isolated mutations with duplicate identifiers, complete detached metadata, copy
cancellation, invalid metadata, name collisions, source/destination permission changes, actual server
quota refusal, failed persistence and retry, stale profiles/connections/results, runtime Shared/Private
result dispatch, send failure, real form draft retention, translated error coverage, named templates,
locale independence, cross-dimension rejection, and command-length boundaries.

The initial contract tests failed on absent APIs. Later assertion failures demonstrated missing
connection dimensions and applying display-text limits to identifiers; both passed after fixes.
Initial command fixture and constructor compilation errors are retained as diagnostic history, not
claimed as product regressions. Review was performed inline because the plan excludes delegation
unless the user chooses it; no unresolved findings remain.

## Evidence and proof limits

- [Full suites](execution/task-6/full.log)
- [Exact Task 6 focused command](execution/task-6/task-tests.log)
- [Older target checks](execution/task-6/versions.log)
- [Machine-readable full/older counts](execution/task-6/test-counts.json)
- [Initial contract failure](execution/task-6/red.log)
- [Identifier regression](execution/task-6/identifier-red.log)
- [Dimension catalog regression](execution/task-6/dimensions-red.log)
- [Copy permission contract](execution/task-6/copy-permission-red.log)
- [Teleport explanation contract](execution/task-6/teleport-reasons-red.log)
- [Execution ledger](execution/progress.md)

The work stays in the existing feature checkout with no publication, schema migration or
compatibility reader. The optional add-command tail is the only server grammar extension. Manager
collection/destination/profile controls, Copy buttons and composed rendering remain Task 7. Actual
UI layout/input, real multiplayer permissions, command execution and teleport arrival remain Task 8.
No live Minecraft session was run for this task.
