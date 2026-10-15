# Theme editor validation — 2026-10-10

Task 11 validates the implementation and the final review fixes on the working tree based on
`11802379`. It includes the remaining M2 fix: a pending ARGB commit updates the value and enables
Save/Reset immediately, while status-driven layout waits until the current click finishes. Both
Stonecutter `mouseClicked` branches use the same dispatch guard. The existing fixes and the adjacent
suggestion-alignment work were preserved. This round is uncommitted.

## Automated checks

All commands ran from the repository, through `rtk proxy`. The existing Gradle wrapper helper uses
`GRADLE_USER_HOME=/Volumes/ssd/gradle_home`, two workers and the configured JDK installations.

```sh
zsh .superpowers/gw.sh :mods:26.1.2-fabric:test \
    --tests '*WidgetThemeConfigScreenClickTest'

zsh .superpowers/gw.sh \
    :mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test \
    :mods:1.20.1-fabric:cleanTest :mods:1.20.1-fabric:test
```

The focused regression was first run with immediate status layout and failed on the bottom-edge
click. After deferring layout, all three tests passed: the real `IconButton` hit box and callback
remain usable through the commit, status changes outside a click lay out immediately, and an
exception flushes the pending layout without leaving later events deferred. Unit tests bypass the
click sound because it requires a running client; the native checks below dispatch real screen clicks.
The initial green attempt exposed that sound dependency in the test fixture, which was corrected.

| Target | Classes | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| 26.1.2 Fabric | 133 | 842 | 0 | 0 | 0 |
| 1.20.1 Fabric | 131 | 834 | 0 | 0 | 0 |

The full run ended `BUILD SUCCESSFUL in 7s`, with 22 actionable tasks. Counts were summed from
the fresh XML reports, rather than inferred from Gradle's exit code. See the
[full output](2026-10-10-theme-editor/automated/task11-tests.log),
[test totals](2026-10-10-theme-editor/automated/test-counts.json),
[red regression output](2026-10-10-theme-editor/automated/m2-red.log) and
[green regression output](2026-10-10-theme-editor/automated/m2-green.log).

Covering tests include `WidgetThemeConfigScreenClickTest`, `ColorHexCodeFieldTest` (selection replacement
in RGB and ARGB modes, six-digit commit and alpha preservation), `WidgetThemeConfigScreenMarkerTest`
(panel visibility), `WidgetThemeEditorSessionTest` (opening preview, Cancel, failed-save value retention
and retry), `WidgetThemeTranslationTest` (all six locales and removed-key scans),
`WidgetThemeEditorLayoutTest`, `WidgetThemeConfigScreenKeyListTest`, `KeySelectionTest`,
`PreviewPackingTest`, `PreviewSampleTest`, `WidgetThemePreviewTest`, `TextChoiceDropdownTest` and the
`SettingsListLayout`/`SettingsListWidget` tests. Relevant XML reports are retained under
[26.1.2](2026-10-10-theme-editor/automated/26.1.2-fabric/) and
[1.20.1](2026-10-10-theme-editor/automated/1.20.1-fabric/).

### All 40 compilation targets

The target list was generated from every `mods/versions/*/gradle.properties`. The command ran
`zsh .superpowers/gw.sh --continue`, followed by `:mods:<target>:compileJava` and
`:mods:<target>:compileTestJava` for each target. The complete argument list is retained in
[matrix-command.txt](2026-10-10-theme-editor/automated/task11-matrix-command.txt).

Result: `BUILD SUCCESSFUL in 1m 20s`, 319 actionable tasks (263 executed, 56 up-to-date).
All 40 production compilation tasks executed; 38 test compilation tasks executed and two were
up-to-date. Each of the 80 requested tasks was checked against the
[output](2026-10-10-theme-editor/automated/task11-matrix.log); the per-target outcomes are in
[matrix-results.json](2026-10-10-theme-editor/automated/matrix-results.json).

| Minecraft versions | Loaders compiled |
| --- | --- |
| 1.20.1 | Fabric, Forge |
| 1.20.2, 1.20.4, 1.20.6 | Fabric, Forge, NeoForge |
| 1.21 | Fabric, Forge, NeoForge |
| 1.21.2 | Fabric, NeoForge |
| 1.21.3, 1.21.5, 1.21.6 | Fabric, Forge, NeoForge |
| 1.21.7 | NeoForge |
| 1.21.9, 1.21.11, 26.1.2, 26.2 | Fabric, Forge, NeoForge |
| 26.3 | Fabric, NeoForge |

The active Stonecutter target stayed `26.1.2-fabric`. No swaps or replacements were added.

### Whitespace and Stonecutter markers

`git diff --check` passed. Each new text file was also checked with
`git diff --no-index --check /dev/null <file>`; a status of 1 with no diagnostic is the expected
result for a nonempty new file. Marker counts and ordering were checked in all 32 Java files touched
since the theme-editor plan, including the adjacent suggestion-alignment commit and the two new
untracked test files. Closed and inline branches balance, replacement tokens stay first, and there
are no tabs. The [marker inventory](2026-10-10-theme-editor/automated/task11-markers.json) records
each file. The successful 40-target compile additionally checks both input API branches.

## Native client checks

The existing `tools/live-game-test` preparation, attach agent, hash validation, diagnostics and owned
process shutdown were reused with a theme-editor-specific scratch probe. Fresh production JARs were
built and loaded with Fabric API into isolated game directories for installed `26.1.2-Fabric` and
`1.20.1-Fabric` profiles. The editor opened directly from a title-screen parent. These are running
LWJGL clients with native framebuffer captures; `java.awt.headless=true` disables AWT, not rendering.
The probe calls the actual screen input handlers on the render thread, uses native mouse coordinates
for hover captures, and observes actual widget/session state and persisted JSON.

The complete pass (`theme-live-5`) passed all six steps on both clients and exited normally with no
crash reports or unexpected errors. Additional popup/hover passes are recorded below. Assertions and
JAR/probe hashes are retained in
[26.1.2 native results](2026-10-10-theme-editor/native/26.1.2-Fabric/result.json) and
[1.20.1 native results](2026-10-10-theme-editor/native/1.20.1-Fabric/result.json).

| Spec checklist | Executed outcome on both versions |
| --- | --- |
| Three sizes × four built-in themes | 480×270, 378×245 and 320×240 were asserted as actual GUI dimensions. All 24 captures were inspected. Footer and key editor fit; values appear at 480 and disappear at both smaller sizes. Narrow preview rows clip vertically and scroll. |
| ARGB, alpha slider, color picker | Select-all and character events replaced the full ARGB value; the responder updated the live theme. Setting alpha to 128 preserved RGB. Picker confirmation changed RGB to AABBCC and preserved alpha 80. The rendered Custom state and wrapped message were captured. |
| Pending edits then Save/Reset | Clicks on the original bottom pixel of initially inactive Save (378×245) and Reset (320×240) committed six digits and performed the button action on that same event. Save persisted 123456; Reset restored the opening theme and cleared dirty state. |
| Cancel and failed Save | Cancel restored the live opening theme. Replacing the isolated theme-file path with a directory caused a real save failure; the screen and FF224466 draft remained usable, and removing the obstruction allowed retry to persist that draft. |
| Opening after a hand edit | A deliberately differing file selection was previewed on open; Cancel restored the prior live Classic theme. |
| Markers and focus ring | Focus-ring, screen-background and panel-background captures show the appropriate outlines. The scrolled-out text field loses its marker; partially visible controls can still have a clipped marker. The key-list focus ring is visible inside its marker. |
| Clear selection and keyboard | Clicking the selected row cleared the key, removed usage markers and showed the empty editor. Sixteen subsequent Tab events skipped hidden ARGB input. Tab reached the key list; Down/Up changed the selected raw key. |
| Preview popups | Combobox and choice-dropdown popups rendered over other samples. Popup wheel events were consumed before preview scrolling; choices changed and closed their popup. Resize closed an open preview popup. Opening the picker closed the preview popup. |
| Modal picker | Underlying controls were inactive, modal focus survived Tab and resize to 320×240, and Escape closed the picker without dirtying the theme and restored color-button focus. |
| Tooltips and wheel | Key-list and preview wheel events changed their scroll positions. Hover screenshots inspect the theme tooltip, absence of key-row/preview tooltips, and absence of tooltips through the picker; see the additional capture pass below. |

### Screenshot evidence

The linked JPEG contact sheets contain resized native captures with labels added outside the captured
image. They were visually inspected. Original PNGs remain in the scratch run directories.

| Capture group | 26.1.2 Fabric | 1.20.1 Fabric |
| --- | --- | --- |
| 480×270, four themes | [Layouts](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/480x270.jpg) | [Layouts](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/480x270.jpg) |
| 378×245, four themes | [Layouts](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/378x245.jpg) | [Layouts](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/378x245.jpg) |
| 320×240, four themes | [Layouts](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/320x240.jpg) | [Layouts](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/320x240.jpg) |
| Focus and usage markers | [Markers](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/markers.jpg) | [Markers](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/markers.jpg) |
| Wrapped status, empty editor, popups | [Interactions](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/interactions.jpg) | [Interactions](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/interactions.jpg) |
| Tooltips and modal suppression | [Tooltips](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/tooltips.jpg) | [Tooltips](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/tooltips.jpg) |
| Picker after keyboard input and resize | [Picker](2026-10-10-theme-editor/screenshots/26.1.2-Fabric/picker-resized.jpg) | [Picker](2026-10-10-theme-editor/screenshots/1.20.1-Fabric/picker-resized.jpg) |

### Additional popup and hover capture pass

The focused pass (`theme-live-7`) passed on both versions. It additionally opened a combobox popup in
the narrow overflowing preview, sent the wheel outside that popup into the preview, and asserted that
the popup closed. It also repeated popup choice/priority, resize, modal focus and Escape checks. See
the [26.1.2 result](2026-10-10-theme-editor/native/26.1.2-Fabric/popup-hover/result.json),
[1.20.1 result](2026-10-10-theme-editor/native/1.20.1-Fabric/popup-hover/result.json) and
[output](2026-10-10-theme-editor/native/theme-live-7.log).

The 26.1.2 tooltip captures show the selector's tooltip and no tooltips over key rows, preview controls
or through the modal picker. The 1.20.1 selector capture still missed hover, so it was repeated in a
focused legacy pass (`theme-live-8`) that held the mouse position through successive rendered frames
and asserted both the native coordinates and the selector's actual `isHovered()` state. That pass
passed, and its screenshots show the same tooltip/suppression behavior. See the
[legacy hover assertions](2026-10-10-theme-editor/native/1.20.1-Fabric/held-hover/01-tooltips.result),
[result](2026-10-10-theme-editor/native/1.20.1-Fabric/held-hover/result.json) and
[output](2026-10-10-theme-editor/native/theme-live-8.log). All final linked screenshots were inspected.

### Diagnostic classification

The complete native pass reported 45 warnings on 26.1.2 and 134 on 1.20.1, chiefly existing Fabric/Mixin
compatibility-level and optional integration warnings. The diagnostic records preserve them rather
than treating a successful interaction pass as a warning-free launch. `MixinEnvironment.audit` passed
on both versions. Xaero/VoxelMap integrations were absent from these core editor profiles.

The expected failed-save log is retained and classified by the deliberate obstruction and successful
retry. Offline test identities also produced inspected HTTP 401 authentication/user-properties errors,
Realms token parsing, or feature-flag network errors. Only those specific expected messages were
excluded from the unexpected-error count. There were no unexpected errors or crash reports in the
complete pass.

### What was not exercised

No physical OS mouse/keyboard sequence, multiplayer session, in-world backdrop, optional map integration,
or Forge/NeoForge live editor session was exercised. Native clients were launched sequentially with
JDK 25 for 26.1.2 and JDK 17 for 1.20.1, as recorded by the launch results. Slider values and picker
confirmation were driven through their actual widget APIs/callbacks, rather than a continuous physical
thumb drag or every swatch. English was used for captures; all six locale resources were tested by
the translation suite. The initial selector tooltip capture missed its intended hover state and was
repeated with a deterministic native mouse-move callback. Earlier scratch attempts stopped on probe
assumptions about Tab's return value or which part of a combobox opens the popup, not failed production
assertions.

Scratch evidence root:
`/private/tmp/claude-501/-Volumes-ssd-fabric-mods-repo-server-waypoint/44c0b42f-f8ff-4ce2-87c8-d4334802674d/scratchpad/`.
It retains the full logs, source probe (`ThemeChecks.java`), adapter (`theme-live.py`), isolated games,
native PNGs and earlier attempts. The portable evidence copied beside this record is sufficient to
inspect the automated results and representative captures without those game installations.

## Spec errata and deferred rulings

The approved design body is preserved. Its status/errata note now links this record: values begin at a
205-pixel key column, and `PreviewSample` declarations follow actual drawing and reachable states,
including the panel visibility exceptions. M4 remains deferred by ruling (focus can briefly remain on
a reset control that hides or deactivates); M8 remains the specified reset-icon behavior after a theme
switch. Neither was expanded into this fix round.
