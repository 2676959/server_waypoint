# Waypoint form validation

How the [form design](../specs/2026-09-29-waypoint-form-design.md) was checked, following its Validation section. The [implementation plan](../plans/2026-09-29-waypoint-form.md) says how it was built.

## Automated checks

Every Gradle command sets `GRADLE_USER_HOME=/Volumes/ssd/gradle_home` and, for 26.x targets, `-Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`. All checks below ran on 2026-09-30, after the final code fixes. Both test tasks were forced to rerun together. All commands also supplied the JDK 25 path shown above. No target in the requested matrix was skipped; the active target stayed `26.1.2-fabric`. The SSD had at least 5.2 GiB free throughout the final builds, above the 1.5 GB floor.

| Check | Command | Date | Result |
| --- | --- | --- | --- |
| Common unit tests | `./gradlew :common:test --rerun` | 2026-09-30 | PASS — 589 tests, no failures or skips |
| Mods unit tests, active target | `./gradlew :mods:26.1.2-fabric:test --rerun` | 2026-09-30 | PASS — 501 tests, no failures or skips |
| Compile 1.20.1 Fabric | `./gradlew :mods:1.20.1-fabric:compileJava --offline` | 2026-09-30 | PASS |
| Compile 1.20.1 Forge | `./gradlew :mods:1.20.1-forge:compileJava` | 2026-09-30 | PASS — 10 existing deprecation/removal warnings |
| Compile 1.21.6 Fabric, the first version with `setTooltipForNextFrame` | `./gradlew :mods:1.21.6-fabric:compileJava --offline` | 2026-09-30 | PASS |
| Compile 26.3 Fabric | `./gradlew :mods:26.3-fabric:compileJava --offline` | 2026-09-30 | PASS — existing Xaero deprecation warning |
| Compile 26.3 NeoForge | `./gradlew :mods:26.3-neoforge:compileJava` | 2026-09-30 | PASS — existing Xaero deprecation warning |
| No whitespace errors | `git diff --check 9b4f5409` | 2026-09-30 | PASS |
| Stonecutter markers balanced in every touched Java file | marker check from the plan over Java files changed since `9b4f5409` | 2026-09-30 | PASS — 6 files |

## In game

A manual pass on 26.1.2 Fabric. Compiling can't prove these. Tick a box only after seeing it.

### Sizes

- [ ] Add at 480×270, 378×245 and 320×240 with a one-line status.
- [ ] Edit at the same three sizes.
- [ ] A status that wraps, such as Spanish at 320×240: the gap between rows shrinks, the footer grows and nothing crosses the screen edge.
- [ ] A long list display name, waypoint name and dimension in the Edit header: one line each, cut with "…".

### Add

- [ ] From the manager's + button: the dimension is the selected one, List is empty and focused, Name is empty.
- [ ] From a list row: List is filled in and Name is focused.
- [ ] From Xaero's World Map: the position comes from the map.
- [ ] Add is inactive, with a hint in the footer, until the dimension, list and name are filled in. An existing name shows an error and a danger outline on Name.
- [ ] Add closes when the waypoint appears, in singleplayer and on a dedicated server.
- [ ] When the server refuses, for example without permission, "Adding…" locks the form and after 5 seconds it unlocks with "The server didn't add the waypoint. Check the chat."
- [ ] A new list name shows the note "Adding creates the list …" and doesn't block.
- [ ] List is a combobox: its arrow opens the lists of the chosen dimension, picking one fills the field, and changing Dimension swaps the choices and keeps what's typed. More than eight lists scroll.
- [ ] The keywords and the description reach the waypoint, as the manager's details show.

### Edit

- [ ] Save with each field changed.
- [ ] Save and Reset are inactive until something changes. Reset restores every field and clears the message and the highlights.
- [ ] A name collision highlights Name, an invalid display name highlights Display name and duplicate keywords highlight Keywords.
- [ ] A permission error shows in the footer.
- [ ] A waypoint with an empty display-name override: the title names the waypoint, Display name shows "Empty: the marker shows no name" and saving untouched changes nothing.
- [ ] The subtitle shows "In <list> · <dimension>" with the list's display name, never blank.

### Keyboard, focus and tooltips

- [ ] Tab order follows the screen on both screens and ends with the footer buttons.
- [ ] Enter in a text field sends the form when Add or Save is active. While a suggestion list is open it takes the highlighted suggestion instead.
- [ ] Escape closes a popup, then leaves the field, then closes the screen. With the color picker open it closes the picker and focus returns to the color button.
- [ ] First focus: List, or Name once List is filled, on Add; nothing on Edit, where the movement keys move the player.
- [ ] Resizing the window keeps the values, the focus, the message and a pending Add or Save, including during "Adding…" and while a field has focus.
- [ ] With the color picker open every other control is inactive.
- [ ] Tooltips appear after about 500 ms on a label and on its controls, with their own text for Initials, Visibility and Yaw. None shows over the remove-icon button, which has its own "Remove icon" tooltip, while a popup or the picker is open, or while a request is pending.
- [ ] Initials follow the name until you type other initials. On Edit they follow only when the saved initials were the default.

### Themes

- [ ] Translucent Dark, Modern Dark and High Contrast: text, placeholders, the danger outline and the footer message are readable.

## Notes

- The final fresh-context code review found two defects in the prescribed Task 11 code. Text length limits now precede saved-value loading, preserving names, display names, keywords and descriptions longer than vanilla's default 32 characters. Form locks also change text editability on activation transitions, because 1.20.1 accepts typing in a focused field even when `active` is false.
- `WaypointFormStateTest` exercises the real shared constructor with long saved text and the real text input path with the legacy activation predicate. Both regressions failed before their fixes and passed afterward. `WaypointFormTextTest` also failed before the header helper was implemented and passed afterward.
- The final count is 501 mods tests rather than the plan's approximate estimate. There were no failures or skipped tests in either verified suite. Gradle and JOML emitted existing deprecation warnings.
- After the initial no-commit instruction, the user explicitly requested step-by-step local commits. Tasks 11, 12 and 13 are committed separately. Whitespace and marker checks cover changes since the starting commit, including already committed files.
- Every in-game checkbox remains unchecked. No live game, visual, focus, timing, Xaero, singleplayer or dedicated-server pass was performed. Compilation and unit tests do not establish these outcomes; complete the checklist above before release.
- The non-English form translations from Tasks 1–10 remain machine drafts requiring native-speaker review before release.
