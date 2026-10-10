# Theme Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `WidgetThemeConfigScreen` as the developer-tool theme editor of layout C: a raw key list with
values, a key editor with an ARGB field, and a full-height live preview that marks where the selected key is
used.

**Architecture:** Pure, unit-tested helpers in `client.gui.screens` decide geometry, selection, sample keys and
packing. Two small widget extensions add a label-less settings row and an ARGB mode to the hex field. The screen
composes existing widgets: a `TreeViewWidget` key list, a fixed key editor, and a package-private
`WidgetThemePreview` built on `SettingsListWidget`.

**Tech Stack:** Java 17 sources, the Minecraft client GUI across Stonecutter targets 1.20.1–26.3 (active target
`26.1.2-fabric`), JUnit 5, and GSON in the translation tests.

**Spec:** [`docs/features/theme-editor/specs/2026-10-09-theme-editor-design.md`](../specs/2026-10-09-theme-editor-design.md)

## Global Constraints

- Java 17 features only; four-space indentation, never tabs (`AGENTS.md`).
- **Don't commit.** `AGENTS.md` allows commits only when the user asks; tasks end with a status check instead.
  Other sessions share this checkout: run `git branch --show-current` (expect `4.0.0`) and `git status --short`
  before each task, and never revert a change you didn't make.
- Every Stonecutter target from 1.20.1 to 26.3 must compile. Keep `//? if` branches balanced and keep
  `//~ gui_graphics_26` as each file's first line where it exists. No new swap or replacement.
- No backward-compatibility code: removed keys and `WidgetThemeEditorSession.reset()` simply go.
- Theme colors are resolved at draw time through `WidgetThemeManager.getColor`; never cache them.
- Fixed, non-theme colors allowed by the spec: the marker `0xFFFF4FD8`, and the chip checkerboard
  `0xFF9A9A9A` / `0xFF5E5E5E` (a transparency visualization, like picker gradients).
- Sizes from the spec: margin 10, header 11, section gap 6, panel gap 2, panel padding 6, group
  `min(W − 20, 420)`, left column `clamp(round(group × 0.5), 152, 210)`, panels `min(space, 230)`, key editor 53,
  key row 12, preview sample gap 6, family gap 12, wide row `max(17, h + 6)`, marker 1 px at 2 px outside.
- Mods lang files use Minecraft `%s` placeholders (the `MessageFormat` rule in `AGENTS.md` is for `common`).
- **Running Gradle.** Bash shells here don't load `~/.zshrc`, and the rtk hook shifts shell variables, so create
  this git-ignored wrapper once (`.superpowers/` is in `.git/info/exclude`) and call it with literal paths:

  ```zsh
  #!/bin/zsh
  # /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh
  export GRADLE_USER_HOME=/Volumes/ssd/gradle_home
  cd /Volumes/ssd/fabric_mods_repo/server_waypoint || exit 1
  ./gradlew --max-workers=2 -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home "$@"
  ```

  `--tests X` binds only to the task just before it. Check `df -h /System/Volumes/Data /Volumes/ssd` before
  compiling targets that weren't built recently, and don't run a game client at the same time.

## Review Focus

1. Committing a value the key already has (focus loss, Enter, an unchanged picker confirm) must not turn a
   built-in theme into Custom or make Save active. Pinned by `WidgetThemeEditorSessionTest.settingTheColorTheDraftAlreadyHasChangesNothing` (Task 3).
2. Pasting a value copied from `widget-theme.json` (`#D9262626`) into the ARGB field must work despite the `#`.
   Pinned by `ColorHexCodeFieldTest.aPastedValueMayStartWithAHash` (Task 2).
3. A failed save must leave the editor usable for another attempt with the values intact. Pinned by
   `WidgetThemeEditorSessionTest.aFailedSaveLeavesTheSessionOpen` (Task 3).
4. A sample wider than the preview (the 112-px combobox at a narrow width) must get a row of its own without
   empty rows or a lost sample. Pinned by `PreviewPackingTest.aSampleWiderThanTheRowGetsARowOfItsOwn` (Task 6).
5. A long translated dropdown label at 320 px must leave the title a width of zero, never a negative one. Pinned
   by `WidgetThemeEditorLayoutTest.aDropdownWiderThanTheGroupLeavesTheTitleNoWidth` (Task 4).

---

## File Structure

| File | Change | Responsibility |
| --- | --- | --- |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayout.java` | Modify | `wideRowHeight` and its constants |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java` | Modify | `WideRow`; ownership and Tab helpers move from `Row` to `Entry` |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java` | Modify | ARGB mode, `commit()`, `#` stripping |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeEditorSession.java` | Modify | `setColor` result, `revert`, `revertAll`, `isChanged`; `reset()` removed |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeEditorLayout.java` | Create | Pure geometry of section 1 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/KeySelection.java` | Create | Click and Up/Down selection rules |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/PreviewSample.java` | Create | Samples, families, marked elements and their keys |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/PreviewPacking.java` | Create | Packs families into rows |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/TextChoiceDropdown.java` | Create | Text dropdown drawing shared by the theme selector and the preview's dropdown |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemePreview.java` | Create | The preview panel: samples, rows, markers, popups |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreen.java` | Rewrite | Header, key list, key editor, footer, state, input, rendering |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java` | Modify | `scrollFocusedPopup` becomes `protected` |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java` | Modify | `isShiftDown` becomes package-private |
| `mods/src/main/resources/assets/server_waypoint/lang/{en_us,es_es,he_il,zh_cn,zh_hk,zh_tw}.json` | Modify | Section 8 keys |
| Tests under `mods/src/test/java/_959/server_waypoint/common/client/gui/` | Create/Modify | Per task; `screens/WidgetThemeConfigScreenLayoutTest.java` is deleted |
| `docs/tips/gui/local-guide.md`, `CHANGELOG.md`, `docs/features/theme-editor/` | Modify/Create | Section 9 |

---

### Task 1: `SettingsListWidget.WideRow`

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayout.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayoutTest.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidgetTest.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidgetTabTest.java`
- Docs: `docs/tips/gui/local-guide.md` ("Settings lists" and the component table)

**Interfaces:**
- Produces: `SettingsListLayout.WIDE_ROW_VERTICAL_PADDING = 3`, `SettingsListLayout.MIN_WIDE_ROW_HEIGHT = 17`,
  `public static int SettingsListLayout.wideRowHeight(int controlHeight)`.
- Produces: `public static final class SettingsListWidget.WideRow extends Entry` with
  `public <C extends LayoutElement & Renderable> WideRow(C control)`.
- Produces (package-private): `Entry.owns(GuiEventListener)`, `Entry.isInteractive()`,
  `Entry.showsWidget(AbstractWidget)` (moved up from `Row`; `Row` overrides `showsWidget` for its conditional
  action) and `static boolean SettingsListWidget.highlightsOnHover(Entry)`.

- [ ] **Step 1: Create the Gradle wrapper** from Global Constraints at
  `/Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh` (not committed; skip if it exists).

- [ ] **Step 2: Write the failing tests**

```java
// SettingsListLayoutTest
@Test
void wideRowsKeepThreePixelsAboveAndBelowAndAtLeastSeventeen() {
    assertEquals(17, SettingsListLayout.wideRowHeight(9));
    assertEquals(17, SettingsListLayout.wideRowHeight(11));
    assertEquals(20, SettingsListLayout.wideRowHeight(14));
    assertEquals(26, SettingsListLayout.wideRowHeight(20));
}
```

```java
// SettingsListWidgetTest (composite(...) and button(...) are the file's existing helpers)
@Test
void aWideRowOwnsEveryWidgetItsControlVisitsAndIsATabStopWhileOneIsActive() {
    IconButton first = button(true);
    IconButton second = button(false);
    SettingsListWidget.WideRow row = new SettingsListWidget.WideRow(composite(first, second));
    assertTrue(row.owns(first));
    assertTrue(row.owns(second));
    assertTrue(row.isInteractive());
    first.active = false;
    assertFalse(row.isInteractive());
}

@Test
void aWideRowIsItsControlPlusPaddingAndHidesWidgetsOutOfView() {
    IconButton top = button(true);
    IconButton bottom = button(true);
    SettingsListWidget list = new SettingsListWidget(new TestFont());
    list.setWidth(200);
    list.setHeight(19); // one row: a 13-pixel button plus 3 above and below
    list.setEntries(List.of(new SettingsListWidget.WideRow(composite(top)),
            new SettingsListWidget.WideRow(composite(bottom))));
    assertEquals(38, list.getContentHeight());
    assertTrue(top.visible);
    assertFalse(bottom.visible);
    list.setScrollY(19);
    assertFalse(top.visible);
    assertTrue(bottom.visible);
}

@Test
void aWideRowStartsItsControlAtTheRowsLeftEdgeAndCentersIt() {
    WidgetStack control = composite(button(true));
    SettingsListWidget list = new SettingsListWidget(new TestFont());
    list.setWidth(200);
    list.setHeight(40);
    list.setX(30);
    list.setY(50);
    list.setEntries(List.of(new SettingsListWidget.WideRow(control)));
    assertEquals(30, control.getX());
    assertEquals(53, control.getY()); // 50 + (19 − 13) / 2
}

@Test
void onlyLabelledRowsHighlightOnHover() {
    assertTrue(SettingsListWidget.highlightsOnHover(row(button(true), null)));
    assertFalse(SettingsListWidget.highlightsOnHover(new SettingsListWidget.WideRow(composite(button(true)))));
    assertFalse(SettingsListWidget.highlightsOnHover(new SettingsListWidget.Header(Component.literal("Title"))));
}
```

```java
// SettingsListWidgetTabTest: give TestScreen a boolean wideRows constructor argument (the existing
// no-argument constructor passes false). With true, each button goes in a WideRow over a one-button
// WidgetStack added with addClickable, and the list is 2 × 19 pixels high.
@Test
void tabFromDoneWrapsAroundToTheFirstWideRowWhileTheListIsScrolledToTheEnd() {
    TestScreen screen = new TestScreen(true);
    screen.list.setScrollY(screen.list.getMaxScroll());
    screen.setFocused(screen.done);

    screen.pressTab(true);

    assertSame(screen.rows[0], screen.getFocused());
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*SettingsList*'`
Expected: compilation fails: `wideRowHeight`, `WideRow` and `highlightsOnHover` don't exist.

- [ ] **Step 4: Implement**
  - `SettingsListLayout.wideRowHeight(controlHeight)` returns
    `max(MIN_WIDE_ROW_HEIGHT, controlHeight + 2 * WIDE_ROW_VERTICAL_PADDING)`.
  - Move `owns`, `isInteractive`, `showsWidget` and the private `widgets()` from `Row` to `Entry` (built on
    `visitWidgets`; `showsWidget` returns true by default). `rowIndexOf`, `interactiveRowBefore`,
    `interactiveRowAfter` and `revealTabTarget` use `Entry` instead of `instanceof Row` and the `Row` cast.
  - `highlightsOnHover(entry)` is `entry instanceof Row`; the render loop fills `ROW_HOVER_BACKGROUND` and tracks
    the tooltip row only when it's true.
  - `Entry` becomes `sealed ... permits Header, Row, WideRow`; update its Javadoc.
  - `WideRow`: `layout` builds a `WidgetPack(0, 0, rowWidth, height, HORIZONTAL)` with `CrossAxisAlignment.CENTER`
    and the control added `FORWARD`, where `height = wideRowHeight(VisualPositioning.getVisualHeight(control))`;
    `position` moves the pack and sets each visited widget's `visible` with the same `fullyVisible` rule as
    `Row`; `renderEntry` calls `renderPart` on the control; `preferredWidth` is the control's visual width;
    `visitWidgets` delegates to the control.

- [ ] **Step 5: Run the tests to see them pass**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*SettingsList*'`
Expected: BUILD SUCCESSFUL; the existing `SettingsList*` tests still pass.

- [ ] **Step 6: Document** in the guide's "Settings lists": `WideRow` (no label column, `wideRowHeight`, no hover
  fill or tooltip, same visibility and Tab rules), and replace "row controls can't open popups" with "unless the
  screen renders the popup separately and closes it when the list scrolls". Add `WideRow` to the component
  table's settings-rows line.

- [ ] **Step 7: Check the tree** with `git status --short`: only this task's files changed.

### Task 2: `ColorHexCodeField` ARGB mode

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeFieldTest.java`
- Docs: `docs/tips/gui/local-guide.md` (the `ColorHexCodeField` notes near the component table)

**Interfaces:**
- Produces: `public static ColorHexCodeField argb(int x, int y, Component text, Font textRenderer)`: 8 digits,
  placeholder `AARRGGBB`, content width 51 (48 for eight 6-px digits plus 3, as the RGB field's 39 is 36 + 3).
- Produces: `public void commit()`; `setFocused(false)` calls it.
- Changes: in ARGB mode `getColor()` returns ARGB (eight digits parsed; otherwise the last color set) and
  `setColor(int)` stores the color and shows `String.format("%08X", argb)`. `insertText` strips one leading `#`
  in both modes. `charTyped` stops at the mode's digit count. RGB mode is otherwise unchanged.

- [ ] **Step 1: Write the failing tests**

```java
class ColorHexCodeFieldTest {
    private static ColorHexCodeField argb() {
        return ColorHexCodeField.argb(0, 0, Component.literal("Color"), new TestFont());
    }

    @Test
    void argbModeShowsAndReturnsAllEightDigits() {
        ColorHexCodeField field = argb();
        field.setColor(0xD9262626);
        assertEquals("D9262626", field.getValue());
        assertEquals(0xD9262626, field.getColor());
    }

    @Test
    void argbModeReadsAnEightDigitValue() {
        ColorHexCodeField field = argb();
        field.setValue("B31C1C1C");
        assertEquals(0xB31C1C1C, field.getColor());
    }

    @Test
    void committingSixDigitsKeepsTheCurrentAlpha() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        field.setValue("FF0000");
        field.commit();
        assertEquals("B3FF0000", field.getValue());
        assertEquals(0xB3FF0000, field.getColor());
    }

    @Test
    void committingAnIncompleteValueRestoresTheCurrentColor() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        field.setValue("D92");
        field.commit();
        assertEquals("B31C1C1C", field.getValue());
    }

    @Test
    void aPastedValueMayStartWithAHash() {
        ColorHexCodeField field = argb();
        field.setValue("");
        field.insertText("#D9262626");
        assertEquals("D9262626", field.getValue());
    }

    @Test
    void rgbModeStillPadsShortValuesToSixDigits() {
        ColorHexCodeField field = new ColorHexCodeField(0, 0, Component.literal("RGB"), new TestFont());
        field.setValue("12");
        field.commit();
        assertEquals("000012", field.getValue());
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*ColorHexCodeFieldTest'`
Expected: compilation fails: `argb` and `commit` don't exist.

- [ ] **Step 3: Implement** the interface above in `ColorHexCodeField`. Keep the mode in a `private final boolean
  alpha` field set by a private constructor that both the public constructor and `argb` call. Parse with
  `Integer.parseUnsignedInt(value, 16)`; keep formatting and parsing in the widget (`ColorUtils` lives in the
  server-side `common` project).

- [ ] **Step 4: Run the tests to see them pass** (same command). Expected: 6 tests pass.

- [ ] **Step 5: Document** the ARGB mode (`argb`, eight digits, `commit()` rules, `#` stripping) next to the
  guide's existing `ColorHexCodeField` notes.

- [ ] **Step 6: Check the tree** with `git status --short`.

### Task 3: Editor session

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeEditorSession.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreen.java`
  (`resetTheme()` calls `revertAll()` until Task 8 rewrites the screen)
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeEditorSessionTest.java`

**Interfaces:**
- Produces: `Optional<WidgetThemeSelection> setColor(WidgetThemeVariable variable, int color)`: empty and no change
  when the draft already has `color`; otherwise today's behavior, returning the built-in selection that was active
  when its colors replaced Custom colors that differ from it in any key.
- Produces: `Optional<WidgetThemeSelection> revert(WidgetThemeVariable variable)`: `setColor(variable,
  originalSettings.theme().getColor(variable))`.
- Produces: `void revertAll()`: `settings = originalSettings` and `WidgetThemeManager.setTheme(getDraftTheme())`.
- Produces: `boolean isChanged(WidgetThemeVariable variable)`: the draft's color differs from
  `originalSettings.theme()`'s.
- Removes: `void reset()`.

- [ ] **Step 1: Write the failing tests** (replace `resetPreviewsDefaultsAndCancelRestoresTheOpeningTheme`, which
  uses `reset()`, with the first test below)

```java
@Test
void cancelRestoresTheOpeningThemeAndIsIdempotent() {
    WidgetTheme original = WidgetThemes.MODERN_DARK.withColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xFF010203);
    WidgetThemeManager.setTheme(original);
    WidgetThemeEditorSession session = new WidgetThemeEditorSession(original, this.tempDirectory.resolve("widget-theme.json"));
    session.select(WidgetThemeSelection.HIGH_CONTRAST);
    session.cancel();
    session.cancel();
    assertEquals(original, WidgetThemeManager.getTheme());
}

@Test
void settingTheColorTheDraftAlreadyHasChangesNothing() {
    WidgetThemeEditorSession session = session(WidgetThemeSelection.MODERN_DARK, WidgetThemes.TRANSLUCENT_DARK);
    int accent = WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.ACCENT);
    assertEquals(Optional.empty(), session.setColor(WidgetThemeVariable.ACCENT, accent));
    assertEquals(WidgetThemeSelection.MODERN_DARK, session.getSelection());
    assertFalse(session.isDirty());
}

@Test
void editingABuiltInThemeReportsItWhenTheCustomColorsDiffer() {
    WidgetThemeEditorSession session = session(WidgetThemeSelection.MODERN_DARK, WidgetThemes.TRANSLUCENT_DARK);
    assertEquals(Optional.of(WidgetThemeSelection.MODERN_DARK), session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456));
    assertEquals(WidgetThemeSelection.CUSTOM, session.getSelection());
    assertEquals(0xFF123456, session.getDraftTheme().getColor(WidgetThemeVariable.ACCENT));
    assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.PANEL_BACKGROUND),
            session.getDraftTheme().getColor(WidgetThemeVariable.PANEL_BACKGROUND));
}

@Test
void editingABuiltInThemeWhoseColorsMatchCustomReportsNothing() {
    WidgetThemeEditorSession session = session(WidgetThemeSelection.MODERN_DARK, WidgetThemes.MODERN_DARK);
    assertEquals(Optional.empty(), session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456));
}

@Test
void editingCustomReportsNothing() {
    WidgetThemeEditorSession session = session(WidgetThemeSelection.CUSTOM, WidgetThemes.MODERN_DARK);
    assertEquals(Optional.empty(), session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456));
}

@Test
void revertRestoresOneKeyFromTheOpeningTheme() {
    WidgetThemeEditorSession session = session(WidgetThemeSelection.CUSTOM, WidgetThemes.MODERN_DARK);
    session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456);
    session.setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xCC112233);
    session.revert(WidgetThemeVariable.ACCENT);
    assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.ACCENT),
            session.getDraftTheme().getColor(WidgetThemeVariable.ACCENT));
    assertFalse(session.isChanged(WidgetThemeVariable.ACCENT));
    assertTrue(session.isChanged(WidgetThemeVariable.PANEL_BACKGROUND));
}

@Test
void revertAllRestoresTheSelectionAndTheCustomColors() {
    WidgetThemeEditorSession session = session(WidgetThemeSelection.CUSTOM, WidgetThemes.MODERN_DARK);
    session.select(WidgetThemeSelection.HIGH_CONTRAST);
    session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456);
    session.revertAll();
    assertEquals(WidgetThemeSelection.CUSTOM, session.getSelection());
    assertEquals(WidgetThemes.MODERN_DARK, session.getDraftTheme());
    assertEquals(WidgetThemes.MODERN_DARK, WidgetThemeManager.getTheme());
    assertFalse(session.isDirty());
}

@Test
void aFailedSaveLeavesTheSessionOpen() throws IOException {
    Path blocker = this.tempDirectory.resolve("blocker");
    Files.writeString(blocker, "not a directory");
    WidgetThemeEditorSession session = new WidgetThemeEditorSession(WidgetThemes.MODERN_DARK, blocker.resolve("widget-theme.json"));
    session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456);
    assertThrows(IOException.class, session::save);
    session.setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xCC112233);
    assertTrue(session.isDirty());
}

// helper in the test class
private WidgetThemeEditorSession session(WidgetThemeSelection selection, WidgetTheme custom) {
    WidgetThemeJson.Settings settings = new WidgetThemeJson.Settings(selection, custom);
    return new WidgetThemeEditorSession(settings.theme(), this.tempDirectory.resolve("widget-theme.json"), settings);
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*WidgetThemeEditorSessionTest'`
Expected: compilation fails: `setColor` returns `void`, `revert`, `revertAll` and `isChanged` don't exist.

- [ ] **Step 3: Implement** the interface above, delete `reset()`, and change `WidgetThemeConfigScreen.resetTheme()`
  to call `session.revertAll()` so the module compiles.

- [ ] **Step 4: Run the tests to see them pass** (same command). Expected: every session test passes.

- [ ] **Step 5: Check the tree** with `git status --short`.

### Task 4: `WidgetThemeEditorLayout`

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeEditorLayout.java`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeEditorLayoutTest.java`

**Interfaces:**
- Produces (package-private `final class`, in the style of `WaypointFormLayout`):
  - Constants: `SCREEN_MARGIN = 10`, `SECTION_GAP = 6`, `PANEL_GAP = 2`, `PANEL_PADDING = 6`, `HEADER_HEIGHT = 11`,
    `TITLE_GAP = 8`, `MAX_GROUP_WIDTH = 420`, `MIN_LEFT_WIDTH = 152`, `MAX_LEFT_WIDTH = 210`,
    `MAX_PANELS_HEIGHT = 230`, `EDITOR_HEIGHT = 53`, `KEY_ROW_HEIGHT = 12`, `MIN_KEY_ROWS = 4`,
    `MIN_PREVIEW_HEIGHT = 40`, `STATUS_GAP = 8`, `STATUS_LINE_GAP = 4`, `KEY_X = 14`, `VALUE_GAP = 8`,
    `VALUE_INSET = 2`.
  - `record Rect(int x, int y, int width, int height)` with `int right()` and `int bottom()`.
  - `record Arrangement(Rect group, Rect title, Rect dropdown, Rect keyList, Rect editor, Rect preview, Rect buttons,
    Rect status, boolean statusAbove)`. The three panel rects are the panels' visual (outer) bounds.
  - `static Arrangement arrange(int screenWidth, int screenHeight, int dropdownWidth, int buttonsWidth,
    int buttonsHeight, IntUnaryOperator statusHeight)`; `statusHeight.applyAsInt(maxWidth)` is 0 without a status.
  - `static int leftColumnWidth(int groupWidth)` and
    `static boolean showsValues(int rowWidth, int widestKeyWidth, int valueWidth)`
    (`KEY_X + widestKeyWidth + VALUE_GAP + valueWidth + VALUE_INSET <= rowWidth`).
- Rules: `statusAbove = ClientConfigScreen.statusAboveButtons(group.width, buttonsWidth)`; the status is
  `group.width` wide above the buttons, else `group.width − buttonsWidth − STATUS_GAP`; footer height is
  `status + STATUS_LINE_GAP + buttons` when above and a status shows, else `max(buttons, status)`; panels height is
  `min(MAX_PANELS_HEIGHT, screenHeight − 2·margin − header − 2·gap − footer)` but at least
  `EDITOR_HEIGHT + PANEL_GAP + MIN_KEY_ROWS·KEY_ROW_HEIGHT + 2·PANEL_PADDING` (115, which also covers the preview's
  40); the group is centered horizontally and at `max(SCREEN_MARGIN, (screenHeight − groupHeight) / 2)`; the title
  is `max(0, group.width − dropdownWidth − TITLE_GAP)` wide; the dropdown ends at `group.right()`; the buttons end at
  `group.right()` at the footer's bottom; the key list fills the left column above the editor.

- [ ] **Step 1: Write the failing tests** (`NO_STATUS = width -> 0`, dropdown 120, buttons 162 × 11 unless noted)

```java
@Test
void groupColumnsAndPanelsMatchTheSpecAt480By270() {
    WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 270, 120, 162, 11, NO_STATUS);
    assertEquals(new WidgetThemeEditorLayout.Rect(30, 10, 420, 250), a.group());
    assertEquals(new WidgetThemeEditorLayout.Rect(30, 27, 210, 161), a.keyList());
    assertEquals(new WidgetThemeEditorLayout.Rect(30, 190, 210, 53), a.editor());
    assertEquals(new WidgetThemeEditorLayout.Rect(242, 27, 208, 216), a.preview());
    assertEquals(new WidgetThemeEditorLayout.Rect(288, 249, 162, 11), a.buttons());
}

@Test
void columnsMatchTheSpecAt378By245And320By240() {
    WidgetThemeEditorLayout.Arrangement retina = WidgetThemeEditorLayout.arrange(378, 245, 120, 162, 11, NO_STATUS);
    assertEquals(179, retina.keyList().width());
    assertEquals(177, retina.preview().width());
    assertEquals(191, retina.preview().height());
    WidgetThemeEditorLayout.Arrangement minimum = WidgetThemeEditorLayout.arrange(320, 240, 120, 162, 11, NO_STATUS);
    assertEquals(152, minimum.keyList().width());
    assertEquals(146, minimum.preview().width());
    assertEquals(186, minimum.preview().height());
}

@Test
void theEditorSitsTwoPixelsUnderTheListAndEndsWithThePreview() {
    WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(378, 245, 120, 162, 11, NO_STATUS);
    assertEquals(a.keyList().bottom() + 2, a.editor().y());
    assertEquals(a.preview().bottom(), a.editor().bottom());
    assertEquals(a.keyList().right() + 2, a.preview().x());
}

@Test
void titleLeavesTheDropdownItsWidthAndAnEightPixelGap() {
    WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 270, 120, 162, 11, NO_STATUS);
    assertEquals(292, a.title().width());
    assertEquals(a.group().right() - 120, a.dropdown().x());
}

@Test
void aDropdownWiderThanTheGroupLeavesTheTitleNoWidth() {
    assertEquals(0, WidgetThemeEditorLayout.arrange(320, 240, 330, 162, 11, NO_STATUS).title().width());
}

@Test
void statusSitsBesideTheButtonsWithRoomAndAboveThemOtherwise() {
    WidgetThemeEditorLayout.Arrangement beside = WidgetThemeEditorLayout.arrange(320, 240, 120, 162, 11, width -> 9);
    assertFalse(beside.statusAbove());
    assertEquals(130, beside.status().width());
    WidgetThemeEditorLayout.Arrangement above = WidgetThemeEditorLayout.arrange(320, 240, 120, 200, 11, width -> 18);
    assertTrue(above.statusAbove());
    assertEquals(300, above.status().width());
    assertEquals(164, above.preview().height()); // 240 − 20 − 11 − 12 − (18 + 4 + 11)
}

@Test
void panelsStopAt230AndTheGroupCentersOnTallScreens() {
    WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 400, 120, 162, 11, NO_STATUS);
    assertEquals(230, a.preview().height());
    assertEquals(68, a.group().y());
}

@Test
void aShortScreenKeepsTheMinimumsAndStartsAtTheTopMargin() {
    WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(320, 150, 120, 162, 11, NO_STATUS);
    assertEquals(115, a.preview().height());
    assertEquals(10, a.group().y());
}

@Test
void leftColumnIsHalfTheGroupBetween152And210() {
    assertEquals(210, WidgetThemeEditorLayout.leftColumnWidth(420));
    assertEquals(179, WidgetThemeEditorLayout.leftColumnWidth(358));
    assertEquals(152, WidgetThemeEditorLayout.leftColumnWidth(300));
}

@Test
void valuesShowOnlyWhenTheWidestKeyAndAValueFit() {
    assertTrue(WidgetThemeEditorLayout.showsValues(190, 120, 46));
    assertFalse(WidgetThemeEditorLayout.showsValues(189, 120, 46));
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*WidgetThemeEditorLayoutTest'`
Expected: compilation fails: `WidgetThemeEditorLayout` doesn't exist.

- [ ] **Step 3: Implement** the class from the interface and rules above.

- [ ] **Step 4: Run the tests to see them pass** (same command). Expected: 10 tests pass.

- [ ] **Step 5: Check the tree** with `git status --short`.

### Task 5: `KeySelection`

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/KeySelection.java`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/KeySelectionTest.java`

**Interfaces:**
- Produces (package-private `final class`):
  - `static @Nullable WidgetThemeVariable click(@Nullable WidgetThemeVariable selected, WidgetThemeVariable clicked)`:
    `null` when `clicked == selected`, else `clicked`.
  - `static WidgetThemeVariable move(@Nullable WidgetThemeVariable selected, boolean down)`: the next or previous
    constant of `WidgetThemeVariable.values()`, clamped at both ends; from `null`, the first (down) or last (up).

- [ ] **Step 1: Write the failing tests**

```java
@Test
void aClickSelectsAKey() {
    assertEquals(WidgetThemeVariable.ACCENT, KeySelection.click(WidgetThemeVariable.TEXT_PRIMARY, WidgetThemeVariable.ACCENT));
    assertEquals(WidgetThemeVariable.ACCENT, KeySelection.click(null, WidgetThemeVariable.ACCENT));
}

@Test
void aClickOnTheSelectedKeyClearsTheSelection() {
    assertNull(KeySelection.click(WidgetThemeVariable.ACCENT, WidgetThemeVariable.ACCENT));
}

@Test
void upAndDownMoveOneKeyAndStopAtTheEnds() {
    assertEquals(WidgetThemeVariable.TEXT_MUTED, KeySelection.move(WidgetThemeVariable.TEXT_PRIMARY, true));
    assertEquals(WidgetThemeVariable.TEXT_PRIMARY, KeySelection.move(WidgetThemeVariable.TEXT_PRIMARY, false));
    assertEquals(WidgetThemeVariable.DANGER_BACKGROUND, KeySelection.move(WidgetThemeVariable.DANGER_BACKGROUND, true));
}

@Test
void withNothingSelectedDownStartsAtTheFirstKeyAndUpAtTheLast() {
    assertEquals(WidgetThemeVariable.TEXT_PRIMARY, KeySelection.move(null, true));
    assertEquals(WidgetThemeVariable.DANGER_BACKGROUND, KeySelection.move(null, false));
}
```

- [ ] **Step 2: Run to see them fail**: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*KeySelectionTest'`. Expected: compilation fails.
- [ ] **Step 3: Implement** the class.
- [ ] **Step 4: Run to see them pass** (same command). Expected: 4 tests pass.
- [ ] **Step 5: Check the tree** with `git status --short`.

### Task 6: `PreviewSample` and `PreviewPacking`

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/PreviewSample.java`
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/PreviewPacking.java`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/PreviewSampleTest.java`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/PreviewPackingTest.java`

**Interfaces:**
- Produces `enum PreviewSample` (package-private) with nested `enum Family { TEXT, FIELD, CHOICES, BUTTONS, TOGGLES,
  SLIDERS, ACCENT, POPUPS, STATUS, SCROLLBARS }`, `@Nullable Family family()`, `Set<WidgetThemeVariable> keys()`,
  `boolean uses(WidgetThemeVariable key)` and `static List<PreviewSample> samplesOf(Family family)` (declaration
  order). Constants, in this order, with the keys of the spec's sample and element tables:

  | Constant | Family | Keys |
  | --- | --- | --- |
  | `PRIMARY_TEXT`, `MUTED_TEXT`, `DISABLED_TEXT` | `TEXT` | `TEXT_PRIMARY` / `TEXT_MUTED` / `TEXT_DISABLED` |
  | `TEXT_FIELD` | `FIELD` | `CONTROL_BACKGROUND`, `TEXT_PLACEHOLDER`, `TEXT_PRIMARY`, `BORDER`, `FOCUS_RING` |
  | `COMBOBOX` | `CHOICES` | `CONTROL_BACKGROUND`, `TEXT_PRIMARY`, `BORDER`, `FOCUS_RING`, `POPUP_BACKGROUND`, `ROW_HOVER_BACKGROUND` |
  | `DROPDOWN` | `CHOICES` | `POPUP_BACKGROUND`, `TEXT_PRIMARY`, `BORDER`, `ROW_HOVER_BACKGROUND` |
  | `BUTTON` | `BUTTONS` | `CONTROL_BACKGROUND`, `CONTROL_HOVER_BACKGROUND`, `TEXT_PRIMARY`, `BORDER`, `FOCUS_RING` |
  | `DISABLED_BUTTON` | `BUTTONS` | `CONTROL_DISABLED_BACKGROUND`, `TEXT_DISABLED` |
  | `SELECTED_TOGGLE`, `ON_TOGGLE`, `OFF_TOGGLE` | `TOGGLES` | `CONTROL_SELECTED_BACKGROUND` / `SUCCESS_BACKGROUND` / `DANGER_BACKGROUND`, each with `TEXT_ON_ACCENT` |
  | `SLIDER` | `SLIDERS` | `ACCENT`, `CONTROL_BACKGROUND` |
  | `DISABLED_SLIDER` | `SLIDERS` | `SLIDER_THUMB_DISABLED`, `CONTROL_DISABLED_BACKGROUND`, `TEXT_DISABLED` |
  | `ACCENT_CHIP`, `HOVERED_ACCENT_CHIP` | `ACCENT` | `ACCENT` / `ACCENT_HOVER`, each with `TEXT_ON_ACCENT` |
  | `TOOLTIP` | `POPUPS` | `POPUP_BACKGROUND`, `BORDER`, `TEXT_PRIMARY` |
  | `POPUP_CHIP`, `DIALOG_CHIP` | `POPUPS` | `POPUP_BACKGROUND` / `DIALOG_BACKGROUND` |
  | `SUCCESS_CHIP`, `WARNING_CHIP`, `DANGER_CHIP` | `STATUS` | `SUCCESS` + `SUCCESS_BACKGROUND` / `WARNING` + `WARNING_BACKGROUND` / `DANGER` + `DANGER_BACKGROUND` |
  | `SCROLLBAR`, `ACTIVE_SCROLLBAR`, `DISABLED_SCROLLBAR` | `SCROLLBARS` | `SCROLLBAR_TRACK` plus `SCROLLBAR_THUMB` / `SCROLLBAR_THUMB_ACTIVE` / `SCROLLBAR_THUMB_DISABLED` |
  | `KEY_LIST` | none | `PANEL_BACKGROUND`, `BORDER`, `TEXT_PRIMARY`, `TEXT_MUTED`, `SELECTION_BACKGROUND`, `ROW_HOVER_BACKGROUND`, `SCROLLBAR_TRACK`, `SCROLLBAR_THUMB`, `SCROLLBAR_THUMB_ACTIVE` |
  | `KEY_EDITOR`, `PREVIEW_PANEL` | none | `PANEL_BACKGROUND`, `BORDER`, `DECOR_LINE`, `TEXT_PRIMARY` |
  | `SCREEN` | none | `SCREEN_BACKGROUND` |

- Produces `final class PreviewPacking` (package-private) with `record Placement(int family, int sample, int x)` and
  `static List<List<Placement>> pack(List<List<Integer>> familyWidths, int rowWidth, int sampleGap, int familyGap)`:
  rows of placements, `x` relative to the row start.

```java
// The packing rule, which the signature doesn't decide:
// for each family in order, width = sum(samples) + sampleGap × (count − 1):
//   it fits after the current row's last placement (+ familyGap), or the row is empty and it fits → place it whole;
//   else, if width <= rowWidth → start a new row and place it whole;
//   else → start a new row unless the current one is empty, then place sample by sample, starting a new row
//          whenever the next sample would pass rowWidth and the row isn't empty.
// A row is never empty, and a sample wider than rowWidth gets a row of its own.
```

- [ ] **Step 1: Write the failing tests**

```java
// PreviewSampleTest
@Test
void everyThemeKeyIsUsedBySomeSampleOrElement() {
    for (WidgetThemeVariable key : WidgetThemeVariable.values()) {
        assertTrue(Arrays.stream(PreviewSample.values()).anyMatch(sample -> sample.uses(key)), key.getJsonName());
    }
}

@Test
void everyFamilyHasASampleAndTheElementsHaveNone() {
    for (PreviewSample.Family family : PreviewSample.Family.values()) {
        assertFalse(PreviewSample.samplesOf(family).isEmpty(), family.name());
    }
    for (PreviewSample element : List.of(PreviewSample.KEY_LIST, PreviewSample.KEY_EDITOR,
            PreviewSample.PREVIEW_PANEL, PreviewSample.SCREEN)) {
        assertNull(element.family());
    }
}

@Test
void samplesUseTheKeysTheSpecLists() {
    assertTrue(PreviewSample.BUTTON.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
    assertFalse(PreviewSample.DISABLED_BUTTON.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
    assertEquals(Set.of(WidgetThemeVariable.SCREEN_BACKGROUND), PreviewSample.SCREEN.keys());
    assertEquals(List.of(PreviewSample.COMBOBOX, PreviewSample.DROPDOWN), PreviewSample.samplesOf(PreviewSample.Family.CHOICES));
}
```

```java
// PreviewPackingTest (sampleGap 6, familyGap 12)
@Test
void familiesShareARowWhenTheyFit() {
    assertEquals(List.of(List.of(new Placement(0, 0, 0), new Placement(0, 1, 46), new Placement(1, 0, 98))),
            PreviewPacking.pack(List.of(List.of(40, 40), List.of(30)), 200, 6, 12));
}

@Test
void aFamilyThatDoesNotFitTheRestOfTheRowStartsANewRow() {
    assertEquals(List.of(List.of(new Placement(0, 0, 0)), List.of(new Placement(1, 0, 0))),
            PreviewPacking.pack(List.of(List.of(100), List.of(100)), 150, 6, 12));
}

@Test
void aFamilyWiderThanARowWrapsItsSamplesAndTheNextFamilyCanFollow() {
    assertEquals(List.of(
                    List.of(new Placement(0, 0, 0), new Placement(0, 1, 70)),
                    List.of(new Placement(0, 2, 0), new Placement(1, 0, 42))),
            PreviewPacking.pack(List.of(List.of(64, 30, 30), List.of(40)), 100, 6, 12));
}

@Test
void aSampleWiderThanTheRowGetsARowOfItsOwn() {
    assertEquals(List.of(List.of(new Placement(0, 0, 0)), List.of(new Placement(0, 1, 0)), List.of(new Placement(1, 0, 0))),
            PreviewPacking.pack(List.of(List.of(20, 112), List.of(30)), 100, 6, 12));
}
```

> Expected rows for the last test: `[20]` is on row 1, `112` can't follow it so it wraps onto its own row 2, and
> the next family (30) can't follow 112 (112 + 12 + 30 > 100), so it starts row 3.

- [ ] **Step 2: Run to see them fail**: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*PreviewSampleTest' --tests '*PreviewPackingTest'` (both filters bind to the one test task). Expected: compilation fails.
- [ ] **Step 3: Implement** both classes.
- [ ] **Step 4: Run to see them pass** (same command). Expected: 7 tests pass.
- [ ] **Step 5: Check the tree** with `git status --short`.

### Task 7: Translations

**Files:**
- Modify: `mods/src/main/resources/assets/server_waypoint/lang/{en_us,es_es,he_il,zh_cn,zh_hk,zh_tw}.json`
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeTranslationTest.java`
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigTranslationTest.java`
  (`arguments(String)` becomes package-private static for reuse)
- Docs: `docs/tips/gui/local-guide.md` "Runtime color themes" (the `getJsonName()` paragraph and the "When adding
  a theme variable" list) and the review checklist's "translation resources cover every theme variable" line

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: the keys below in every locale, which Tasks 8 and 9 use.

- [ ] **Step 1: Rewrite the test** with three tests and a `read(locale)` helper like the current one:
  - `everyEditorKeyExistsInEveryLocaleWithTheArgumentsOfEnglish`: for each of the six locales and each key in the
    table below, the key exists and `ClientConfigTranslationTest.arguments(...)` of its value equals English's.
  - `noLocaleTranslatesTheRawThemeKeysOrTheRemovedKeys`: no locale has `server_waypoint.theme.variable.<json>` for
    any `WidgetThemeVariable`, nor `server_waypoint.theme.rgb`, `server_waypoint.theme.opacity` or
    `server_waypoint.theme.reset.preview`.
  - `everyPresetNameIsTranslatedInEveryLanguage`: unchanged.

- [ ] **Step 2: Run to see it fail**: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test --tests '*WidgetThemeTranslationTest'`. Expected: FAIL listing missing keys in `es_es`.

- [ ] **Step 3: Edit the six lang files.** Put the keys next to each file's existing `server_waypoint.theme.*` keys;
  remove the 31 `server_waypoint.theme.variable.*` keys and `theme.rgb`, `theme.opacity`, `theme.reset.preview` from
  every locale that has them. Keys below are relative to `server_waypoint.theme.`; non-English text is a draft for
  native review.

  | Key | en_us | es_es | he_il | zh_cn | zh_hk | zh_tw |
  | --- | --- | --- | --- | --- | --- | --- |
  | `screen.title` | Color theme | Tema de colores | ערכת צבעים | 颜色主题 | 顏色主題 | 色彩主題 |
  | `variables` | Theme keys | Claves del tema | מפתחות ערכת הנושא | 主题键 | 主題鍵 | 主題鍵 |
  | `color_picker` | Open color picker | Abrir el selector de color | פתיחת בורר הצבעים | 打开颜色选择器 | 開啟顏色選擇器 | 開啟色彩選擇器 |
  | `save` | Save | Guardar | שמור | 保存 | 儲存 | 儲存 |
  | `save.failed` | Couldn't save the theme. Your changes are still here. | No se pudo guardar el tema. Tus cambios siguen aquí. | לא ניתן היה לשמור את ערכת הנושא. השינויים שלך עדיין כאן. | 无法保存主题。你的更改仍然保留。 | 無法儲存主題。你的更改仍然保留。 | 無法儲存主題。你的變更仍然保留。 |
  | `alpha` | Alpha | Alfa | אלפא | Alpha 值 | Alpha 值 | Alpha 值 |
  | `no_key` | No key selected | Ninguna clave seleccionada | לא נבחר מפתח | 未选择键 | 未選擇鍵 | 未選擇鍵 |
  | `no_key.hint` | Click a key to edit it. Click it again to hide the markers. | Haz clic en una clave para editarla. Haz clic de nuevo para ocultar las marcas. | לחצו על מפתח כדי לערוך אותו. לחצו שוב כדי להסתיר את הסימונים. | 点击一个键进行编辑。再次点击可隐藏标记。 | 點擊一個鍵進行編輯。再次點擊可隱藏標記。 | 點擊一個鍵即可編輯。再次點擊可隱藏標記。 |
  | `reset_key` | Undo changes to this key | Deshacer los cambios de esta clave | ביטול השינויים במפתח זה | 撤销对此键的更改 | 撤銷對此鍵的更改 | 復原此鍵的變更 |
  | `reset.tooltip` | Undo every change since this screen opened. | Deshace todos los cambios desde que se abrió esta pantalla. | ביטול כל השינויים מאז שמסך זה נפתח. | 撤销打开此界面以来的所有更改。 | 撤銷打開此介面以來的所有更改。 | 復原開啟此畫面以來的所有變更。 |
  | `selector.tooltip` | Pick a built-in theme or your Custom colors. Editing a built-in theme turns it into Custom. | Elige un tema integrado o tus colores personalizados. Editar un tema integrado lo convierte en Personalizado. | בחרו ערכת נושא מובנית או את הצבעים המותאמים אישית שלכם. עריכת ערכת נושא מובנית הופכת אותה למותאמת אישית. | 选择内置主题或你的自定义颜色。编辑内置主题会将其变为自定义。 | 選擇內置主題或你的自訂顏色。編輯內置主題會將其變為自訂。 | 選擇內建主題或你的自訂色彩。編輯內建主題會將其轉為自訂。 |
  | `custom_replaced` | Custom now starts from %s. | Personalizado ahora parte de %s. | מותאם אישית מבוסס כעת על %s. | 自定义颜色现在基于%s。 | 自訂顏色現在基於%s。 | 自訂色彩現在以%s為基礎。 |
  | `preview.title` | Preview | Vista previa | תצוגה מקדימה | 预览 | 預覽 | 預覽 |
  | `preview.primary` | Primary | Principal | ראשי | 主要 | 主要 | 主要 |
  | `preview.muted` | Muted | Atenuado | מעומעם | 次要 | 次要 | 次要 |
  | `preview.placeholder` | Editable text | Texto editable | טקסט הניתן לעריכה | 可编辑文本 | 可編輯文字 | 可編輯文字 |
  | `preview.button` | Button | Botón | כפתור | 按钮 | 按鈕 | 按鈕 |
  | `preview.disabled` | Disabled | Desactivado | מושבת | 已禁用 | 已停用 | 已停用 |
  | `preview.normal` | Normal | Normal | רגיל | 常规 | 一般 | 一般 |
  | `preview.selected` | Selected | Seleccionado | נבחר | 已选择 | 已選取 | 已選取 |
  | `preview.popup` | Popup | Emergente | חלון קופץ | 弹出层 | 彈出層 | 彈出層 |
  | `preview.dialog` | Dialog | Diálogo | דו-שיח | 对话框 | 對話框 | 對話框 |
  | `preview.accent` | Accent | Acento | הדגשה | 强调 | 強調 | 強調 |
  | `preview.hover` | Hovered | Resaltado | בריחוף | 悬停 | 懸停 | 懸停 |
  | `preview.success` | Success | Éxito | הצלחה | 成功 | 成功 | 成功 |
  | `preview.warning` | Warning | Aviso | אזהרה | 警告 | 警告 | 警告 |
  | `preview.danger` | Danger | Peligro | סכנה | 危险 | 危險 | 危險 |
  | `preview.tooltip` | Tooltip | Información | תיאור כלי | 提示框 | 提示框 | 提示框 |
  | `preview.choice` | Choice %s | Opción %s | אפשרות %s | 选项 %s | 選項 %s | 選項 %s |

- [ ] **Step 4: Run to see it pass** (same command). Also run `--tests '*ClientConfigTranslationTest'` on the same
  task. Expected: both pass.

- [ ] **Step 5: Document** in "Runtime color themes": the editor shows `getJsonName()` raw and theme keys have no
  translations; in "When adding a theme variable", replace the translation step with "a `PreviewSample` (sample or
  marked element) that uses it"; in the review checklist, replace "translation resources cover every theme
  variable" with "every theme variable has a `PreviewSample`".

- [ ] **Step 6: Check the tree** with `git status --short`.

### Task 8: Screen frame: header, key list, key editor, footer and state

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/TextChoiceDropdown.java`
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemePreview.java` (panel and
  "Preview" header only; Task 9 adds the samples)
- Rewrite: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreen.java`
- Delete: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreenLayoutTest.java`

**Interfaces:**
- Consumes: `WidgetThemeEditorLayout.arrange/showsValues` (Task 4), `KeySelection.click/move` (Task 5),
  `WidgetThemeEditorSession` (Task 3), `ColorHexCodeField.argb/commit` (Task 2), `SettingsListWidget` (Task 1), the
  keys of Task 7.
- Produces `abstract class TextChoiceDropdown extends AbstractDropdownMenuWidget` (package-private): the current
  `ThemeSelector`'s trigger and item drawing (`renderChoice`, label, the ⏷/⏶ arrow and its 14-px column, 2-px
  inset), with `protected abstract Component triggerLabel()` and a nested `protected class TextChoice extends
  AbstractMenuItem` taking a label and a `Runnable onSelected`.
- Produces `final class WidgetThemePreview` (package-private): `WidgetThemePreview(Font font)`,
  `SettingsListWidget list()`, `void setBounds(int visualX, int visualY, int visualWidth, int visualHeight)`,
  `void visitWidgets(Consumer<AbstractWidget> consumer)`, `void setActive(boolean active)`,
  `void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, @Nullable WidgetThemeVariable marked)`.

- [ ] **Step 1: Delete `WidgetThemeConfigScreenLayoutTest`**; `WidgetThemeEditorLayoutTest` replaces it.

- [ ] **Step 2: Extract `TextChoiceDropdown`** from `ThemeSelector`; `ThemeSelector` becomes its subclass in the
  screen, sized `max(font.width("Theme: <preset>") for every preset) + 16` wide and `font.lineHeight + 2` high, with
  `setTooltip(theme.selector.tooltip)` and `setRenderPopupSeparately(true)`. Choosing a preset calls
  `session.select`, clears the status, syncs the controls and refreshes the control states.

- [ ] **Step 3: Build the screen's parts in the constructor** (they survive resizes):
  - `KeyList`: a nested `TreeViewWidget<WidgetThemeVariable>` with 12-px rows, padding 6 on every side,
    `PANEL_BACKGROUND` and `BORDER`, roots `Arrays.asList(WidgetThemeVariable.values())`, no children. A row draws the
    selection or hover fill across the content width, an 8 × 8 chip at x 2 over the 2-px checkerboard, the raw key
    with `drawScaledText` at 0.85 at `KEY_X`, and, while `showValues` is set, the `#AARRGGBB` value at 0.85 in
    `TEXT_MUTED`, right-aligned `VALUE_INSET` from the content edge. Left clicks report the clicked key;
    `keyPressed` handles `InputConstants.KEY_UP`/`KEY_DOWN` while active. Methods: `setSelected(@Nullable key)`,
    `setShowValues(boolean)`, `reveal(WidgetThemeVariable)` (least scroll that shows the row).
  - Key editor: `ScalableText keyText` (1.0) and a `SeparatorWidget` after it; `ColorSquareButton colorButton`
    (size `font.lineHeight`, tooltip `theme.color_picker`, opens the picker); `ColorHexCodeField argbField =
    ColorHexCodeField.argb(...)` with a responder that applies the value at eight digits; `IconButton resetKeyButton`
    9 × 9 with `WidgetTextures.RESET_ICON`, `.withoutBackground().withIconPadding(2).withIconRegion(7, 7, 34, 32, 48, 48)`
    and tooltip `theme.reset_key`; `ScalableText alphaLabel` (0.85, `theme.alpha`); `IntegerSlider alphaSlider =
    new IntegerSlider(0, 0, 64, 30, 0, 255, alpha, this::onAlphaChanged, font, 0.85F)`; `ScalableText hintText`
    (0.85, `TEXT_MUTED`, `theme.no_key.hint`).
  - Footer: `TranslucentButton.fitted` Reset (`waypoint.reset.button`, tooltip `theme.reset.tooltip`), Cancel
    (`server_waypoint.cancel.button`) and Save (`theme.save`) in a `WidgetPack` placed from the right with 6-px
    spacers, as in `ClientConfigScreen`; `ScalableText statusText`.
  - `ScalableText titleText` (1.2, `TEXT_PRIMARY`), `SwatchWidget swatchWidget` (hidden), `WidgetThemePreview preview`.
  - State: `@Nullable WidgetThemeVariable selectedKey = WidgetThemeVariable.TEXT_PRIMARY`, `boolean updatingControls`,
    `boolean hasStatus`, `WidgetThemeEditorLayout.Arrangement arrangement`.

- [ ] **Step 4: Lay out in `layoutContent()`**, called from `init()` and after every status change: measure the
  dropdown width, the buttons' width (visual widths plus two 6-px gaps) and height, and the status height, call
  `WidgetThemeEditorLayout.arrange`, then place: the title cut with `AbstractWaypointPropertiesScreen.cutToWidth` to
  the title rect at 1.2×; the dropdown; the key list by its visual bounds with `setShowValues(showsValues(...))`
  measured at 0.85; the editor's parts at the editor rect inset by 6 (key line at the top, color row 14 below it,
  alpha row 30 below it, rows 11 high, swatch then field 4 apart, reset icon right-aligned and centered on the color
  row, alpha label then slider after `round(width(alpha) × 0.85) + 8`, hint text 14 below the top wrapped to the
  inner width); `preview.setBounds(...)`; the footer; the swatch centered on the group. `getContentWidth/Height`
  return the group's size.

- [ ] **Step 5: Register in `init()`** after `super.init()`, `acceptMovementKeys(false)`, closing the dropdown's
  popup and `layoutContent()`, in Tab order: theme dropdown, key list, color button, ARGB field, reset icon, alpha
  slider, `preview.visitWidgets`, Reset, Cancel, Save, swatch. Then `refreshControlStates()`, and focus the swatch when
  it's visible. `hasOpenModal()` returns `swatchWidget.visible`.

- [ ] **Step 6: Implement the state and handlers**
  - `refreshControlStates()`: the spec's section 6 table. The color button, ARGB field (also `setEditable`), alpha
    slider and reset icon are `visible` only while a key is selected, and the reset icon only while
    `session.isChanged(selectedKey)`; `preview.setActive(!modal)`; Reset and Save need `!modal &&
    session.isDirty()`.
  - `selectKey(@Nullable key)` (from `KeySelection.click` on a click and `KeySelection.move` on Up/Down, which also
    calls `keyList.reveal`): stores it, updates the list and syncs the controls.
  - `syncControls(source)`: under `updatingControls`, sets the key text (`getJsonName()` in `TEXT_PRIMARY`, or
    `theme.no_key` in `TEXT_MUTED`), the color button, ARGB field, alpha slider and swatch from the draft color,
    skipping the control the change came from.
  - `applyColor(argb, source)`: `session.setColor(...).ifPresent(preset -> showStatus(theme.custom_replaced with
    the preset's name, TEXT_MUTED))`, then syncs and refreshes. ARGB responder: eight digits only. Alpha:
    `alpha << 24 | rgb`. Swatch confirm: keep alpha, take RGB, close the picker. Reset icon: `session.revert`, same
    status rule.
  - Reset: `revertAll()`, clear the status, sync, refresh. Save: `save()` then return to the parent; on `IOException`
    log `"Failed to save widget theme"` and show `theme.save.failed` in `DANGER`. Cancel, `onClose()` and `removed()`
    cancel the session as today.
  - Opening the picker closes the dropdown's popup, sets the swatch's color and previous color, shows it, refreshes
    and focuses it; closing hides it, refreshes and focuses the color button.

- [ ] **Step 7: Route input**
  - `mouseClicked` (both version branches): an open theme-dropdown popup takes the click first, as today; then
    `super`; then the modal-focus normalization of today's screen.
  - `keyPressed(int, int, int)`: Escape → `dismissFocusedInput()`, then close the picker, then cancel and close;
    `KEY_RETURN`/`KEY_NUMPADENTER` while `argbField` has focus → `argbField.commit()`; otherwise `super`.

- [ ] **Step 8: Render** in `renderScreenContents` in the spec's order. Use `NO_MOUSE` for everything but the picker
  while it's open, and for content while the pointer is over the open dropdown popup. Order: the key list, the editor
  panel's `PANEL_BACKGROUND` fill and `BORDER` outline with its parts (the hint instead of the controls without a
  selection), `preview.render(..., null)` (markers come in Task 9), title, dropdown, status when shown, footer
  buttons, the dropdown popup, then the swatch between `nextLayer` and `previousLayer`.

- [ ] **Step 9: Compile and test**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test`
Expected: BUILD SUCCESSFUL, including `MovementAllowedScreenPauseTest` and Tasks 1–7's tests.

- [ ] **Step 10: Check the tree** with `git status --short`; Stonecutter markers in the screen stay balanced.

### Task 9: Preview samples, markers and popups

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemePreview.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreen.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java`
  (`scrollFocusedPopup` → `protected`)
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java`
  (`isShiftDown` → package-private static)

**Interfaces:**
- Consumes: `PreviewSample`, `PreviewPacking` (Task 6), `TextChoiceDropdown` (Task 8), `SettingsListWidget.WideRow`
  (Task 1), `TranslucentTooltip`.
- Produces on `WidgetThemePreview`: `static final int MARKER_COLOR = 0xFFFF4FD8`, `SAMPLE_GAP = 6`,
  `FAMILY_GAP = 12`; `void renderPopups(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)`,
  `boolean isMouseOverPopup(double mouseX, double mouseY)`,
  `@Nullable AbstractDropdownMenuWidget clickPopup(double mouseX, double mouseY, int button)`,
  `void closePopups()`, `void layoutPopups(int screenHeight)`,
  `boolean scroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)`,
  and `static void drawMarker(GuiGraphicsExtractor context, int x, int y, int width, int height)`
  (`renderOutline` at x − 2, y − 2, width + 4, height + 4).

- [ ] **Step 1: Build one widget per sample** in an `EnumMap<PreviewSample, …>`, at full size:
  `ScalableText` texts in their keys (`preview.primary`, `preview.muted`, `preview.disabled`); a 104-px
  `TranslucentTextField` with `setPlaceholder(preview.placeholder)`; a 112-px `ComboBoxWidget` on
  `minecraft:overworld` with the three vanilla dimensions and `setRenderPopupSeparately(true)`; a 72-px
  `TextChoiceDropdown` with `preview.choice` 1–3 that remembers its choice, also rendered separately; fitted
  Button and inactive Disabled buttons; a 64 × 11 `ToggleButton` (`preview.normal`/`preview.selected`,
  `CONTROL_BACKGROUND`/`CONTROL_SELECTED_BACKGROUND`, starts selected) and two `OnOffToggleButton`s set to On and
  Off with `setWidth(30)`; two `IntegerSlider`s with 60-px tracks and 26-px fields (the second inactive); chips (a
  screen-local `ShiftableWidget` drawing a fill key, an outline in `BORDER` and its label in a text key; 11 high,
  14 for Popup and Dialog, label width + 12 wide) for Accent, Hovered, Popup, Dialog, Success, Warning and Danger; a
  `TranslucentTooltip` with `preview.tooltip`; three 3 × 20 scrollbar samples (another screen-local
  `ShiftableWidget`).

- [ ] **Step 2: Pack the rows** in `setBounds` when the width changed: measure each family's visual sample widths,
  `PreviewPacking.pack(widths, list.getWidth() − 3 − 2, SAMPLE_GAP, FAMILY_GAP)`, and build each row as a screen-local
  composite (`LayoutElement & Renderable`) over a `WidgetPack` of the row's width and
  `SettingsListLayout.wideRowHeight(tallest)` with `CrossAxisAlignment.CENTER`, the samples added `FORWARD` with
  `SpacerElement` gaps from the placements, rendering its parts in order and visiting the interactive samples'
  widgets. Then `list.setEntries([Header(preview.title), WideRow(row)...])`.

- [ ] **Step 3: Draw the markers** in `render` after the list, clipped to the list's viewport grown by 2 px:
  `drawMarker` around each sample whose `PreviewSample` uses `marked` and that is at least partly in view. In the
  screen, pass `selectedKey` and draw markers for `KEY_LIST`, `KEY_EDITOR` and `PREVIEW_PANEL` around their visual
  rects, and for `SCREEN` at `(2, 2, width − 4, height − 4)`, when they use it. Nothing is marked without a
  selection.

- [ ] **Step 4: Route the popups**
  - `layoutPopups(screenHeight)`: `combobox.layoutPopup(screenHeight, 6)`; the dropdown opens upward when three rows
    below it would pass `screenHeight − 4`. Call it at the start of `renderScreenContents`, as
    `AbstractWaypointPropertiesScreen` does with its comboboxes.
  - `clickPopup`: for the combobox and the dropdown, when `isMouseOver` and `mouseClicked` take the click, return
    that widget; otherwise `closeMenuIfOutside`. The screen calls it after the theme dropdown's routing; for a
    returned widget it calls `setFocused(widget)` and, for the left button, `setDragging(true)`.
  - `scroll`: when the pointer is over the list and it overflows, `closePopups()` and scroll the list. The screen's
    `mouseScrolled` (both version branches) tries `scrollFocusedPopup`, then `preview.scroll` (clearing focus from a
    widget the scroll hid, as `ClientConfigScreen.scrollSettingsList` does), then `super`.
  - Closing: `init()` and opening the color picker call `preview.closePopups()` as well as closing the theme
    dropdown's popup, so a resize never leaves a popup behind.
  - Rendering: `NO_MOUSE` for content while the pointer is over any open popup; `preview.renderPopups` after the
    theme dropdown's popup.
  - Tab: before `super.keyPressed` for `KEY_TAB`, call
    `preview.list().revealTabTarget(this, !ClientConfigScreen.isShiftDown(...))`; after a key press that moved focus
    to a preview widget, `preview.list().reveal(focused)`.

- [ ] **Step 5: Compile and test**

Run: `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Check the tree** with `git status --short`; Stonecutter markers balanced in the touched files.

### Task 10: Documentation

**Files:**
- Modify: `docs/tips/gui/local-guide.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/features/theme-editor/README.md`

- [ ] **Step 1: Rewrite the guide's "In-game theme editor" section** for this design: layout C and its sizes; the
  raw key list (`KeyList`, values, Up/Down, clearing the selection); the key editor (ARGB field, Alpha slider, reset
  icon, empty state); `WidgetThemePreview` (families, `PreviewPacking`, `WideRow`, markers and `PreviewSample`,
  separately rendered popups that close on scroll and resize); Reset, Cancel, Save and the status; the session's
  `setColor`, `revert`, `revertAll`, `isChanged`; `TextChoiceDropdown`. Keep the transaction rules that still hold.
- [ ] **Step 2: Update the other guide passages:** the `screens` example bullet for `WidgetThemeConfigScreen`; the
  `TreeViewWidget` note that calls the theme editor's list a tree that paints its own fill (still true; name it the
  key list); the Input section's mention of `WidgetThemeConfigScreen` calling `dismissFocusedInput()`.
- [ ] **Step 3: CHANGELOG, 4.0.0:** under "Client settings and themes", add that the colour theme editor is a
  developer tool now: raw keys with their `#AARRGGBB` values, an ARGB field, a live preview that marks where the
  selected key is used, Reset that undoes changes and Save only when something changed. Under "Translations", drop
  "the theme editor" from the list of screens that fall back to English.
- [ ] **Step 4: Check** `git diff --check` and `git status --short`.

### Task 11: Verification and validation record

**Files:**
- Create: `docs/features/theme-editor/validation/2026-10-09-theme-editor.md` (use the date the checks run; remove
  `validation/.gitkeep`)
- Modify: `docs/features/theme-editor/README.md` (validation link)

- [ ] **Step 1: Whitespace and markers:** `git diff --check`; for untracked files,
  `git diff --no-index --check /dev/null <file>`; count `//? if`/`//?}`/`/*?`/`*///?}` in every touched Java file.
  Expected: no output, balanced markers.
- [ ] **Step 2: Tests on both Fabric targets:**
  `zsh /Volumes/ssd/fabric_mods_repo/server_waypoint/.superpowers/gw.sh :mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test :mods:1.20.1-fabric:cleanTest :mods:1.20.1-fabric:test`.
  Expected: BUILD SUCCESSFUL with 0 failures.
- [ ] **Step 3: Compile every `mods` target:** `compileJava` and `compileTestJava` for each directory in
  `mods/versions`, with `--continue`. Expected: BUILD SUCCESSFUL for all 40 targets.
- [ ] **Step 4: In game,** on 26.1.2 Fabric and 1.20.1 Fabric, run the spec's in-game list (layout at the three sizes
  and four themes, editing through all three controls, markers including a scrolled-out sample and
  `background.screen`, clearing the selection, the popups including a resize with one open, the picker, Reset /
  Cancel / Save and a failed save, tooltips, Tab and Up/Down, the wheel). The live-game harness under
  `tools/live-game-test` can drive a dev client without OS input; record what ran, with screenshots, and say plainly
  what didn't.
- [ ] **Step 5: Write the validation record** with the commands, results and in-game outcomes, and link it from the
  feature README.
