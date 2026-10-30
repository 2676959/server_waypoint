# Local profile list

Date: 2026-10-30. Implements the [Local profile list design](../specs/2026-10-30-local-profile-list-design.md)
through its [implementation plan](../plans/2026-10-30-local-profile-list.md), executed inline in the
feature checkout. Nothing is committed.

`LocalWaypointProfilesScreen` now lists every Local profile of the current server or world in
storage order, with Use, Rename and Delete on each row, New profile, the reason the active profile is
in use, and the active profile's teleport command with color-coded placeholders. Use, Done and
Escape ask before an unsaved command edit is dropped.

## Changes

- `LocalTeleportCommand`: `tokens` and `problem` from one scanner that `expand` also uses; `expand`
  keeps its results and exception messages.
- `LocalWaypointProfiles`: `place()`, `activeReason()` and the world name passed to
  `joinSingleplayer`, which `WaypointClientMod` reads from the integrated server's world data. The
  storage format is unchanged.
- Widgets: `SuggestingTextInput.setTextFormatter` (vanilla `setFormatter` below 1.21.9,
  `addFormatter` from 1.21.9), `IconButton.withIconColor`, `ConfirmationDialog.getConfirmButton`,
  `SettingsListWidget.getRowWidth()`, and `SettingsListWidget.Row.action` accepting a composite such
  as a `WidgetStack` of icon buttons. All are documented in the
  [GUI guide](../../../tips/gui/local-guide.md).
- `TeleportCommandStyle`: one fixed color per placeholder, `DANGER` with strikethrough for mistakes,
  and the colored legend.
- Locales: 39 keys added, 4 changed and 23 retired in all six client locales. The retired keys are
  in `WaypointFormTranslationTest`'s retired list.

## Verification

`GW` is the plan's Gradle wrapper (SSD Gradle home, JDK 25 toolchain path, `--max-workers=2`).

```sh
GW :mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test
GW :mods:1.20.1-fabric:test --tests '*LocalWaypointProfiles*' --tests '*LocalTeleportCommandTest' \
    --tests '*TeleportCommandStyleTest' --tests '*SuggestingTextInputTest' --tests '*IconButtonTest' \
    --tests '*ConfirmationDialogTest' --tests '*WaypointFormTranslationTest' --tests '*ClientWaypointAccessTest' \
    --tests '*SettingsListWidgetTest'
GW :mods:1.20.1-forge:compileJava :mods:1.21.7-neoforge:compileJava :mods:26.3-neoforge:compileJava
```

- Fabric 26.1.2 full suite: **1,086 tests** in 158 classes, zero failures, errors or skips
  ([log](execution/profile-list/minors-full-26.1.2-fabric.log)).
- Fabric 1.20.1 focused tests: **136 tests** in 11 classes, zero failures, errors or skips
  ([log](execution/profile-list/minors-focused-1.20.1-fabric.log)).
- Forge 1.20.1, NeoForge 1.21.7 (the `setFormatter` branch) and NeoForge 26.3 compile
  ([log](execution/profile-list/minors-loader-compile.log)).
- These are the runs after the review fixes below. Before them the suites passed 1,079 and 129
  tests ([full](execution/profile-list/task-7-full-26.1.2-fabric.log),
  [focused](execution/profile-list/task-7-focused-1.20.1-fabric.log),
  [loaders](execution/profile-list/task-7-loader-compile.log)), and after the Important fix 1,080
  and 130 ([full](execution/profile-list/final-fix-green-26.1.2-fabric.log),
  [focused](execution/profile-list/final-fix-green-1.20.1-fabric.log)).
- Each task's failing and passing runs are kept as `task-N-red.log` and `task-N-green*.log` in
  [execution/profile-list](execution/profile-list/).
- Tree checks: no tabs or trailing whitespace in the touched files, balanced Stonecutter markers,
  no duplicate keys in the six locales, and `mods/stonecutter.gradle.kts` still names
  `26.1.2-fabric` as active.

## Review focus

| Input | Test |
|---|---|
| A 128-character name with no spaces at 320×240 and 480×270 | `rowsKeepTheirControlsInsideThePanel` |
| Duplicate names already in storage | `duplicateNamesInStorageStaySeparateRows` |
| Deleting the only profile | `deletingTheOnlyProfileSwitchesToANewDefault` |
| The session ending while a dialog is open | `aSessionEndingUnderADialogLeavesOnlyCancelAndDone` |
| A scrolled field whose shown text starts inside a placeholder | `aScrolledFieldColorsThePlaceholderItStartsInside` |

## Final review

One fresh reviewer read the whole change: no Critical findings, one Important, six Minor.

- Fixed: opened with the mouse, the Rename dialog lost the current name's selection, because
  vanilla focuses the clicked icon after its callback and the name field clears its selection when
  it loses focus. The selection is now applied again after the focus hand-over;
  `renameKeepsTheWholeNameSelectedAfterTheClickHandsFocusOver` failed first
  ([log](execution/profile-list/final-fix-red.log)).
- The six Minor findings were then fixed at the user's request
  ([failing run](execution/profile-list/minors-red.log)):
  - Discard switches first, so a switch that fails keeps the dialog and the edit
    (`aDiscardWhoseSwitchFailsKeepsTheEditAndTheDialog`).
  - A template problem stays in the footer after another operation
    (`aTemplateProblemOutlastsAnotherOperationsStatus`).
  - A delete whose files can't be removed after the index dropped the profile closes its dialog and
    shows the rows as storage has them (`aDeleteThatFailsAfterStorageDroppedTheProfileFollowsStorage`).
  - Without a place or an active profile, the headers read "Profiles" and "Teleport command"
    (`aScreenWithoutALocalProfileNamesNoPlaceOrProfile`).
  - The es/zh reset tooltip and status now use each locale's config-screen wording. That is copy
    only, with no test.
  - Test gaps: registration order through the package-private `start()`, a stale close with an
    active profile, focus after deleting the active profile, after a reset and after Discard then
    Cancel, and labels that wrap with `TestFont.wrapping()`. These passed on the existing code;
    breaking the code each one guards made each fail
    ([log](execution/profile-list/minors-mutations.log)).
- Real mouse routing stays untested: the base screen's `mouseClicked` needs the game's key bindings,
  so tests replay a click as `onClick`, `setFocused` and `applyPendingFocus()`.

## Not verified

No live in-game check ran, by the user's decision for this round. Pixel layout, text wrapping in
each locale, tooltip placement, IME input, real mouse and keyboard input, and translation fluency
remain unverified. The feature's Task 8 live acceptance is still pending.
