# Local profile list design

Date: 2026-10-30. Supersedes the
[Local profile screen layout refinement](2026-10-29-local-profile-layout-design.md).

## Intent

The user asked to complete and improve `LocalWaypointProfilesScreen` and chose four goals: a
visible profile list, clearer labels and feedback, the server or world the profiles belong to with
the reason the active one is in use, and keyboard and focus behavior on par with
`ClientConfigScreen`. They chose layout A from the 2026-10-30 mockups, one color per placeholder in
the teleport command, a confirmation before an unsaved command edit is dropped, and no live
in-game check in the plan.

Kept from the earlier rounds: the `ClientConfigScreen` style (one centered `SettingsListWidget`
panel, 1.2-scale title, section headers, row controls at `SettingsListWidget.ROW_TEXT_SCALE`, a
status/Done footer), UUID identity, captured-generation checks, drafts kept on failure, and the
storage format. No backward-compatibility code.

Success: from this one screen, keyboard alone, at a 320×240 GUI, a player sees every Local profile
for the current server or world, switches to any of them, creates, renames and deletes any of
them, and edits the active profile's teleport command.

## Layout

Top to bottom, inside the existing title/panel/footer frame:

1. **Title:** "Local waypoint profiles" (`waypoint.profile.manage`, also the manager button's
   tooltip).
2. **Header "Profiles for `<place>`"**, where the place is the server address as typed in the
   server list, or the singleplayer world's name.
3. **One row per profile**, in storage order, so rows never move when the active one changes.
   The label is the profile name, wrapping when long. The active row's control is a
   `SUCCESS`-colored text: "In use · automatic" for the `FALLBACK` and `BACKEND` reasons, "In use ·
   picked by you" for `MANUAL` (see Code changes). Every other row's control is a **Use** button.
   Every row's action is a pair of icon buttons: rename (`EDIT_ICON`) and delete (`REMOVE_ICON`,
   tinted `DANGER`), each with a tooltip naming the profile. Only the active row has a row
   tooltip, giving the longer reason (see Text); the Use buttons have their own.
4. **New profile**: a text row button under the last profile. The mockup's plus sign is dropped,
   because `TranslucentButton` draws no icons and a new widget isn't worth it.
5. **Header "Teleport command · `<active profile>`"**.
6. **Command field** at the full row width, with color-coded placeholders.
7. **Placeholder row**: the label "Placeholders: {dimension} {x} {y} {z} {yaw}" in `TEXT_MUTED`
   with each placeholder in its color (the legend), **Save** as the control, and a reset icon
   (`RESET_ICON`, config-screen style) as the action, shown only while the saved command differs
   from `LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND`. The row tooltip explains each placeholder.
8. **Footer**: the status on the left, **Done** on the right.

The section order stays fixed; with many profiles the panel scrolls like the config screen.

## Placeholder colors

The command field colors its text through a vanilla `EditBox` formatter. Colors are fixed values,
not theme roles:

| Placeholder | Color |
|---|---|
| `{x}` | `#FF7B72` |
| `{y}` | `#7EE787` |
| `{z}` | `#79B8FF` |
| `{yaw}` | `#FFC66D` |
| `{dimension}` | `#D2A8FF` |

Other text keeps the field's text color. A mistake (an unknown placeholder, an unclosed `{` to the
end of the text, a stray `}`) takes the theme's `DANGER` color and strikethrough; an underline
would land on the field's bottom outline. Bold is never used, because it changes glyph advances
and would misplace the caret. The legend uses the same colors. A disabled field draws plain text
in its disabled color.

## Behavior

- **Use** switches profiles with `LocalWaypointProfiles.select`. The command field loads the new
  profile's command. Status: "Switched to Creative." Focus moves to that row's rename button.
- **New profile** opens a dialog with an empty name field, focused. **Create** is enabled when the
  stripped name is non-empty and no other profile has it, ignoring case; a taken name shows "A
  profile named "Creative" already exists." under the field in `DANGER`, and the field outline
  turns red. Enter confirms. Creating also switches to the new profile (`create` already does).
  Status: "Created Skyblock."
- **Rename** works on any row. Its dialog opens with the current name selected; **Rename** needs a
  changed, unique, non-empty name. Status: "Renamed Creative to Builds." Renaming the active
  profile updates the command section's header.
- **Delete** works on any row. The dialog names the profile; for the active one it adds that
  another profile takes over, and it always says Shared and Private waypoints aren't affected. The
  storage's existing rules pick the replacement. Status: "Deleted Skyblock." or "Deleted Survival.
  Now using Creative." Focus moves to the row that took the deleted row's place, else New profile.
- **Save** is enabled while the field differs from the saved command and the template has no
  problem. Enter in the field saves. The reset icon restores the default and saves it at once.
  Statuses: "Teleport command saved.", "Teleport command reset to the default."
- **Problems** with the template show in the footer in `DANGER` while it's invalid: unknown
  placeholder (naming it), unclosed `{`, stray `}`, empty command, more than one leading `/`,
  line breaks or control characters. The command-length limit stays a teleport-time check.
- **Unsaved command edits** are never dropped silently. An edit is unsaved while the field differs
  from the active profile's saved command. Use on another row opens a "Discard unsaved edit"
  dialog first (Discard, Cancel); Discard switches. Done and Escape open the same dialog;
  Discard closes the screen. The New profile dialog shows a note that the edit will be discarded,
  so creating is the confirmation. Deleting the active profile drops its command with it and adds
  no extra step. Resetting with the reset icon is an explicit replacement and doesn't ask.
- **Keyboard and focus**: Tab walks the rows in order and reveals hidden rows before they take
  focus (`SettingsListWidget.revealTabTarget`, as in `ClientConfigScreen`). The New and Rename
  dialogs focus their name field; the Delete and Discard dialogs focus Cancel. Escape closes an
  open dialog first; otherwise it closes the screen, through the Discard dialog when the command
  has an unsaved edit. Closing a dialog returns focus to the control that opened it while it
  still exists. Operations update the rows in place, keeping the scroll position.
- **Out-of-date screen**: unchanged. When the Local session changes under the screen (server
  switch, disconnect, automatic backend switch), or the screen opens without a Local profile, the
  screen disables its actions and the footer says the profiles changed and to reopen the screen.
  Done and Cancel stay enabled, and Done then closes without asking.
- **Failures**: a storage `IOException` keeps the dialog and typed values and shows
  `waypoint.local.storage_failed`.

## Code changes

**`LocalWaypointProfiles`**: two read-only calls; no storage-format change.

- `place()`: the label the profiles belong to. `joinMultiplayer` records the address it receives;
  `joinSingleplayer` gains a world-name argument, which `WaypointClientMod` reads from
  `getSingleplayerServer().getWorldData().getLevelName()`. Cleared on `leave()`.
- `activeReason()`: `MANUAL` while the session's selection is manual (Use, New, deleting the
  active profile); `BACKEND` while the active profile is the binding of the backend identity the
  server reported; otherwise `FALLBACK`, the address's saved profile chosen on join.

`rename(UUID, String)` and `delete(UUID)` already accept any profile; `setTeleportCommand` keeps
targeting the active one.

**`LocalTeleportCommand`**: one scanner serves `expand` (unchanged results and exceptions) and
two new read-only calls: `tokens(template)` returns ordered spans (plain text, a placeholder and
its name, or a mistake), and `problem(template)` returns the first problem's kind and text, or
none. The screen uses `problem` for Save and the footer instead of expanding a sample waypoint.

**Widgets** (documented in `docs/tips/gui/local-guide.md`):

- `SuggestingTextInput.setTextFormatter(...)` installs a formatter that receives the shown
  substring and its offset in the value. It holds the one Stonecutter split: vanilla
  `setFormatter` below 1.21.9 (checked in the 1.21.7 NeoForge sources), `addFormatter` from
  1.21.9. While the input isn't editable it returns plain text.
- `IconButton.withIconColor(WidgetThemeVariable)` tints an active icon with a theme role; a
  disabled icon keeps the disabled tint.

**`LocalWaypointProfilesScreen`**, rewritten:

- Rows are built from `profiles.profiles()`. Each profile's controls are created once and cached
  by UUID, so focus and hover survive updates; widgets of removed profiles are unregistered and
  new ones registered without rebuilding the screen.
- A screen-owned style maps `LocalTeleportCommand.tokens` to the palette and `DANGER`
  strikethrough, splitting correctly when the caret cuts a placeholder.
- Dialogs: New, Rename, Delete and Discard, built on `ConfirmationDialog`; the name field is
  registered explicitly, as today, because `ConfirmationDialog.visitWidgets` visits only buttons.
  One dialog field serves New and Rename.
- Name rules (non-empty after stripping, at most 128 characters, unique ignoring case and
  surrounding spaces) live in a small screen-owned helper; storage keeps accepting any non-blank
  name, so automatically created profiles are unaffected.
- `ProfileChoices`, the combobox, and the separate name and remove sections are removed.
  `ProfileLayout` keeps placing the title, panel and footer.

**Locales**: all six client locales get the new keys. Retired keys are removed from every locale
and added to `WaypointFormTranslationTest`'s retired list; new labels join its no-trailing-colon
check.

## Text (English)

| Use | Text |
|---|---|
| Title | Local waypoint profiles |
| List header | Profiles for %s |
| Active status | In use · automatic / In use · picked by you |
| Reason tooltip, `FALLBACK` | Picked automatically: the profile last used here. |
| Reason tooltip, `BACKEND` | Picked automatically for the world you're in. |
| Reason tooltip, `MANUAL` | You picked this profile. It stays in use until you leave and opens again next time. |
| Use / tooltip | Use / Switch to %s and show its Local waypoints. |
| Icon tooltips | Rename %s / Delete %s |
| Command header | Teleport command · %s |
| Legend | Placeholders: %s (the colored placeholder list) |
| Legend tooltip | One line each: {x} {y} {z} the waypoint's block position, {yaw} the direction it faces, {dimension} its dimension, such as minecraft:overworld |
| Save / reset tooltip | Save / Reset to the default: %s |
| New dialog | New profile · Starts with no Local waypoints and becomes the profile in use. · Create |
| Rename dialog | Rename profile · New name for "%s" · Rename |
| Delete dialog | Delete profile · Delete "%s" and its Local waypoints? This can't be undone. · You're using this profile, so another one takes over. · Shared and Private waypoints aren't affected. · Delete |
| Discard dialog | Unsaved teleport command · You changed the teleport command for %s but didn't save it. Discard the change? · Discard |
| Discard note in New | Your unsaved teleport command edit will be discarded. |
| Name taken | A profile named "%s" already exists. |
| Statuses | Switched to %s. · Created %s. · Renamed %s to %s. · Deleted %s. · Deleted %s. Now using %s. · Teleport command saved. · Teleport command reset to the default. |
| Out of date | Local profiles changed outside this screen. Reopen it. |
| Problems | Unknown placeholder: %s · A placeholder is missing its closing }. · Found } without an opening {. · Enter a command. · Start the command with at most one /. · Commands can't contain line breaks or control characters. |

Exact key names are settled in the implementation plan.

## Testing

- `LocalTeleportCommandTest`: `tokens` and `problem` for every problem kind, placeholders next to
  each other and at the ends, and unchanged `expand` results and exceptions.
- Formatter: each placeholder's color, plain text between them, a placeholder split by the caret,
  mistakes in `DANGER` with strikethrough, and plain text when not editable.
- `LocalWaypointProfilesTest`: `place()` for multiplayer and singleplayer and after `leave()`;
  `activeReason()` through join (`FALLBACK`), backend identity (`BACKEND`), select, create and
  deleting the active profile (`MANUAL`).
- Screen interaction tests on real profile storage, in the existing fixture style: Use; renaming
  and deleting a non-active profile without switching; deleting the active profile and its status;
  duplicate and empty names; Save, Enter and reset; the Discard dialog from Use, Done and Escape
  with both answers; the New dialog's discard note; focus after each operation; out-of-date
  screens disabling actions.
- Geometry at 320×240 and 480×270: aligned control and action columns, the footer inside the
  window, long names wrapping, one registration per widget.
- `WaypointFormTranslationTest`: new keys and arguments in all six locales, retired keys gone.
- Verification: the full `26.1.2-fabric` suite, focused `1.20.1-fabric` tests, and compiling
  `1.20.1-forge`, `1.21.7-neoforge` (the formatter split) and `26.3-neoforge`.

## Out of scope

- A live in-game check (the user's decision for this round).
- Editing a non-active profile's teleport command; switch to it first.
- Searching or filtering the list, and per-profile waypoint counts.
- Unique names enforced by storage, and the names of automatically created profiles.
- Refreshing in place when the session changes under the screen.
