# Waypoint form validation

How the [form design](../specs/2026-09-29-waypoint-form-design.md) was checked, following its Validation section. The [implementation plan](../plans/2026-09-29-waypoint-form.md) says how it was built.

## Automated checks

Every Gradle command sets `GRADLE_USER_HOME=/Volumes/ssd/gradle_home` and, for 26.x targets, `-Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`. Record the date and the result of each row, and note any target that was not run.

| Check | Command | Date | Result |
| --- | --- | --- | --- |
| Common unit tests | `./gradlew :common:test` | | |
| Mods unit tests, active target | `./gradlew :mods:26.1.2-fabric:test` | | |
| Compile 1.20.1 Fabric | `./gradlew :mods:1.20.1-fabric:compileJava --offline` | | |
| Compile 1.20.1 Forge | `./gradlew :mods:1.20.1-forge:compileJava` | | |
| Compile 1.21.6 Fabric, the first version with `setTooltipForNextFrame` | `./gradlew :mods:1.21.6-fabric:compileJava --offline` | | |
| Compile 26.3 Fabric | `./gradlew :mods:26.3-fabric:compileJava --offline` | | |
| Compile 26.3 NeoForge | `./gradlew :mods:26.3-neoforge:compileJava` | | |
| No whitespace errors | `git diff --check` | | |
| Stonecutter markers balanced in every touched Java file | marker check from the plan | | |

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

Record anything that differed from the plan here.
