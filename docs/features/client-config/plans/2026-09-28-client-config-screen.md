# Client Config Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish `ClientConfigScreen` as described in
[the screen design](../specs/2026-09-28-client-config-screen-design.md): a reusable
`SettingsListWidget`, grouped and explained settings, per-row and global reset, VoxelMap and Xaero's
Minimap sync with accurate dialogs, correct Escape and focus handling, and entry points from the
Fabric, NeoForge and Forge mod lists.

**Architecture:** A new public `SettingsListWidget` (built on `ShiftableScrollableWidget`, with its
pure geometry in `SettingsListLayout`) renders headers and rows whose controls stay registered with
the screen. A package-private `ClientConfigSettings` model describes each setting, and a
package-private `ClientConfigSync` holds the pure sync rules. `ClientConfigScreen` is rewritten on
top of them. Small, focused extensions: a cross-axis option in `WidgetPack`, buttons passed into
`DialogWidget`, map-mod lookups in `MapModIntegrations`, a world-less background and number-field
Escape handling in `MovementAllowedScreen`, and one config-screen hook per loader.

**Tech Stack:** Java 17, Minecraft client GUI on Fabric, NeoForge and Forge through Stonecutter,
JUnit 5, GSON, Gradle Kotlin DSL, Mod Menu (compile-only).

**Spec:** [`docs/features/client-config/specs/2026-09-28-client-config-screen-design.md`](../specs/2026-09-28-client-config-screen-design.md)

## Global Constraints

- Java 17 language features only. Indent with four spaces, never tabs.
- The active Stonecutter target is `26.1.2-fabric`. Source under `mods/src` is written in that
  target's form: `GuiGraphicsExtractor`; `extractRenderState` on the line after a
  `//$ render_method_swap` comment; `extractWidgetRenderState` on the line after a
  `//$ render_widget_method_swap` comment; `Identifier` on the line after a
  `//$ resource_location_type_swap` comment where it is a type. Keep `//~` replacement tokens above
  the `package` line.
- Keep every Stonecutter branch working from 1.20.1 to 26.3. Don't add a swap or replacement; if one
  becomes necessary, update the inventory in `AGENTS.md` in the same change.
- `mods/src/main/java/_959/server_waypoint/neoforge/*` files are wrapped in `//? if neoforge {`. In
  the active Fabric tree their whole body sits inside one block comment, so nested block comments
  are escaped: inactive branches use `/^ … ^/` instead of `/* … */`, and Javadoc is written
  `/^* … ^/` (see `MessageChunkPayload`). A literal `*/` ends the outer comment and fails
  `stonecutterPrepare` with "Unclosed scope". Keep that form when editing them.
  `mods/src/main/java/_959/server_waypoint/forge/*` files aren't wrapped (the build excludes them
  for other loaders); their version branches follow the active 26.1.2 form.
- Run Gradle tasks by version project (for example `:mods:1.20.1-fabric:compileJava`). Never switch
  the active Stonecutter project.
- On this machine Gradle can't find Java 25 by itself. Append
  `-Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`
  to every Gradle command that touches a 26.x target. The commands below include it.
- No backward-compatibility code. Removed translation keys need no migration; replaced classes and
  methods are removed outright.
- **Do not commit.** AGENTS.md forbids commits unless the user asks. Each task ends with a review
  checkpoint instead of a commit.
- Update `docs/tips/gui/local-guide.md` in the same task as any GUI API change (AGENTS.md).
- Translation changes apply to all six locales: `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk`,
  `zh_tw`. All non-English strings in this plan are drafts for native review.
- Work on branch `claude/clientconfigscreen-design-f63a3f`, fast-forwarded to commit `8bab7abe`
  (the tip of `waypoint-manager-improved`), with the uncommitted `docs/features/client-config/`
  folder and the `docs/features/README.md` link. Every exact-match "Replace" anchor in this plan was
  checked against that state; anchors that earlier tasks create are checked by those tasks. If an
  anchor doesn't match, stop and report instead of improvising. Make only the edits listed here, and
  leave other working-tree changes alone.
- On this base, `TranslucentButton`, `ToggleButton` and `IconButton` extend `ShiftableButtonWidget`:
  Enter, Space and keypad Enter press a focused, active button, subclasses implement `onPress()`,
  and `onClick` is final. `MovementAllowedScreen.dismissFocusedInput()` makes the first Escape
  close a focused popup or leave a focused `EditBox` or `ComboBoxWidget`; Task 10 adds
  `IntegerSlider` number fields. The GUI guide's "Input" section describes both.

## Review Focus

These conditions are implied by the spec but no unit test can fully exercise them. Each line names
the test that pins what can be pinned and the in-game check (Task 12, Step 5) that covers the rest.

1. **No world loaded**, as when the screen opens from a mod list on the title screen: the screen
   opens without errors, shows vanilla's panorama under the themed overlay, and disables both Sync
   buttons with "Join a world to sync." `ClientConfigSyncTest.noWorldWinsOverEveryOtherBlocker`
   (Task 5) pins the rule; in-game check 8 covers the rest.
2. **A narrow GUI with long translations** (320 pixels wide, Spanish): labels wrap, controls line
   up, and nothing overlaps the reset column or the footer buttons.
   `SettingsListLayoutTest.labelWidthNeverDropsBelowTheMinimum` (Task 2) pins the label floor;
   in-game check 1 covers the rest.
3. **Keyboard-only use with a scrolled list**: Tab and Shift-Tab reach every row, Enter and Space
   press the focused button, Escape closes a dialog before the screen, and focus returns to the
   button that opened it. The `reveal…` tests in `SettingsListLayoutTest` (Task 2) pin the
   scrolling; in-game checks 3 and 7 cover the rest.
4. **The connection changes while a sync dialog is open** (leaving the world, a disconnect, a proxy
   switch): confirming checks the blocker again, and replacing the screen still saves the config.
   `ClientConfigSyncTest` (Task 5) pins the blocker order; in-game checks 6 and 9 cover the rest.
5. **The mouse wheel over a slider**: it scrolls the list while the list overflows and changes the
   slider only when the list fits. No pure logic exists here; in-game check 4 covers it.

## File Map

| File | Responsibility | Tasks |
| --- | --- | --- |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/layout/WidgetPack.java` | Cross-axis alignment | 1 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/layout/WidgetPackTest.java` | Alignment tests | 1 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayout.java` (new) | Pure list geometry | 2 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayoutTest.java` (new) | Geometry tests | 2 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java` (new) | The settings list | 3 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/PaddingWidgetContractTest.java` | Padding contract | 3 |
| `mods/src/main/java/_959/server_waypoint/common/client/ClientConfig.java` | Default constants | 4 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSettings.java` (new) | Settings model | 4 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSettingsTest.java` (new) | Model tests | 4 |
| `mods/src/main/java/_959/server_waypoint/common/client/integrations/{MapModIntegration,MapModIntegrations,XaerosMinimapIntegration,VoxelMapIntegration}.java` | Map-mod API | 5 |
| `mods/src/main/java/_959/server_waypoint/mixin/xaeros_minimap/MinimapWorldStateUpdaterMixin.java` | Uses `syncNow` | 5 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSync.java` (new) | Pure sync rules | 5 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSyncTest.java` (new), `mods/src/test/java/_959/server_waypoint/common/client/integrations/MapModIntegrationsTest.java` (new) | Sync and lookup tests | 5 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/{DialogWidget,ConfirmationDialog}.java` | Buttons through the constructor, confirm label | 6 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TrueFalseToggleButton.java` → `OnOffToggleButton.java` | Rename | 6 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreen.java` | Uses `OnOffToggleButton` | 6 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WidgetTextures.java`, `mods/src/main/resources/assets/server_waypoint/textures/gui/reset.png` (new) | Reset icon | 6 |
| `mods/src/main/resources/assets/server_waypoint/lang/{en_us,es_es,he_il,zh_cn,zh_hk,zh_tw}.json` | Translations | 6, 7, 8 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigTranslationTest.java` (new) | Translation coverage | 6, 7, 8 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java` | The screen | 5, 6, 8 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java` | Background without a world; Escape leaves number fields | 9, 10 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/IntegerSlider.java` | `isEditingNumber()` | 10 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreenPopupEscapeTest.java` | Number-field Escape tests | 10 |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointModMenu.java` (new), `mods/src/main/resources/fabric.mod.json`, `mods/fabric.gradle.kts`, `mods/fabric-unobfuscated.gradle.kts`, `mods/versions/*-fabric/gradle.properties` | Mod Menu entry point | 11 |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java`, `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java` | Mod-list hooks | 11 |
| `docs/tips/gui/local-guide.md` | GUI guide | 1, 3, 6, 8, 9, 10 |
| `README.md`, `README_zh.md` | Player documentation | 11 |
| `docs/features/client-config/README.md`, `docs/features/client-config/validation/` | Validation record | 12 |

---

### Task 1: `WidgetPack` cross-axis alignment

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/layout/WidgetPack.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/layout/WidgetPackTest.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `public enum WidgetPack.CrossAxisAlignment { START, CENTER }`,
  `public void setCrossAxisAlignment(WidgetPack.CrossAxisAlignment alignment)` (lays the children
  out again) and `public WidgetPack.CrossAxisAlignment getCrossAxisAlignment()`. `START` is the
  default and keeps today's behavior. `CENTER` offsets each child across the pack by
  `Math.floorDiv(space - childVisualSize, 2)`.

- [ ] **Step 1: Write the failing tests**

In `WidgetPackTest.java`, replace:
```java
    private static void assertPosition(TestElement element, int x, int y) {
```
With:
```java
    @Test
    void startIsTheDefaultAlignment() {
        assertEquals(WidgetPack.CrossAxisAlignment.START, new WidgetPack().getCrossAxisAlignment());
    }

    @Test
    void centerAlignmentCentersChildrenAcrossAHorizontalPack() {
        WidgetPack pack = new WidgetPack(10, 20, 100, 21, LayoutFlow.Orientation.HORIZONTAL);
        pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        TestElement label = new TestElement(40, 9);
        TestElement control = new TestElement(50, 13);

        pack.addChild(label, LayoutFlow.Direction.FORWARD);
        pack.addChild(control, LayoutFlow.Direction.REVERSE);

        assertPosition(label, 10, 26);
        assertPosition(control, 60, 24);
    }

    @Test
    void centerAlignmentCentersChildrenAcrossAVerticalPack() {
        WidgetPack pack = new WidgetPack(5, 7, 40, 90, LayoutFlow.Orientation.VERTICAL);
        pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        TestElement child = new TestElement(11, 20);

        pack.addChild(child, LayoutFlow.Direction.FORWARD);

        assertPosition(child, 19, 7);
    }

    @Test
    void centerAlignmentUsesTheVisualBoundsOfPaddedChildren() {
        WidgetPack pack = new WidgetPack(0, 0, 60, 20, LayoutFlow.Orientation.HORIZONTAL);
        pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        PaddedElement padded = new PaddedElement(10, 9, 2, 3);

        pack.addChild(padded, LayoutFlow.Direction.FORWARD);

        assertEquals(0, padded.getVisualX());
        assertEquals(4, padded.getVisualY());
    }

    @Test
    void centerAlignmentRoundsTowardTheStartWhenAChildIsTallerThanThePack() {
        WidgetPack pack = new WidgetPack(0, 10, 50, 10, LayoutFlow.Orientation.HORIZONTAL);
        pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        TestElement child = new TestElement(10, 13);

        pack.addChild(child, LayoutFlow.Direction.FORWARD);

        assertPosition(child, 0, 8);
    }

    @Test
    void changingTheAlignmentLaysExistingChildrenOutAgain() {
        WidgetPack pack = new WidgetPack(0, 0, 50, 20, LayoutFlow.Orientation.HORIZONTAL);
        TestElement child = new TestElement(10, 10);
        pack.addChild(child, LayoutFlow.Direction.FORWARD);
        assertPosition(child, 0, 0);

        pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);

        assertPosition(child, 0, 5);
    }

    private static void assertPosition(TestElement element, int x, int y) {
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.layout.WidgetPackTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: compilation fails with `cannot find symbol` for `CrossAxisAlignment`.

- [ ] **Step 3: Implement the alignment**

Replace the whole content of `WidgetPack.java` with:

```java
package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;

import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.getVisualHeight;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.getVisualWidth;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.setVisualPosition;

/**
 * Packs children inward from either end of a fixed-size layout area.
 *
 * <p>For a horizontal pack, {@link Direction#FORWARD} anchors a child to the left and
 * {@link Direction#REVERSE} anchors it to the right. For a vertical pack, the same options anchor
 * children to the top and bottom respectively. Adding children never changes the pack's dimensions;
 * resize the pack explicitly through {@link Expandable} when its available area changes.
 *
 * <p>Across the pack's orientation, children touch the top of a horizontal pack or the left of a
 * vertical pack. {@link #setCrossAxisAlignment} can center them instead.
 *
 * <p>This class only manages layout. The owning screen or composite remains responsible for
 * registering and rendering the packed widgets.
 */
public class WidgetPack implements LayoutElement, Expandable {
    private int x;
    private int y;
    private int width;
    private int height;
    private final Orientation orientation;
    private final List<Entry> children = new ArrayList<>();
    private CrossAxisAlignment crossAxisAlignment = CrossAxisAlignment.START;

    public WidgetPack() {
        this(0, 0, 0, 0, Orientation.HORIZONTAL);
    }

    public WidgetPack(int width, int height) {
        this(0, 0, width, height, Orientation.HORIZONTAL);
    }

    public WidgetPack(Orientation orientation) {
        this(0, 0, 0, 0, orientation);
    }

    public WidgetPack(int width, int height, Orientation orientation) {
        this(0, 0, width, height, orientation);
    }

    public WidgetPack(int x, int y, int width, int height, Orientation orientation) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
    }

    /**
     * Adds a child that stacks inward from the selected side.
     */
    public <T extends LayoutElement> void addChild(T child, Direction anchor) {
        this.children.add(new Entry(child, anchor));
        this.layoutChildren();
    }

    /**
     * Adds a child that stacks from the left or top side.
     */
    public <T extends LayoutElement> void addChild(T child) {
        this.addChild(child, Direction.FORWARD);
    }

    /**
     * Places children across the pack's orientation and lays them out again.
     */
    public void setCrossAxisAlignment(CrossAxisAlignment alignment) {
        this.crossAxisAlignment = Objects.requireNonNull(alignment, "alignment");
        this.layoutChildren();
    }

    public CrossAxisAlignment getCrossAxisAlignment() {
        return this.crossAxisAlignment;
    }

    @Override
    public void setX(int x) {
        this.x = x;
        this.layoutChildren();
    }

    @Override
    public void setY(int y) {
        this.y = y;
        this.layoutChildren();
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
        this.layoutChildren();
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.layoutChildren();
    }

    @Override
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        this.layoutChildren();
    }

    @Override
    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
        this.layoutChildren();
    }

    @Override
    public void setVisualWidth(int width) {
        this.setWidth(width);
    }

    @Override
    public void setVisualHeight(int height) {
        this.setHeight(height);
    }

    @Override
    public void setVisualDimensions(int width, int height) {
        this.setDimensions(width, height);
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        for (Entry entry : this.children) {
            entry.widget().visitWidgets(consumer);
        }
    }

    public Orientation getOrientation() {
        return this.orientation;
    }

    private void layoutChildren() {
        if (this.orientation == Orientation.HORIZONTAL) {
            this.layoutHorizontal();
        } else {
            this.layoutVertical();
        }
    }

    private void layoutHorizontal() {
        int startX = this.x;
        int endX = this.x + this.width;

        for (Entry entry : this.children) {
            int childWidth = getVisualWidth(entry.widget());
            int childY = this.y + this.crossOffset(this.height, getVisualHeight(entry.widget()));
            if (entry.anchor() == Direction.FORWARD) {
                setVisualPosition(entry.widget(), startX, childY);
                startX += childWidth;
            } else {
                endX -= childWidth;
                setVisualPosition(entry.widget(), endX, childY);
            }
        }
    }

    private void layoutVertical() {
        int startY = this.y;
        int endY = this.y + this.height;

        for (Entry entry : this.children) {
            int childHeight = getVisualHeight(entry.widget());
            int childX = this.x + this.crossOffset(this.width, getVisualWidth(entry.widget()));
            if (entry.anchor() == Direction.FORWARD) {
                setVisualPosition(entry.widget(), childX, startY);
                startY += childHeight;
            } else {
                endY -= childHeight;
                setVisualPosition(entry.widget(), childX, endY);
            }
        }
    }

    private int crossOffset(int space, int size) {
        return this.crossAxisAlignment == CrossAxisAlignment.CENTER ? Math.floorDiv(space - size, 2) : 0;
    }

    /**
     * How children are placed across the pack's orientation.
     */
    public enum CrossAxisAlignment {
        /** At the top of a horizontal pack or the left of a vertical pack. */
        START,
        /** Centered by visual bounds, rounding toward the start. */
        CENTER
    }

    private record Entry(LayoutElement widget, Direction anchor) {
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.layout.WidgetPackTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`; all eleven `WidgetPackTest` tests pass.

- [ ] **Step 5: Document the option in the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
Like `ExpandableManager`, `WidgetPack` is layout-only. Its children still need to be registered for
input and rendered by their owning screen or composite.
```
With:
```markdown
Like `ExpandableManager`, `WidgetPack` is layout-only. Its children still need to be registered for
input and rendered by their owning screen or composite.

Children touch the top of a horizontal pack, or the left of a vertical pack. Call
`setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER)` to center them across the pack by
their visual bounds instead, rounding toward the start; changing the alignment lays the children out
again. `SettingsListWidget` rows use this to line up labels and controls of different heights.
```

- [ ] **Step 6: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `git status --short` → `WidgetPack.java`, `WidgetPackTest.java` and `local-guide.md` are
modified, besides the plan's pre-existing documentation changes.

---

### Task 2: `SettingsListLayout`

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayout.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/layout/SettingsListLayoutTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces, all `public static` in `SettingsListLayout`:
  - Constants: `SECTION_GAP = 6`, `LABEL_GAP = 8`, `SUFFIX_GAP = 3`, `ACTION_GAP = 4`,
    `MIN_LABEL_WIDTH = 40`, `ROW_VERTICAL_PADDING = 4`, `MIN_ROW_HEIGHT = 21`,
    `HEADER_BOTTOM_PADDING = 4`.
  - `int suffixSlot(int suffixColumnWidth)` and `int actionSlot(int actionColumnWidth)`: the column
    plus its gap, or 0 for an empty column.
  - `int labelWidth(int rowWidth, int controlWidth, int suffixColumnWidth, int actionColumnWidth)`.
  - `int rowPreferredWidth(int labelWidth, int controlWidth, int suffixColumnWidth, int actionColumnWidth)`.
  - `int rowHeight(int tallestPartHeight)` and `int headerHeight(int titleHeight)`.
  - `int[] entryTops(int[] heights, boolean[] headers)`: one top per entry plus the content height
    as the last element.
  - `boolean fullyVisible(int top, int bottom, int viewportTop, int viewportBottom)`.
  - `double revealScroll(double scroll, int viewportHeight, int contentHeight, int focusedTop,
    int focusedBottom, int previousTop, int nextBottom)`.

- [ ] **Step 1: Write the failing test**

Create `SettingsListLayoutTest.java`:

```java
package _959.server_waypoint.common.client.gui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsListLayoutTest {
    @Test
    void emptyColumnsTakeNoSpaceOrGap() {
        assertEquals(0, SettingsListLayout.suffixSlot(0));
        assertEquals(0, SettingsListLayout.actionSlot(0));
        assertEquals(38, SettingsListLayout.suffixSlot(35));
        assertEquals(17, SettingsListLayout.actionSlot(13));
    }

    @Test
    void labelGetsTheWidthLeftAfterTheControlTheColumnsAndTheGap() {
        // 300 - 135 control - 8 gap - 38 unit slot - 17 action slot
        assertEquals(102, SettingsListLayout.labelWidth(300, 135, 35, 13));
    }

    @Test
    void labelWidthNeverDropsBelowTheMinimum() {
        assertEquals(SettingsListLayout.MIN_LABEL_WIDTH, SettingsListLayout.labelWidth(120, 135, 35, 13));
    }

    @Test
    void preferredRowWidthFitsTheLabelOnOneLine() {
        // 105 label + 8 gap + 135 control + 38 unit slot + 17 action slot
        assertEquals(303, SettingsListLayout.rowPreferredWidth(105, 135, 35, 13));
    }

    @Test
    void rowsKeepTheirPaddingAndMinimumHeight() {
        assertEquals(21, SettingsListLayout.rowHeight(11));
        assertEquals(21, SettingsListLayout.rowHeight(13));
        assertEquals(26, SettingsListLayout.rowHeight(18));
        assertEquals(13, SettingsListLayout.headerHeight(9));
    }

    @Test
    void headersAfterTheFirstEntryGetASectionGap() {
        int[] tops = SettingsListLayout.entryTops(
                new int[]{13, 21, 21, 13, 21},
                new boolean[]{true, false, false, true, false}
        );

        assertArrayEquals(new int[]{0, 13, 34, 61, 74, 95}, tops);
    }

    @Test
    void entryTopsRejectArraysOfDifferentLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> SettingsListLayout.entryTops(new int[]{1}, new boolean[0]));
    }

    @Test
    void fullVisibilityIncludesBothEdges() {
        assertTrue(SettingsListLayout.fullyVisible(10, 30, 10, 30));
        assertFalse(SettingsListLayout.fullyVisible(9, 30, 10, 30));
        assertFalse(SettingsListLayout.fullyVisible(10, 31, 10, 30));
    }

    @Test
    void revealKeepsTheScrollWhenTheRowAndItsNeighborsAreVisible() {
        assertEquals(10.0, SettingsListLayout.revealScroll(10.0, 100, 300, 40, 61, 19, 82));
    }

    @Test
    void revealScrollsDownJustEnoughToShowTheNextRow() {
        assertEquals(32.0, SettingsListLayout.revealScroll(0.0, 100, 300, 90, 111, 69, 132));
    }

    @Test
    void revealScrollsUpJustEnoughToShowThePreviousRow() {
        assertEquals(159.0, SettingsListLayout.revealScroll(200.0, 100, 400, 180, 201, 159, 222));
    }

    @Test
    void neighborsThatDoNotFitGiveWayToTheFocusedRow() {
        assertEquals(91.0, SettingsListLayout.revealScroll(0.0, 30, 400, 100, 121, 79, 142));
    }

    @Test
    void aRowTallerThanTheViewportShowsItsTop() {
        assertEquals(100.0, SettingsListLayout.revealScroll(0.0, 20, 400, 100, 150, 79, 171));
    }

    @Test
    void revealNeverScrollsPastTheContent() {
        assertEquals(50.0, SettingsListLayout.revealScroll(0.0, 100, 150, 120, 141, 99, 150));
    }

    @Test
    void revealingTheFirstRowShowsTheHeaderAboveIt() {
        assertEquals(0.0, SettingsListLayout.revealScroll(20.0, 100, 300, 13, 34, 0, 55));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.layout.SettingsListLayoutTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: compilation fails with `cannot find symbol` for `SettingsListLayout`.

- [ ] **Step 3: Implement the helper**

Create `SettingsListLayout.java`:

```java
package _959.server_waypoint.common.client.gui.layout;

/**
 * Pure geometry for {@code SettingsListWidget}: columns, label width, entry offsets, reveal
 * scrolling and visibility. Values are pixels; offsets are relative to the top of the list content.
 */
public final class SettingsListLayout {
    public static final int SECTION_GAP = 6;
    public static final int LABEL_GAP = 8;
    public static final int SUFFIX_GAP = 3;
    public static final int ACTION_GAP = 4;
    public static final int MIN_LABEL_WIDTH = 40;
    public static final int ROW_VERTICAL_PADDING = 4;
    public static final int MIN_ROW_HEIGHT = 21;
    public static final int HEADER_BOTTOM_PADDING = 4;

    private SettingsListLayout() {
    }

    /** The unit column plus its gap, or zero when no row has a unit. */
    public static int suffixSlot(int suffixColumnWidth) {
        return suffixColumnWidth > 0 ? SUFFIX_GAP + suffixColumnWidth : 0;
    }

    /** The action column plus its gap, or zero when no row has an action. */
    public static int actionSlot(int actionColumnWidth) {
        return actionColumnWidth > 0 ? ACTION_GAP + actionColumnWidth : 0;
    }

    /** The width a row's label may wrap to, never less than {@link #MIN_LABEL_WIDTH}. */
    public static int labelWidth(int rowWidth, int controlWidth, int suffixColumnWidth, int actionColumnWidth) {
        int width = rowWidth - controlWidth - LABEL_GAP
                - suffixSlot(suffixColumnWidth) - actionSlot(actionColumnWidth);
        return Math.max(MIN_LABEL_WIDTH, width);
    }

    /** The width a row needs to show its label on one line. */
    public static int rowPreferredWidth(int labelWidth, int controlWidth, int suffixColumnWidth, int actionColumnWidth) {
        return labelWidth + LABEL_GAP + controlWidth + suffixSlot(suffixColumnWidth) + actionSlot(actionColumnWidth);
    }

    /** A row's tallest part plus padding above and below, and at least {@link #MIN_ROW_HEIGHT}. */
    public static int rowHeight(int tallestPartHeight) {
        return Math.max(MIN_ROW_HEIGHT, tallestPartHeight + ROW_VERTICAL_PADDING * 2);
    }

    /** A header's title plus the padding below it. */
    public static int headerHeight(int titleHeight) {
        return titleHeight + HEADER_BOTTOM_PADDING;
    }

    /**
     * The top of each entry, with {@link #SECTION_GAP} before every header except the first entry.
     * The returned array has one more element than {@code heights}; the last one is the content height.
     */
    public static int[] entryTops(int[] heights, boolean[] headers) {
        if (heights.length != headers.length) {
            throw new IllegalArgumentException("heights and headers have different lengths");
        }
        int[] tops = new int[heights.length + 1];
        int y = 0;
        for (int i = 0; i < heights.length; i++) {
            if (headers[i] && i > 0) {
                y += SECTION_GAP;
            }
            tops[i] = y;
            y += heights[i];
        }
        tops[heights.length] = y;
        return tops;
    }

    /** Whether the span from {@code top} to {@code bottom} lies entirely inside the viewport. */
    public static boolean fullyVisible(int top, int bottom, int viewportTop, int viewportBottom) {
        return top >= viewportTop && bottom <= viewportBottom;
    }

    /**
     * The scroll position that fully shows the focused row while moving as little as possible. When
     * they fit, it also shows everything down to {@code nextBottom} and then up to
     * {@code previousTop}. A row taller than the viewport shows its top. The result stays within the
     * content.
     */
    public static double revealScroll(double scroll, int viewportHeight, int contentHeight,
                                      int focusedTop, int focusedBottom, int previousTop, int nextBottom) {
        double maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (focusedBottom - focusedTop > viewportHeight) {
            return clamp(focusedTop, 0, maxScroll);
        }
        double lowest = focusedBottom - viewportHeight;
        double highest = focusedTop;
        int bottom = focusedBottom;
        if (nextBottom - focusedTop <= viewportHeight) {
            lowest = Math.max(lowest, nextBottom - viewportHeight);
            bottom = nextBottom;
        }
        if (bottom - previousTop <= viewportHeight) {
            highest = Math.min(highest, previousTop);
        }
        return clamp(clamp(scroll, lowest, highest), 0, maxScroll);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.layout.SettingsListLayoutTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`; all fifteen tests pass.

- [ ] **Step 5: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `git status --short` → the two new files are untracked; nothing else changed in this task.

---

### Task 3: `SettingsListWidget`

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java`
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/PaddingWidgetContractTest.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: `WidgetPack.CrossAxisAlignment` (Task 1); every `SettingsListLayout` member (Task 2).
- Produces, in `public class SettingsListWidget extends ShiftableScrollableWidget implements Padding, Expandable`:
  - `public static final int PANEL_PADDING = 6`.
  - `public SettingsListWidget(Font font)`.
  - `public void setEntries(List<Entry> entries)`, `public void relayout()`,
    `public int getPreferredWidth()`, `public int getContentHeight()`,
    `public void reveal(GuiEventListener widget)`,
    `public void visitWidgets(Consumer<AbstractWidget> consumer)` (row widgets, then the list).
  - `public abstract static sealed class Entry permits Header, Row`.
  - `public static final class Header extends Entry` with `public Header(Component title)`.
  - `public static final class Row extends Entry` with
    `public <C extends LayoutElement & Renderable> Row(Component label, C control)` and the builders
    `suffix(Component)`, `action(AbstractWidget)`, `tooltip(Supplier<Component>)`,
    `labelColor(WidgetThemeVariable)`, each returning the row.
  - Sizing: `setVisualWidth(int)` and `setVisualHeight(int)` take the panel's outer size;
    `setPosition(int, int)` takes the position of the rows' area, `PANEL_PADDING` inside the panel.

- [ ] **Step 1: Write the failing contract test**

In `PaddingWidgetContractTest.java`, replace:
```java
        assertPadding(TreeViewWidget.class);
```
With:
```java
        assertPadding(TreeViewWidget.class);
        assertPadding(SettingsListWidget.class);
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.widgets.PaddingWidgetContractTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: compilation fails with `cannot find symbol` for `SettingsListWidget`.

- [ ] **Step 3: Create the widget**

Create `SettingsListWidget.java`:

```java
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.layout.SettingsListLayout;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * A scrollable panel of settings: section headers, and rows made of a label, a control, an optional
 * unit and an optional action. Entries have their own heights, so long labels wrap. The row widgets
 * stay registered with the screen; see the GUI guide's "Settings lists" section for the contract.
 */
public class SettingsListWidget extends ShiftableScrollableWidget implements Padding, Expandable {
    /** Space between the panel outline and the rows, on every side. */
    public static final int PANEL_PADDING = 6;
    private static final int SCROLLBAR_GAP = 2;
    private static final int SCROLL_STEP = 10;
    private static final long TOOLTIP_DELAY_NANOS = 500_000_000L;
    private static final int HEADER_LINE_GAP = 4;
    private static final int MIN_HEADER_LINE = 8;
    private static final int NO_MOUSE = -10_000;

    private final Font font;
    private final PaddingBackground panel;
    private List<Entry> entries = List.of();
    private int[] entryTops = {0};
    private int[] entryHeights = new int[0];
    private int suffixColumnWidth;
    private int actionColumnWidth;
    private int contentHeight;
    private @Nullable Row tooltipRow;
    private long tooltipRowSince;

    public SettingsListWidget(Font font) {
        super(0, 0, 0, 0, Component.empty());
        this.font = Objects.requireNonNull(font, "font");
        this.panel = new PaddingBackground(this, PANEL_PADDING, PANEL_PADDING, PANEL_BACKGROUND, BORDER, true);
    }

    /** Replaces the entries and lays them out. */
    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.relayout();
    }

    /** Lays the entries out again, after a label, unit or control size changes. */
    public void relayout() {
        this.suffixColumnWidth = 0;
        this.actionColumnWidth = 0;
        for (Entry entry : this.entries) {
            if (entry instanceof Row row) {
                this.suffixColumnWidth = Math.max(this.suffixColumnWidth, row.suffixWidth(this.font));
                this.actionColumnWidth = Math.max(this.actionColumnWidth, row.actionWidth());
            }
        }
        int rowWidth = this.rowWidth();
        this.entryHeights = new int[this.entries.size()];
        boolean[] headers = new boolean[this.entries.size()];
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            this.entryHeights[i] = entry.layout(this, rowWidth);
            headers[i] = entry instanceof Header;
        }
        this.entryTops = SettingsListLayout.entryTops(this.entryHeights, headers);
        this.contentHeight = this.entryTops[this.entries.size()];
        this.refreshScroll();
    }

    /** The width at which no entry wraps, including the always-reserved scrollbar column. */
    public int getPreferredWidth() {
        int widest = 0;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, entry.preferredWidth(this));
        }
        return widest + this.SCROLLBAR_WIDTH + SCROLLBAR_GAP;
    }

    @Override
    public int getContentHeight() {
        return this.contentHeight;
    }

    @Override
    public double getDeltaYPerScroll() {
        return SCROLL_STEP;
    }

    @Override
    public void setScrollY(double scrollY) {
        super.setScrollY(scrollY);
        this.positionEntries();
    }

    /**
     * Scrolls the least amount that fully shows the row owning {@code widget} and, when they fit, the
     * nearest rows above and below that have an interactive widget. Does nothing for other listeners.
     */
    public void reveal(GuiEventListener widget) {
        int index = this.rowIndexOf(widget);
        if (index < 0) {
            return;
        }
        int previous = this.interactiveRowBefore(index);
        int next = this.interactiveRowAfter(index);
        int previousTop = previous < 0 ? 0 : this.entryTops[previous];
        int nextBottom = next < 0 ? this.contentHeight : this.entryBottom(next);
        this.setScrollY(SettingsListLayout.revealScroll(this.getScrollY(), this.height, this.contentHeight,
                this.entryTops[index], this.entryBottom(index), previousTop, nextBottom));
    }

    /** Visits every row's control and action in entry order, then the list itself. */
    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        for (Entry entry : this.entries) {
            entry.visitWidgets(consumer);
        }
        consumer.accept(this);
    }

    /** The list itself isn't a Tab stop; the widgets in its rows are. */
    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    /** Only the scrollbar reacts to clicks; clicks on empty list space do nothing. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.overflows() || !this.checkScrollbarDragged(mouseX, mouseY, button)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.positionEntries();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.positionEntries();
    }

    @Override
    public void setXOffset(int xOffset) {
        super.setXOffset(xOffset);
        this.positionEntries();
    }

    @Override
    public void setYOffset(int yOffset) {
        super.setYOffset(yOffset);
        this.positionEntries();
    }

    @Override
    public void setWidth(int width) {
        if (this.width != width) {
            this.width = width;
            this.relayout();
        }
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.refreshScroll();
    }

    @Override
    public void setVisualWidth(int width) {
        this.setWidth(width - PANEL_PADDING * 2);
    }

    @Override
    public void setVisualHeight(int height) {
        this.setHeight(height - PANEL_PADDING * 2);
    }

    @Override
    public int getVisualX() {
        return this.panel.getVisualX();
    }

    @Override
    public int getVisualY() {
        return this.panel.getVisualY();
    }

    @Override
    public int getVisualWidth() {
        return this.panel.getVisualWidth();
    }

    @Override
    public int getVisualHeight() {
        return this.panel.getVisualHeight();
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.panel.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        int x = this.getX();
        int y = this.getY();
        int bottom = y + this.height;
        int rowWidth = this.rowWidth();
        boolean pointerInside = this.active
                && mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < bottom;
        Row hovered = null;
        context.enableScissor(x, y, x + this.width, bottom);
        for (int i = 0; i < this.entries.size(); i++) {
            int top = y + this.entryTops[i] - (int) this.getScrollY();
            int entryBottom = top + this.entryHeights[i];
            if (entryBottom <= y || top >= bottom) {
                continue;
            }
            Entry entry = this.entries.get(i);
            if (pointerInside && mouseY >= top && mouseY < entryBottom && entry instanceof Row row) {
                hovered = row;
                context.fill(x, top, x + rowWidth, entryBottom, getColor(ROW_HOVER_BACKGROUND));
            }
            entry.renderEntry(this, context, mouseX, mouseY, deltaTicks);
        }
        context.disableScissor();
        this.drawScrollbar(context);
        this.scheduleTooltip(context, hovered, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    private void scheduleTooltip(GuiGraphicsExtractor context, @Nullable Row hovered, int mouseX, int mouseY) {
        if (hovered == null || hovered.isOverAction(mouseX, mouseY)) {
            this.tooltipRow = null;
            return;
        }
        long now = System.nanoTime();
        if (hovered != this.tooltipRow) {
            this.tooltipRow = hovered;
            this.tooltipRowSince = now;
            return;
        }
        if (now - this.tooltipRowSince < TOOLTIP_DELAY_NANOS) {
            return;
        }
        Component text = hovered.tooltipText();
        if (text == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        var lines = Tooltip.create(text).toCharSequence(client);
        // Anchor the row's tooltip to the cursor, not the whole scrollable list.
        //? if >=1.21.6 {
        context.setTooltipForNextFrame(lines, mouseX, mouseY);
        //?} else {
        /*if (client.screen != null) client.screen.setTooltipForNextRenderPass(lines);
        *///?}
    }

    /**
     * Draws one part of an entry. A row widget that isn't fully inside the viewport is invisible to
     * input; it's drawn anyway, clipped by the scissor, with the mouse moved off-screen.
     */
    private void renderPart(GuiGraphicsExtractor context, Renderable part, int mouseX, int mouseY, float deltaTicks) {
        if (part instanceof AbstractWidget widget && !widget.visible) {
            widget.visible = true;
            widget.
            //$ render_method_swap
            extractRenderState
                    (context, NO_MOUSE, NO_MOUSE, deltaTicks);
            widget.visible = false;
            return;
        }
        part.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }

    private void positionEntries() {
        int top = this.getY() - (int) this.getScrollY();
        int viewportTop = this.getY();
        int viewportBottom = this.getY() + this.height;
        for (int i = 0; i < this.entries.size(); i++) {
            this.entries.get(i).position(this.getX(), top + this.entryTops[i], viewportTop, viewportBottom);
        }
    }

    private int rowIndexOf(GuiEventListener widget) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i) instanceof Row row && row.owns(widget)) {
                return i;
            }
        }
        return -1;
    }

    private int interactiveRowBefore(int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (this.entries.get(i) instanceof Row row && row.isInteractive()) {
                return i;
            }
        }
        return -1;
    }

    private int interactiveRowAfter(int index) {
        for (int i = index + 1; i < this.entries.size(); i++) {
            if (this.entries.get(i) instanceof Row row && row.isInteractive()) {
                return i;
            }
        }
        return -1;
    }

    private int entryBottom(int index) {
        return this.entryTops[index] + this.entryHeights[index];
    }

    private int rowWidth() {
        return Math.max(0, this.width - this.SCROLLBAR_WIDTH - SCROLLBAR_GAP);
    }

    private static int visualX(LayoutElement element) {
        return element instanceof Padding padding ? padding.getVisualX() : element.getX();
    }

    private static int visualY(LayoutElement element) {
        return element instanceof Padding padding ? padding.getVisualY() : element.getY();
    }

    private static int visualWidth(LayoutElement element) {
        return element instanceof Padding padding ? padding.getVisualWidth() : element.getWidth();
    }

    private static int visualHeight(LayoutElement element) {
        return element instanceof Padding padding ? padding.getVisualHeight() : element.getHeight();
    }

    /** An entry of the list: a {@link Header} or a {@link Row}. */
    public abstract static sealed class Entry permits Header, Row {
        private Entry() {
        }

        /** Lays the entry out for {@code rowWidth} and returns its height. */
        abstract int layout(SettingsListWidget list, int rowWidth);

        abstract void position(int x, int y, int viewportTop, int viewportBottom);

        abstract void renderEntry(SettingsListWidget list, GuiGraphicsExtractor context,
                                  int mouseX, int mouseY, float deltaTicks);

        abstract int preferredWidth(SettingsListWidget list);

        void visitWidgets(Consumer<AbstractWidget> consumer) {
        }
    }

    /** A section title followed by a line across the rest of the width. */
    public static final class Header extends Entry {
        private final Component title;
        private @Nullable ScalableText titleText;
        private int x;
        private int y;
        private int width;

        public Header(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        @Override
        int layout(SettingsListWidget list, int rowWidth) {
            int wrapWidth = Math.max(1, rowWidth);
            if (this.titleText == null) {
                this.titleText = new ScalableText(0, 0, this.title, 1.0F, TEXT_PRIMARY, wrapWidth, list.font);
            } else {
                this.titleText.setMaxWidth(wrapWidth);
            }
            this.width = rowWidth;
            return SettingsListLayout.headerHeight(this.titleText.getHeight());
        }

        @Override
        void position(int x, int y, int viewportTop, int viewportBottom) {
            this.x = x;
            this.y = y;
            if (this.titleText != null) {
                this.titleText.setPosition(x, y);
            }
        }

        @Override
        void renderEntry(SettingsListWidget list, GuiGraphicsExtractor context,
                         int mouseX, int mouseY, float deltaTicks) {
            if (this.titleText == null) {
                return;
            }
            list.renderPart(context, this.titleText, mouseX, mouseY, deltaTicks);
            int lineStart = this.x + list.font.width(this.title) + HEADER_LINE_GAP;
            int lineEnd = this.x + this.width;
            if (this.titleText.getHeight() <= list.font.lineHeight && lineEnd - lineStart >= MIN_HEADER_LINE) {
                int lineY = this.y + list.font.lineHeight / 2;
                context.fill(lineStart, lineY, lineEnd, lineY + 1, getColor(BORDER));
            }
        }

        @Override
        int preferredWidth(SettingsListWidget list) {
            return list.font.width(this.title) + HEADER_LINE_GAP + MIN_HEADER_LINE;
        }
    }

    /** A label on the left, then a control, an optional unit and an optional action on the right. */
    public static final class Row extends Entry {
        private final Component label;
        private final LayoutElement control;
        private final Renderable controlRenderer;
        private @Nullable Component suffix;
        private @Nullable AbstractWidget action;
        private @Nullable Supplier<Component> tooltip;
        private WidgetThemeVariable labelColor = TEXT_PRIMARY;
        private @Nullable ScalableText labelText;
        private @Nullable ScalableText suffixText;
        private WidgetPack pack = new WidgetPack(Orientation.HORIZONTAL);

        public <C extends LayoutElement & Renderable> Row(Component label, C control) {
            this.label = Objects.requireNonNull(label, "label");
            this.control = Objects.requireNonNull(control, "control");
            this.controlRenderer = control;
        }

        /** A muted unit after the control, such as "%" or "chunks". */
        public Row suffix(Component unit) {
            this.suffix = Objects.requireNonNull(unit, "unit");
            return this;
        }

        /** A widget in the last column, such as a reset button. */
        public Row action(AbstractWidget action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        /** The hover text. It's read each time the tooltip shows, so it can follow the current state. */
        public Row tooltip(Supplier<Component> tooltip) {
            this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
            return this;
        }

        public Row labelColor(WidgetThemeVariable color) {
            this.labelColor = Objects.requireNonNull(color, "color");
            return this;
        }

        int suffixWidth(Font font) {
            return this.suffix == null ? 0 : font.width(this.suffix);
        }

        int actionWidth() {
            return this.action == null ? 0 : visualWidth(this.action);
        }

        boolean owns(GuiEventListener widget) {
            return widget == this.control || widget == this.action;
        }

        boolean isInteractive() {
            return this.control instanceof AbstractWidget || this.action != null;
        }

        boolean isOverAction(int mouseX, int mouseY) {
            if (this.action == null) {
                return false;
            }
            int x = visualX(this.action);
            int y = visualY(this.action);
            return mouseX >= x && mouseX < x + visualWidth(this.action)
                    && mouseY >= y && mouseY < y + visualHeight(this.action);
        }

        @Nullable Component tooltipText() {
            return this.tooltip == null ? null : this.tooltip.get();
        }

        @Override
        int layout(SettingsListWidget list, int rowWidth) {
            int suffixColumn = list.suffixColumnWidth;
            int actionColumn = list.actionColumnWidth;
            int labelWidth = SettingsListLayout.labelWidth(rowWidth, visualWidth(this.control), suffixColumn, actionColumn);
            if (this.labelText == null) {
                this.labelText = new ScalableText(0, 0, this.label, 1.0F, this.labelColor, labelWidth, list.font);
            } else {
                this.labelText.setMaxWidth(labelWidth);
                this.labelText.setColor(this.labelColor);
            }
            if (this.suffix != null) {
                if (this.suffixText == null) {
                    this.suffixText = new ScalableText(0, 0, this.suffix, 1.0F, TEXT_MUTED, suffixColumn, list.font);
                } else {
                    this.suffixText.setMaxWidth(suffixColumn);
                }
            }
            int actionHeight = this.action == null ? 0 : visualHeight(this.action);
            int height = SettingsListLayout.rowHeight(
                    Math.max(this.labelText.getHeight(), Math.max(visualHeight(this.control), actionHeight)));

            WidgetPack pack = new WidgetPack(0, 0, rowWidth, height, Orientation.HORIZONTAL);
            pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
            pack.addChild(this.labelText, Direction.FORWARD);
            if (actionColumn > 0) {
                int actionWidth = this.actionWidth();
                if (this.action != null) {
                    pack.addChild(this.action, Direction.REVERSE);
                }
                if (actionColumn > actionWidth) {
                    pack.addChild(SpacerElement.width(actionColumn - actionWidth), Direction.REVERSE);
                }
                pack.addChild(SpacerElement.width(SettingsListLayout.ACTION_GAP), Direction.REVERSE);
            }
            if (suffixColumn > 0) {
                if (this.suffixText != null) {
                    pack.addChild(this.suffixText, Direction.REVERSE);
                } else {
                    pack.addChild(SpacerElement.width(suffixColumn), Direction.REVERSE);
                }
                pack.addChild(SpacerElement.width(SettingsListLayout.SUFFIX_GAP), Direction.REVERSE);
            }
            pack.addChild(this.control, Direction.REVERSE);
            this.pack = pack;
            return height;
        }

        @Override
        void position(int x, int y, int viewportTop, int viewportBottom) {
            this.pack.setPosition(x, y);
            this.visitWidgets(widget -> {
                int top = visualY(widget);
                widget.visible = SettingsListLayout.fullyVisible(top, top + visualHeight(widget), viewportTop, viewportBottom);
            });
        }

        @Override
        void renderEntry(SettingsListWidget list, GuiGraphicsExtractor context,
                         int mouseX, int mouseY, float deltaTicks) {
            if (this.labelText == null) {
                return;
            }
            list.renderPart(context, this.labelText, mouseX, mouseY, deltaTicks);
            if (this.suffixText != null) {
                list.renderPart(context, this.suffixText, mouseX, mouseY, deltaTicks);
            }
            list.renderPart(context, this.controlRenderer, mouseX, mouseY, deltaTicks);
            if (this.action != null) {
                list.renderPart(context, this.action, mouseX, mouseY, deltaTicks);
            }
        }

        @Override
        int preferredWidth(SettingsListWidget list) {
            return SettingsListLayout.rowPreferredWidth(list.font.width(this.label), visualWidth(this.control),
                    list.suffixColumnWidth, list.actionColumnWidth);
        }

        @Override
        void visitWidgets(Consumer<AbstractWidget> consumer) {
            this.control.visitWidgets(consumer);
            if (this.action != null) {
                this.action.visitWidgets(consumer);
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.widgets.PaddingWidgetContractTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Compile an older target**

The widget uses version-dependent render and tooltip calls. Run:
`./gradlew :mods:1.20.1-fabric:compileJava :mods:1.21.6-fabric:compileJava --console=plain`

Expected: `BUILD SUCCESSFUL`. If a swap or branch fails, compare with `IconListWidget`, which uses the
same render and tooltip patterns.

- [ ] **Step 6: Document the widget in the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
| Scrollable hierarchical rows | Extend `TreeViewWidget<T>` |
```
With:
```markdown
| Scrollable hierarchical rows | Extend `TreeViewWidget<T>` |
| Scrollable settings rows with section headers | `SettingsListWidget` |
```

Then replace:
```markdown
### New interactive widget checklist
```
With:
```markdown
### Settings lists

Use `SettingsListWidget` for a scrollable panel of settings. It holds `SettingsListWidget.Header`
entries (a title followed by a line) and `SettingsListWidget.Row` entries: a label, a control, an
optional muted unit (`suffix`), an optional last-column widget (`action`, such as a reset button)
and an optional `tooltip` supplier. Entries have their own heights, so long labels wrap onto more
lines instead of being clipped. The unit and action columns are as wide as their widest entry, so
every control's right edge lines up.

- **Layout:** `setEntries` copies the entries and lays them out. Call `relayout()` after a label,
  unit or control size changes. `getPreferredWidth()` is the width at which nothing wraps, including
  the always-reserved scrollbar column; `getContentHeight()` is the total entry height. The widget's
  bounds are the rows' area; its `Padding` adds `PANEL_PADDING` on each side for the themed panel,
  so size it with `setVisualWidth`/`setVisualHeight` and position it `PANEL_PADDING` inside the
  panel. The pure geometry lives in `SettingsListLayout`.
- **Registration:** call `list.visitWidgets(this::addRenderableWidget)`. It visits every row's
  control and action, then the list itself; that order keeps the list from taking clicks meant for
  its rows. The list renders all of them once, inside its scissor. Don't render row widgets again.
- **Visibility:** the list owns `visible` for its row widgets. A widget is visible only while it's
  entirely inside the rows' area, so clipped parts can't be clicked or focused. Partly visible
  widgets are still drawn, clipped, with no hover state. Screens set only `active`.
- **Input:** offer the mouse wheel to the list before `super.mouseScrolled`, so it scrolls while the
  list overflows and reaches a slider under the cursor only when it doesn't. The list isn't a Tab
  stop. After a key press moves focus to a row widget, call `reveal(focused)`: vanilla Tab skips
  invisible widgets, and `reveal` scrolls the row and its interactive neighbors into view. If
  scrolling hides the focused widget, clear focus.
- **Tooltips:** the hovered row gets `ROW_HOVER_BACKGROUND`. After the pointer rests on a row for
  500 ms, the list schedules the row's tooltip at the cursor, except over the row's action, which
  keeps its own vanilla tooltip.
- **Limitations:** row controls can't open popups, because the scissor would clip them.

### New interactive widget checklist
```

- [ ] **Step 7: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `grep -n "//?\|/\*?\|\*///?}" mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java`
→ exactly one `//? if >=1.21.6 {`, one `//?} else {` and one `*///?}`.

---

### Task 4: Settings model

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/ClientConfig.java`
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSettings.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSettingsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `ClientConfig` constants: `DEFAULT_ENABLE_WAYPOINT_RENDER` (`true`),
    `DEFAULT_WAYPOINT_SCALING_FACTOR` (`100`), `DEFAULT_WAYPOINT_VERTICAL_OFFSET` (`0`),
    `DEFAULT_WAYPOINT_BACKGROUND_ALPHA` (`128`), `DEFAULT_VIEW_DISTANCE` (`12`),
    `DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP` (`true`), `DEFAULT_AUTO_SYNC_TO_VOXELMAP` (`true`).
  - Package-private `final class ClientConfigSettings` with:
    - `sealed interface Setting permits IntSetting, BooleanSetting` with `SettingText text()`,
      `Component defaultText()`, `boolean isDefault(ClientConfig)`, `void reset(ClientConfig)`.
    - `record IntSetting(SettingText text, int min, int max, int defaultValue, ValueFormat format,
      ToIntFunction<ClientConfig> getter, ObjIntConsumer<ClientConfig> setter)` with
      `int get(ClientConfig)` and `void set(ClientConfig, int)`.
    - `record BooleanSetting(SettingText text, boolean defaultValue, Predicate<ClientConfig> getter,
      BiConsumer<ClientConfig, Boolean> setter)` with `boolean get(ClientConfig)` and
      `void set(ClientConfig, boolean)`.
    - `record SettingText(String labelKey, String descriptionKey, @Nullable String argumentKey)`
      with `Component label()` and `Component description()`.
    - `enum ValueFormat { PLAIN, PERCENT, CHUNKS }` with `@Nullable Component unit()` and
      `Component value(int)`.
    - Constants `SHOW_WAYPOINTS`, `SCALE`, `VERTICAL_OFFSET`, `BACKGROUND_OPACITY`,
      `LOCAL_WAYPOINT_RANGE`, `List<Setting> RENDERING` (in screen order) and
      `List<UploadTarget> MAP_MODS` (`XAERO`, `VOXELMAP`).
    - `static BooleanSetting autoSync(UploadTarget)`, `static String mapModNameKey(UploadTarget)`,
      `static List<Setting> forScreen(Set<UploadTarget> installedMapMods)`,
      `static void resetAll(ClientConfig, List<Setting>)`,
      `static boolean allDefault(ClientConfig, List<Setting>)` and `static Component onOff(boolean)`.

- [ ] **Step 1: Write the failing test**

Create `ClientConfigSettingsTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rendering setters load {@code OptimizedWaypointRenderer}, which reads the Minecraft font when
 * its class loads, so only the auto-sync settings are reset here. Rendering resets are checked in game.
 */
class ClientConfigSettingsTest {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @AfterEach
    void clearLoadedMapMods() {
        ClientConfig.isXaerosMinimapLoaded = false;
        ClientConfig.isVoxelMapLoaded = false;
    }

    @Test
    void everyIntegerDefaultIsInsideItsRange() {
        for (ClientConfigSettings.Setting setting : ClientConfigSettings.RENDERING) {
            if (setting instanceof ClientConfigSettings.IntSetting intSetting) {
                assertTrue(intSetting.min() <= intSetting.defaultValue()
                        && intSetting.defaultValue() <= intSetting.max(), intSetting.text().labelKey());
            }
        }
    }

    @Test
    void aConfigParsedFromEmptyJsonIsAtEveryDefault() {
        ClientConfig.isXaerosMinimapLoaded = true;
        ClientConfig.isVoxelMapLoaded = true;
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        List<ClientConfigSettings.Setting> settings =
                ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO, UploadTarget.VOXELMAP));

        for (ClientConfigSettings.Setting setting : settings) {
            assertTrue(setting.isDefault(config), setting.text().labelKey());
        }
        assertTrue(ClientConfigSettings.allDefault(config, settings));
    }

    @Test
    void aChangedValueIsNotAtItsDefault() {
        ClientConfig config = GSON.fromJson("{\"waypointScalingFactor\": 150}", ClientConfig.class);

        assertFalse(ClientConfigSettings.SCALE.isDefault(config));
        assertTrue(ClientConfigSettings.LOCAL_WAYPOINT_RANGE.isDefault(config));
        assertFalse(ClientConfigSettings.allDefault(config, ClientConfigSettings.RENDERING));
    }

    @Test
    void resettingAutoSyncRestoresItAndKeepsTheManagerState() {
        ClientConfig.isXaerosMinimapLoaded = true;
        ClientConfig config = GSON.fromJson("""
                {
                  "autoSyncToXaerosMinimap": false,
                  "waypointManagerSortMode": "NAME",
                  "waypointManagerSortReversed": true,
                  "waypointManagerGroupByLists": false,
                  "waypointManagerShowAllDimensions": true
                }
                """, ClientConfig.class);
        ClientConfigSettings.BooleanSetting autoSync = ClientConfigSettings.autoSync(UploadTarget.XAERO);
        assertFalse(autoSync.isDefault(config));

        ClientConfigSettings.resetAll(config, List.of(autoSync));

        assertTrue(autoSync.isDefault(config));
        assertEquals(WaypointSorting.SortMode.NAME, config.getWaypointManagerSortMode());
        assertTrue(config.isWaypointManagerSortReversed());
        assertFalse(config.isWaypointManagerGroupByLists());
        assertTrue(config.isWaypointManagerShowAllDimensions());
    }

    @Test
    void settingsOfMissingMapModsAreLeftOut() {
        assertEquals(ClientConfigSettings.RENDERING, ClientConfigSettings.forScreen(Set.of()));

        List<ClientConfigSettings.Setting> withXaero = ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO));

        assertEquals(ClientConfigSettings.RENDERING.size() + 1, withXaero.size());
        assertEquals("server_waypoint.map_mod.xaeros_minimap",
                withXaero.get(withXaero.size() - 1).text().argumentKey());
    }

    @Test
    void defaultsAreFormattedWithTheirUnits() {
        assertTranslation(ClientConfigSettings.SCALE.defaultText(), "server_waypoint.config.value.percent", 100);
        assertTranslation(ClientConfigSettings.LOCAL_WAYPOINT_RANGE.defaultText(), "server_waypoint.config.value.chunks", 12);
        assertEquals("128", ClientConfigSettings.BACKGROUND_OPACITY.defaultText().getString());
        assertTranslation(ClientConfigSettings.SHOW_WAYPOINTS.defaultText(), "server_waypoint.config.on");
        assertTranslation(ClientConfigSettings.ValueFormat.CHUNKS.unit(), "server_waypoint.config.unit.chunks");
    }

    @Test
    void mapModTextNamesTheMapMod() {
        Component label = ClientConfigSettings.autoSync(UploadTarget.VOXELMAP).text().label();
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, label.getContents());

        assertEquals("server_waypoint.config.map_mod.auto_sync", contents.getKey());
        Component name = assertInstanceOf(Component.class, contents.getArgs()[0]);
        assertTranslation(name, "server_waypoint.map_mod.voxelmap");
    }

    private static void assertTranslation(Component component, String key, Object... args) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals(key, contents.getKey());
        assertArrayEquals(args, contents.getArgs());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigSettingsTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: compilation fails with `cannot find symbol` for `ClientConfigSettings`.

- [ ] **Step 3: Add the default constants**

In `ClientConfig.java`, replace:
```java
public class ClientConfig {
    @Expose private boolean enableWaypointRender = true;
    @Expose private int waypointScalingFactor = 100; // in percent
    @Expose private int waypointVerticalOffset = 0; // [-100, 100] in percent
    @Expose private int waypointBackgroundAlpha = 0x80; // [0, 255]
    @Expose private int viewDistance = 12;
    @Expose private boolean autoSyncToXaerosMinimap = true;
    @Expose private boolean autoSyncToVoxelMap = true;
```
With:
```java
public class ClientConfig {
    public static final boolean DEFAULT_ENABLE_WAYPOINT_RENDER = true;
    public static final int DEFAULT_WAYPOINT_SCALING_FACTOR = 100;
    public static final int DEFAULT_WAYPOINT_VERTICAL_OFFSET = 0;
    public static final int DEFAULT_WAYPOINT_BACKGROUND_ALPHA = 128;
    public static final int DEFAULT_VIEW_DISTANCE = 12;
    public static final boolean DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP = true;
    public static final boolean DEFAULT_AUTO_SYNC_TO_VOXELMAP = true;

    @Expose private boolean enableWaypointRender = DEFAULT_ENABLE_WAYPOINT_RENDER;
    @Expose private int waypointScalingFactor = DEFAULT_WAYPOINT_SCALING_FACTOR; // in percent
    @Expose private int waypointVerticalOffset = DEFAULT_WAYPOINT_VERTICAL_OFFSET; // [-100, 100] in percent
    @Expose private int waypointBackgroundAlpha = DEFAULT_WAYPOINT_BACKGROUND_ALPHA; // [0, 255]
    @Expose private int viewDistance = DEFAULT_VIEW_DISTANCE;
    @Expose private boolean autoSyncToXaerosMinimap = DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP;
    @Expose private boolean autoSyncToVoxelMap = DEFAULT_AUTO_SYNC_TO_VOXELMAP;
```

- [ ] **Step 4: Create the model**

Create `ClientConfigSettings.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.core.network.upload.UploadTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The client settings shown on {@link ClientConfigScreen}: their text, ranges and defaults, and how
 * each reads and writes {@link ClientConfig}. The setters apply changes to the renderer immediately.
 */
final class ClientConfigSettings {
    private static final String KEY_PREFIX = "server_waypoint.config.";

    static final BooleanSetting SHOW_WAYPOINTS = new BooleanSetting(
            SettingText.of("enable_waypoint_render"),
            ClientConfig.DEFAULT_ENABLE_WAYPOINT_RENDER,
            ClientConfig::isEnableWaypointRender,
            ClientConfig::setEnableWaypointRender
    );
    static final IntSetting SCALE = new IntSetting(
            SettingText.of("waypoint_scale_factor"),
            0,
            500,
            ClientConfig.DEFAULT_WAYPOINT_SCALING_FACTOR,
            ValueFormat.PERCENT,
            ClientConfig::getWaypointScalingFactor,
            ClientConfig::setWaypointScalingFactor
    );
    static final IntSetting VERTICAL_OFFSET = new IntSetting(
            SettingText.of("waypoint_vertical_offset"),
            -100,
            100,
            ClientConfig.DEFAULT_WAYPOINT_VERTICAL_OFFSET,
            ValueFormat.PERCENT,
            ClientConfig::getWaypointVerticalOffset,
            ClientConfig::setWaypointVerticalOffset
    );
    static final IntSetting BACKGROUND_OPACITY = new IntSetting(
            SettingText.of("waypoint_bg_opacity"),
            0,
            255,
            ClientConfig.DEFAULT_WAYPOINT_BACKGROUND_ALPHA,
            ValueFormat.PLAIN,
            ClientConfig::getWaypointBackgroundAlpha,
            ClientConfig::setWaypointBackgroundAlpha
    );
    static final IntSetting LOCAL_WAYPOINT_RANGE = new IntSetting(
            SettingText.of("local_waypoint_view_distance"),
            0,
            1024,
            ClientConfig.DEFAULT_VIEW_DISTANCE,
            ValueFormat.CHUNKS,
            ClientConfig::getViewDistance,
            ClientConfig::setViewDistance
    );
    /** The Waypoint rendering section, in screen order. */
    static final List<Setting> RENDERING = List.of(
            SHOW_WAYPOINTS, SCALE, VERTICAL_OFFSET, BACKGROUND_OPACITY, LOCAL_WAYPOINT_RANGE);
    /** The map mods the Map mods section lists, in screen order. */
    static final List<UploadTarget> MAP_MODS = List.of(UploadTarget.XAERO, UploadTarget.VOXELMAP);

    private ClientConfigSettings() {
    }

    static BooleanSetting autoSync(UploadTarget target) {
        SettingText text = SettingText.forMapMod("map_mod.auto_sync", target);
        return switch (target) {
            case XAERO -> new BooleanSetting(text, ClientConfig.DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP,
                    ClientConfig::isAutoSyncToXaerosMinimap, ClientConfig::setAutoSyncToXaerosMinimap);
            case VOXELMAP -> new BooleanSetting(text, ClientConfig.DEFAULT_AUTO_SYNC_TO_VOXELMAP,
                    ClientConfig::isAutoSyncToVoxelMap, ClientConfig::setAutoSyncToVoxelMap);
        };
    }

    static String mapModNameKey(UploadTarget target) {
        return switch (target) {
            case XAERO -> "server_waypoint.map_mod.xaeros_minimap";
            case VOXELMAP -> "server_waypoint.map_mod.voxelmap";
        };
    }

    /** The rendering settings, then auto sync for each installed map mod in {@link #MAP_MODS} order. */
    static List<Setting> forScreen(Set<UploadTarget> installedMapMods) {
        List<Setting> settings = new ArrayList<>(RENDERING);
        for (UploadTarget target : MAP_MODS) {
            if (installedMapMods.contains(target)) {
                settings.add(autoSync(target));
            }
        }
        return List.copyOf(settings);
    }

    static void resetAll(ClientConfig config, List<Setting> settings) {
        for (Setting setting : settings) {
            setting.reset(config);
        }
    }

    static boolean allDefault(ClientConfig config, List<Setting> settings) {
        for (Setting setting : settings) {
            if (!setting.isDefault(config)) {
                return false;
            }
        }
        return true;
    }

    static Component onOff(boolean value) {
        return Component.translatable(KEY_PREFIX + (value ? "on" : "off"));
    }

    /** A setting shown as one row. */
    sealed interface Setting permits IntSetting, BooleanSetting {
        SettingText text();

        /** The default as the row shows it, such as "100%", "12 chunks" or "On". */
        Component defaultText();

        boolean isDefault(ClientConfig config);

        /** Restores the default through the setter, so the change applies immediately. */
        void reset(ClientConfig config);
    }

    record IntSetting(SettingText text, int min, int max, int defaultValue, ValueFormat format,
                      ToIntFunction<ClientConfig> getter, ObjIntConsumer<ClientConfig> setter) implements Setting {
        int get(ClientConfig config) {
            return this.getter.applyAsInt(config);
        }

        void set(ClientConfig config, int value) {
            this.setter.accept(config, value);
        }

        @Override
        public Component defaultText() {
            return this.format.value(this.defaultValue);
        }

        @Override
        public boolean isDefault(ClientConfig config) {
            return this.get(config) == this.defaultValue;
        }

        @Override
        public void reset(ClientConfig config) {
            this.set(config, this.defaultValue);
        }
    }

    record BooleanSetting(SettingText text, boolean defaultValue, Predicate<ClientConfig> getter,
                          BiConsumer<ClientConfig, Boolean> setter) implements Setting {
        boolean get(ClientConfig config) {
            return this.getter.test(config);
        }

        void set(ClientConfig config, boolean value) {
            this.setter.accept(config, value);
        }

        @Override
        public Component defaultText() {
            return onOff(this.defaultValue);
        }

        @Override
        public boolean isDefault(ClientConfig config) {
            return this.get(config) == this.defaultValue;
        }

        @Override
        public void reset(ClientConfig config) {
            this.set(config, this.defaultValue);
        }
    }

    /** Translation keys for a setting. {@code argumentKey} names the map mod in per-mod text. */
    record SettingText(String labelKey, String descriptionKey, @Nullable String argumentKey) {
        static SettingText of(String key) {
            return new SettingText(KEY_PREFIX + key, KEY_PREFIX + key + ".tooltip", null);
        }

        static SettingText forMapMod(String key, UploadTarget target) {
            return new SettingText(KEY_PREFIX + key, KEY_PREFIX + key + ".tooltip", mapModNameKey(target));
        }

        Component label() {
            return this.translate(this.labelKey);
        }

        Component description() {
            return this.translate(this.descriptionKey);
        }

        private Component translate(String key) {
            return this.argumentKey == null
                    ? Component.translatable(key)
                    : Component.translatable(key, Component.translatable(this.argumentKey));
        }
    }

    /** How an integer setting shows its unit and its default. */
    enum ValueFormat {
        PLAIN(null, null),
        PERCENT(KEY_PREFIX + "unit.percent", KEY_PREFIX + "value.percent"),
        CHUNKS(KEY_PREFIX + "unit.chunks", KEY_PREFIX + "value.chunks");

        private final @Nullable String unitKey;
        private final @Nullable String valueKey;

        ValueFormat(@Nullable String unitKey, @Nullable String valueKey) {
            this.unitKey = unitKey;
            this.valueKey = valueKey;
        }

        /** The unit shown after the control, or null for a plain number. */
        @Nullable Component unit() {
            return this.unitKey == null ? null : Component.translatable(this.unitKey);
        }

        Component value(int value) {
            return this.valueKey == null
                    ? Component.literal(Integer.toString(value))
                    : Component.translatable(this.valueKey, value);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigSettingsTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`; all seven tests pass.

- [ ] **Step 6: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `git status --short` → `ClientConfig.java` is modified; the model and its test are new.

---

### Task 5: Map-mod API and sync rules

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/integrations/MapModIntegration.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/integrations/MapModIntegrations.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/integrations/XaerosMinimapIntegration.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/integrations/VoxelMapIntegration.java`
- Modify: `mods/src/main/java/_959/server_waypoint/mixin/xaeros_minimap/MinimapWorldStateUpdaterMixin.java`
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSync.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java` (one call, until Task 8 replaces the file)
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSyncTest.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/integrations/MapModIntegrationsTest.java`

**Interfaces:**
- Consumes: `WaypointManagerScreen.ManagerViewState` (package-private enum: `LOADING`,
  `UNSUPPORTED`, `INCOMPATIBLE`, `READY`).
- Produces:
  - `MapModIntegration`: `boolean isInstalled()`, `boolean isReady()`,
    `void syncAll(WaypointClientMod waypointClientMod)`.
  - `MapModIntegrations.find(UploadTarget)` returning `Optional<MapModIntegration>` (replaces
    `findUploadCollector`), and `MapModIntegrations.syncNow(UploadTarget, WaypointClientMod)`
    returning `boolean` (replaces `syncXaerosMinimap`).
  - Package-private `final class ClientConfigSync` with `enum MapModRowState { HIDDEN,
    NOT_INSTALLED, INSTALLED }`, `enum SyncBlocker { NO_WORLD, WAYPOINTS_LOADING,
    NO_SERVERSIDE_SUPPORT, INCOMPATIBLE_SERVER, MAP_MOD_LOADING }` with `String messageKey()` and
    `boolean namesMapMod()`, `static MapModRowState resolveMapModRowState(boolean supported, boolean
    installed)` and `static @Nullable SyncBlocker resolveSyncBlocker(boolean inWorld,
    WaypointManagerScreen.ManagerViewState waypointState, boolean mapModReady)`.

- [ ] **Step 1: Write the failing tests**

Create `ClientConfigSyncTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.ClientConfigSync.MapModRowState;
import _959.server_waypoint.common.client.gui.screens.ClientConfigSync.SyncBlocker;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen.ManagerViewState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigSyncTest {
    @Test
    void mapModRowsFollowSupportAndInstallation() {
        assertEquals(MapModRowState.HIDDEN, ClientConfigSync.resolveMapModRowState(false, false));
        assertEquals(MapModRowState.HIDDEN, ClientConfigSync.resolveMapModRowState(false, true));
        assertEquals(MapModRowState.NOT_INSTALLED, ClientConfigSync.resolveMapModRowState(true, false));
        assertEquals(MapModRowState.INSTALLED, ClientConfigSync.resolveMapModRowState(true, true));
    }

    @Test
    void noWorldWinsOverEveryOtherBlocker() {
        for (ManagerViewState state : ManagerViewState.values()) {
            assertEquals(SyncBlocker.NO_WORLD, ClientConfigSync.resolveSyncBlocker(false, state, false));
            assertEquals(SyncBlocker.NO_WORLD, ClientConfigSync.resolveSyncBlocker(false, state, true));
        }
    }

    @Test
    void waypointStatesBlockBeforeTheMapMod() {
        assertEquals(SyncBlocker.WAYPOINTS_LOADING,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.LOADING, false));
        assertEquals(SyncBlocker.NO_SERVERSIDE_SUPPORT,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.UNSUPPORTED, false));
        assertEquals(SyncBlocker.INCOMPATIBLE_SERVER,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.INCOMPATIBLE, false));
    }

    @Test
    void aMapModThatIsNotReadyBlocksLast() {
        assertEquals(SyncBlocker.MAP_MOD_LOADING,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.READY, false));
    }

    @Test
    void aReadyMapModInAReadyWorldCanSync() {
        assertNull(ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.READY, true));
    }

    @Test
    void onlyTheMapModBlockerNamesTheMapMod() {
        for (SyncBlocker blocker : SyncBlocker.values()) {
            assertEquals(blocker == SyncBlocker.MAP_MOD_LOADING, blocker.namesMapMod(), blocker.name());
        }
        assertEquals("server_waypoint.manager.loading", SyncBlocker.WAYPOINTS_LOADING.messageKey());
        assertTrue(SyncBlocker.NO_WORLD.messageKey().startsWith("server_waypoint.config.sync."));
        assertFalse(SyncBlocker.NO_SERVERSIDE_SUPPORT.namesMapMod());
    }
}
```

Create `MapModIntegrationsTest.java`:

```java
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.core.network.upload.UploadTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapModIntegrationsTest {
    @AfterEach
    void clearLoadedMapMods() {
        ClientConfig.isXaerosMinimapLoaded = false;
        ClientConfig.isVoxelMapLoaded = false;
    }

    @Test
    void xaerosMinimapIsSupportedOnEveryLoader() {
        assertTrue(MapModIntegrations.find(UploadTarget.XAERO).isPresent());
    }

    @Test
    void voxelMapIsSupportedOnlyOnFabric() {
        //? if fabric {
        assertTrue(MapModIntegrations.find(UploadTarget.VOXELMAP).isPresent());
        //?} else {
        /*assertTrue(MapModIntegrations.find(UploadTarget.VOXELMAP).isEmpty());
        *///?}
    }

    @Test
    void installedStateFollowsTheLoadedFlag() {
        ClientConfig.isXaerosMinimapLoaded = true;
        assertTrue(MapModIntegrations.find(UploadTarget.XAERO).orElseThrow().isInstalled());

        ClientConfig.isXaerosMinimapLoaded = false;
        assertFalse(MapModIntegrations.find(UploadTarget.XAERO).orElseThrow().isInstalled());
    }

    @Test
    void syncNowSkipsAMapModThatIsNotInstalled() {
        assertFalse(MapModIntegrations.syncNow(UploadTarget.XAERO, null));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigSyncTest" --tests "_959.server_waypoint.common.client.integrations.MapModIntegrationsTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: compilation fails with `cannot find symbol` for `ClientConfigSync` and `find`.

- [ ] **Step 3: Extend `MapModIntegration`**

In `MapModIntegration.java`, replace:
```java
    void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod);
}
```
With:
```java
    void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod);

    /** Whether the map mod is installed in this game. */
    boolean isInstalled();

    /** Whether the map mod can take waypoints now. */
    boolean isReady();

    /** Rewrites the waypoints this mod added to the map mod so they match the synced waypoints. */
    void syncAll(WaypointClientMod waypointClientMod);
}
```

- [ ] **Step 4: Implement it for Xaero's Minimap**

In `XaerosMinimapIntegration.java`, replace:
```java
    @Override
    public boolean isEnabled(ClientConfig clientConfig) {
        return clientConfig.isAutoSyncToXaerosMinimap() && WaypointClientMod.isXaerosMinimapReady;
    }
```
With:
```java
    @Override
    public boolean isEnabled(ClientConfig clientConfig) {
        return clientConfig.isAutoSyncToXaerosMinimap() && this.isReady();
    }

    @Override
    public boolean isInstalled() {
        return ClientConfig.isXaerosMinimapLoaded;
    }

    @Override
    public boolean isReady() {
        return WaypointClientMod.isXaerosMinimapReady;
    }

    @Override
    public void syncAll(WaypointClientMod waypointClientMod) {
        XaerosMinimapWaypointHelper.replaceAll(waypointClientMod);
    }
```

- [ ] **Step 5: Implement it for VoxelMap**

In `VoxelMapIntegration.java`, replace:
```java
    @Override
    public boolean isEnabled(ClientConfig clientConfig) {
        return clientConfig.isAutoSyncToVoxelMap();
    }
```
With:
```java
    @Override
    public boolean isEnabled(ClientConfig clientConfig) {
        return clientConfig.isAutoSyncToVoxelMap();
    }

    @Override
    public boolean isInstalled() {
        return ClientConfig.isVoxelMapLoaded;
    }

    @Override
    public boolean isReady() {
        // Auto sync writes to VoxelMap as soon as waypoints arrive, so being in a world is enough.
        return true;
    }

    @Override
    public void syncAll(WaypointClientMod waypointClientMod) {
        VoxelMapWaypointHelper.replaceAll(waypointClientMod);
    }
```

- [ ] **Step 6: Replace the lookups in `MapModIntegrations`**

In `MapModIntegrations.java`, replace:
```java
    public static Optional<MapModIntegration> findUploadCollector(UploadTarget target) {
        return INTEGRATIONS.stream()
                .filter(integration -> integration.uploadTarget() == target)
                .findFirst();
    }

    /** Collects a detached snapshot; callers must run this on the Minecraft client thread. */
    public static WaypointData collectUpload(UploadRequestBuffer request) {
        return findUploadCollector(request.target())
```
With:
```java
    /** The integration for {@code target}, or empty when this loader has none. */
    public static Optional<MapModIntegration> find(UploadTarget target) {
        return INTEGRATIONS.stream()
                .filter(integration -> integration.uploadTarget() == target)
                .findFirst();
    }

    /** Collects a detached snapshot; callers must run this on the Minecraft client thread. */
    public static WaypointData collectUpload(UploadRequestBuffer request) {
        return find(request.target())
```

Then replace:
```java
    public static void syncXaerosMinimap(WaypointClientMod waypointClientMod) {
        if (!WaypointClientMod.isXaerosMinimapReady) {
            return;
        }
        XaerosMinimapWaypointHelper.replaceAll(waypointClientMod);
    }
```
With:
```java
    /**
     * Rewrites the waypoints this mod added to a map mod, if this loader supports it and it's
     * installed and ready. Returns whether it ran.
     */
    public static boolean syncNow(UploadTarget target, WaypointClientMod waypointClientMod) {
        Optional<MapModIntegration> integration = find(target)
                .filter(MapModIntegration::isInstalled)
                .filter(MapModIntegration::isReady);
        integration.ifPresent(mapMod -> mapMod.syncAll(waypointClientMod));
        return integration.isPresent();
    }
```

- [ ] **Step 7: Update the Xaero's Minimap readiness mixin**

In `MinimapWorldStateUpdaterMixin.java`, replace:
```java
import org.spongepowered.asm.mixin.Mixin;
```
With:
```java
import _959.server_waypoint.core.network.upload.UploadTarget;
import org.spongepowered.asm.mixin.Mixin;
```

Replace:
```java
import static _959.server_waypoint.common.client.integrations.MapModIntegrations.syncXaerosMinimap;
```
With:
```java
import static _959.server_waypoint.common.client.integrations.MapModIntegrations.syncNow;
```

Replace:
```java
            syncXaerosMinimap(getInstance());
```
With:
```java
            syncNow(UploadTarget.XAERO, getInstance());
```

- [ ] **Step 8: Fix the remaining caller**

Run: `grep -rn "syncXaerosMinimap\|findUploadCollector" mods/src tools`
Expected: exactly one hit, in `ClientConfigScreen.java` (`runXaerosSync`).

Fix it now so the build stays green until Task 8 rewrites the screen. In `ClientConfigScreen.java`,
replace:
```java
            MapModIntegrations.syncXaerosMinimap(WaypointClientMod.getInstance());
```
With:
```java
            MapModIntegrations.syncNow(_959.server_waypoint.core.network.upload.UploadTarget.XAERO, WaypointClientMod.getInstance());
```
Then run the `grep` again. Expected: no output.

- [ ] **Step 9: Create the sync rules**

Create `ClientConfigSync.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen.ManagerViewState;
import org.jetbrains.annotations.Nullable;

/** Pure rules for the Map mods section of {@link ClientConfigScreen}. */
final class ClientConfigSync {
    private ClientConfigSync() {
    }

    /** Which rows a map mod gets. */
    enum MapModRowState {
        /** This loader has no integration for the map mod: no rows. */
        HIDDEN,
        /** Supported but not installed: one muted "Not installed" row. */
        NOT_INSTALLED,
        /** Supported and installed: the auto-sync and sync-now rows. */
        INSTALLED
    }

    /** Why a map mod can't sync now; its message explains the disabled Sync button. */
    enum SyncBlocker {
        NO_WORLD("server_waypoint.config.sync.no_world"),
        WAYPOINTS_LOADING("server_waypoint.manager.loading"),
        NO_SERVERSIDE_SUPPORT("server_waypoint.no_serverside_support"),
        INCOMPATIBLE_SERVER("server_waypoint.incompatible_protocol_version"),
        MAP_MOD_LOADING("server_waypoint.config.sync.map_mod_loading");

        private final String messageKey;

        SyncBlocker(String messageKey) {
            this.messageKey = messageKey;
        }

        String messageKey() {
            return this.messageKey;
        }

        /** Whether the message takes the map mod's name as its {@code %s} argument. */
        boolean namesMapMod() {
            return this == MAP_MOD_LOADING;
        }
    }

    static MapModRowState resolveMapModRowState(boolean supported, boolean installed) {
        if (!supported) {
            return MapModRowState.HIDDEN;
        }
        return installed ? MapModRowState.INSTALLED : MapModRowState.NOT_INSTALLED;
    }

    /**
     * The first reason a map mod can't sync, or null when it can. The world check comes first
     * because {@code isXaerosMinimapReady} stays true after the player leaves a world.
     */
    static @Nullable SyncBlocker resolveSyncBlocker(boolean inWorld, ManagerViewState waypointState, boolean mapModReady) {
        if (!inWorld) {
            return SyncBlocker.NO_WORLD;
        }
        SyncBlocker waypointBlocker = switch (waypointState) {
            case LOADING -> SyncBlocker.WAYPOINTS_LOADING;
            case UNSUPPORTED -> SyncBlocker.NO_SERVERSIDE_SUPPORT;
            case INCOMPATIBLE -> SyncBlocker.INCOMPATIBLE_SERVER;
            case READY -> null;
        };
        if (waypointBlocker != null) {
            return waypointBlocker;
        }
        return mapModReady ? null : SyncBlocker.MAP_MOD_LOADING;
    }
}
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigSyncTest" --tests "_959.server_waypoint.common.client.integrations.MapModIntegrationsTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`; six sync tests and four integration tests pass.

- [ ] **Step 11: Compile a NeoForge target**

The mixin and integrations are loader-shared. Run:
`./gradlew :mods:26.3-neoforge:compileJava --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `git status --short` → the five integration and mixin files and `ClientConfigScreen.java` are
modified; `ClientConfigSync.java` and both tests are new.

---

### Task 6: Dialog buttons, On/Off toggle and reset icon

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/DialogWidget.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ConfirmationDialog.java`
- Delete: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TrueFalseToggleButton.java`
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/OnOffToggleButton.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WidgetThemeConfigScreen.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WidgetTextures.java`
- Create: `mods/src/main/resources/assets/server_waypoint/textures/gui/reset.png`
- Modify: the six language files
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigTranslationTest.java` (new)
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `DialogWidget`'s only constructor is now
    `protected DialogWidget(int x, int y, Component title, WidgetStack content, List<AbstractWidget> buttons, Font textRenderer)`;
    the abstract `createButtons()` is gone.
  - `ConfirmationDialog(int, int, Component title, WidgetStack content, Runnable confirm, Runnable cancel, Font)`
    (unchanged, confirm reads "Confirm"), the new
    `ConfirmationDialog(int, int, Component title, WidgetStack content, Component confirmLabel, Runnable confirm, Runnable cancel, Font)`,
    and `TranslucentButton getCancelButton()`. Buttons are `max(50, text width + 10)` wide.
  - `OnOffToggleButton(int x, int y, ToggleButtonCallback callback)`, replacing `TrueFalseToggleButton`.
  - `WidgetTextures.RESET_ICON`, a 9×9 texture.
  - Translation keys `server_waypoint.config.on` and `server_waypoint.config.off`;
    `server_waypoint.config.true` and `server_waypoint.config.false` are removed.
  - `ClientConfigTranslationTest` with the lists `SCREEN_KEYS` and `RETIRED_KEYS`, which Tasks 7 and
    8 extend.

- [ ] **Step 1: Write the failing translation test**

Create `ClientConfigTranslationTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");
    private static final List<String> SCREEN_KEYS = List.of(
            "server_waypoint.config.on",
            "server_waypoint.config.off"
    );
    private static final List<String> RETIRED_KEYS = List.of(
            "server_waypoint.config.true",
            "server_waypoint.config.false"
    );

    @Test
    void allSixLocalesDefineTheScreenKeysWithMatchingPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : SCREEN_KEYS) {
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    @Test
    void retiredKeysAreRemovedFromEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : RETIRED_KEYS) {
                assertFalse(translated.has(key), locale + ": " + key);
            }
        }
    }

    private JsonObject read(String locale) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/server_waypoint/lang/" + locale + ".json");
        assertNotNull(stream, locale);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static long placeholders(String value) {
        return PLACEHOLDER.matcher(value).results().count();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigTranslationTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: both tests fail: `en_us: server_waypoint.config.on` and `en_us: server_waypoint.config.true`.

- [ ] **Step 3: Replace True/False with On/Off in all six locales**

In `en_us.json`, replace:
```json
  "server_waypoint.config.true": "True",
  "server_waypoint.config.false": "False",
```
With:
```json
  "server_waypoint.config.on": "On",
  "server_waypoint.config.off": "Off",
```

In `es_es.json`, replace:
```json
  "server_waypoint.config.true": "Verdadero",
  "server_waypoint.config.false": "Falso",
```
With:
```json
  "server_waypoint.config.on": "Sí",
  "server_waypoint.config.off": "No",
```

In `he_il.json`, replace:
```json
  "server_waypoint.config.true": "נכון",
  "server_waypoint.config.false": "לא נכון",
```
With:
```json
  "server_waypoint.config.on": "פועל",
  "server_waypoint.config.off": "כבוי",
```

In `zh_cn.json`, replace:
```json
  "server_waypoint.config.true": "是",
  "server_waypoint.config.false": "否",
```
With:
```json
  "server_waypoint.config.on": "开",
  "server_waypoint.config.off": "关",
```

In `zh_hk.json`, replace:
```json
  "server_waypoint.config.true": "是",
  "server_waypoint.config.false": "否",
```
With:
```json
  "server_waypoint.config.on": "開",
  "server_waypoint.config.off": "關",
```

In `zh_tw.json`, replace:
```json
  "server_waypoint.config.true": "是",
  "server_waypoint.config.false": "否",
```
With:
```json
  "server_waypoint.config.on": "開啟",
  "server_waypoint.config.off": "關閉",
```

- [ ] **Step 4: Rename the toggle**

Delete the old class: `rm mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TrueFalseToggleButton.java`

Create `OnOffToggleButton.java`:

```java
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ToggleButtonCallback;

import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS_BACKGROUND;

/** A 50×11 toggle that reads On or Off. */
public class OnOffToggleButton extends ToggleButton {
    public OnOffToggleButton(int x, int y, ToggleButtonCallback callback) {
        super(
                x,
                y,
                50,
                11,
                Component.translatable("server_waypoint.config.off"),
                Component.translatable("server_waypoint.config.on"),
                DANGER_BACKGROUND,
                SUCCESS_BACKGROUND,
                callback
        );
    }
}
```

In `WidgetThemeConfigScreen.java`, replace:
```java
import _959.server_waypoint.common.client.gui.widgets.TrueFalseToggleButton;
```
With:
```java
import _959.server_waypoint.common.client.gui.widgets.OnOffToggleButton;
```

Then replace:
```java
    private final TrueFalseToggleButton galleryBooleanToggle = new TrueFalseToggleButton(
```
With:
```java
    private final OnOffToggleButton galleryBooleanToggle = new OnOffToggleButton(
```

In `ClientConfigScreen.java`, replace every `new TrueFalseToggleButton(` with `new OnOffToggleButton(`
(two occurrences; the file imports `widgets.*`).

Run: `grep -rn "TrueFalseToggleButton" mods/src`
Expected: no output.

- [ ] **Step 5: Pass dialog buttons through the constructor**

In `DialogWidget.java`, replace:
```java
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
import org.jetbrains.annotations.Unmodifiable;
```
With:
```java
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
```

Replace:
```java
    public DialogWidget(int x, int y, Component title, WidgetStack content, Font textRenderer) {
        super(x, y, textRenderer.width(title), 0, title);
        this.textRenderer = textRenderer;
        this.title = title;
        this.content = content;
        this.mainLayout.addChild(new ScalableText(0, 0, this.title, 1.2F, TEXT_PRIMARY, this.textRenderer), 0);
        this.mainLayout.addChild(content);
        List<AbstractWidget> buttons = this.createButtons();
        this.buttonRow.addClickable(buttons.get(0), 0);
```
With:
```java
    /**
     * Lays out the title, the content and a row of {@code buttons} under them; the first button sits
     * on the right. Buttons come in through the constructor so a subclass can build them from its own
     * arguments.
     */
    protected DialogWidget(int x, int y, Component title, WidgetStack content, List<AbstractWidget> buttons, Font textRenderer) {
        super(x, y, textRenderer.width(title), 0, title);
        this.textRenderer = textRenderer;
        this.title = title;
        this.content = content;
        this.mainLayout.addChild(new ScalableText(0, 0, this.title, 1.2F, TEXT_PRIMARY, this.textRenderer), 0);
        this.mainLayout.addChild(content);
        this.buttonRow.addClickable(buttons.get(0), 0);
```

Replace:
```java
    abstract protected @Unmodifiable List<AbstractWidget> createButtons();

    @Override
    public int getWidth() {
```
With:
```java
    @Override
    public int getWidth() {
```

Replace the whole content of `ConfirmationDialog.java` with:

```java
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** A dialog with Cancel on the right and a confirm button to its left, each sized to fit its text. */
public class ConfirmationDialog extends DialogWidget {
    private static final int MIN_BUTTON_WIDTH = 50;
    private static final int BUTTON_TEXT_PADDING = 10;
    private static final int BUTTON_HEIGHT = 11;

    private final TranslucentButton cancelButton;

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content,
                              @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
        this(x, y, title, content, Component.translatable("server_waypoint.confirm.button"), confirm, cancel, textRenderer);
    }

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content, Component confirmLabel,
                              @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
        this(x, y, title, content,
                button(Component.translatable("server_waypoint.cancel.button"), cancel, textRenderer),
                button(confirmLabel, confirm, textRenderer),
                textRenderer);
    }

    private ConfirmationDialog(int x, int y, Component title, WidgetStack content,
                               TranslucentButton cancelButton, TranslucentButton confirmButton, Font textRenderer) {
        super(x, y, title, content, List.<AbstractWidget>of(cancelButton, confirmButton), textRenderer);
        this.cancelButton = cancelButton;
    }

    /** The Cancel button, so a screen can focus it when the dialog opens. */
    public TranslucentButton getCancelButton() {
        return this.cancelButton;
    }

    private static TranslucentButton button(Component label, Runnable action, Font font) {
        int width = Math.max(MIN_BUTTON_WIDTH, font.width(label) + BUTTON_TEXT_PADDING);
        return new TranslucentButton(0, 0, width, BUTTON_HEIGHT, label, action::run);
    }
}
```

- [ ] **Step 6: Add the reset icon**

Create the 9×9 texture, in the existing icons' color (`#D9D9D9`). From the repository root, run:

```bash
python3 - <<'PY'
import struct
import zlib

rows = [
    "...X.....",
    "..XXXX...",
    "...X..X..",
    ".......X.",
    ".X.....X.",
    ".X.....X.",
    "..X...X..",
    "...XXX...",
    ".........",
]
color = bytes((217, 217, 217, 255))
raw = b"".join(b"\x00" + b"".join(color if cell == "X" else bytes(4) for cell in row) for row in rows)

def chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", 9, 9, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw, 9))
       + chunk(b"IEND", b""))
with open("mods/src/main/resources/assets/server_waypoint/textures/gui/reset.png", "wb") as out:
    out.write(png)
PY
```

Run: `file mods/src/main/resources/assets/server_waypoint/textures/gui/reset.png`
Expected: `PNG image data, 9 x 9, 8-bit/color RGBA, non-interlaced`.

The pixels draw a counter-clockwise arrow (↺): an arc around the right side with a left-pointing
arrowhead at the top. `IconButton` draws its texture inside a 2-pixel inset, so a 13×13 button shows
it at exactly 9×9.

In `WidgetTextures.java`, replace:
```java
    LAN_SERVERS_ICON = modId("textures/gui/lan_servers.png");

    private WidgetTextures() {
```
With:
```java
    LAN_SERVERS_ICON = modId("textures/gui/lan_servers.png");

    public static final
    //$ resource_location_type_swap
    Identifier
    RESET_ICON = modId("textures/gui/reset.png");

    private WidgetTextures() {
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigTranslationTest" --tests "_959.server_waypoint.common.client.gui.widgets.PaddingWidgetContractTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
| Boolean state | `ToggleButton` or `TrueFalseToggleButton` |
```
With:
```markdown
| Boolean state | `ToggleButton` or `OnOffToggleButton` |
```

Then replace:
```markdown
### New interactive widget checklist
```
With:
```markdown
### Confirmation dialogs

`ConfirmationDialog` takes an optional confirm label; the shorter constructor keeps "Confirm". Both
buttons are at least 50 pixels wide and grow to fit their text, so longer translations don't
overflow. `getCancelButton()` returns the Cancel button so a screen can focus it when the dialog
opens. `DialogWidget` receives its buttons through its constructor, and the first one sits on the
right. Register the buttons through the dialog's `visitWidgets`, keep them inactive while the dialog
is hidden, and render the open dialog on a later layer. Escape should close an open dialog before the
screen; `ClientConfigScreen` shows the pattern.

### New interactive widget checklist
```

- [ ] **Step 9: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `git status --short` → `TrueFalseToggleButton.java` is deleted; `OnOffToggleButton.java`,
`reset.png` and the test are new; the dialog, texture, theme-editor, screen, guide and six language
files are modified.

---

### Task 7: New translation keys in six locales

**Files:**
- Modify: the six language files under `mods/src/main/resources/assets/server_waypoint/lang/`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigTranslationTest.java`

**Interfaces:**
- Consumes: the `server_waypoint.config.off` lines (Task 6) as insertion anchors.
- Produces: every key in the spec's text tables, in all six locales. `%%` is the literal percent
  sign in `server_waypoint.config.unit.percent` and the percent value format.

- [ ] **Step 1: Write the failing tests**

In `ClientConfigTranslationTest.java`, replace:
```java
    private static final List<String> SCREEN_KEYS = List.of(
            "server_waypoint.config.on",
            "server_waypoint.config.off"
    );
```
With:
```java
    private static final List<String> SCREEN_KEYS = List.of(
            "server_waypoint.config.screen.title",
            "server_waypoint.config.on",
            "server_waypoint.config.off",
            "server_waypoint.config.enable_waypoint_render",
            "server_waypoint.config.enable_waypoint_render.tooltip",
            "server_waypoint.config.waypoint_scale_factor",
            "server_waypoint.config.waypoint_scale_factor.tooltip",
            "server_waypoint.config.waypoint_vertical_offset",
            "server_waypoint.config.waypoint_vertical_offset.tooltip",
            "server_waypoint.config.waypoint_bg_opacity",
            "server_waypoint.config.waypoint_bg_opacity.tooltip",
            "server_waypoint.config.local_waypoint_view_distance",
            "server_waypoint.config.local_waypoint_view_distance.tooltip",
            "server_waypoint.config.theme",
            "server_waypoint.config.theme.open",
            "server_waypoint.config.theme.tooltip",
            "server_waypoint.config.confirm_sync",
            "server_waypoint.config.section.rendering",
            "server_waypoint.config.section.map_mods",
            "server_waypoint.config.section.appearance",
            "server_waypoint.map_mod.xaeros_minimap",
            "server_waypoint.map_mod.voxelmap",
            "server_waypoint.config.map_mod.auto_sync",
            "server_waypoint.config.map_mod.auto_sync.tooltip",
            "server_waypoint.config.map_mod.sync_now",
            "server_waypoint.config.map_mod.sync_button",
            "server_waypoint.config.map_mod.not_installed",
            "server_waypoint.config.map_mod.not_installed.tooltip",
            "server_waypoint.config.unit.percent",
            "server_waypoint.config.unit.chunks",
            "server_waypoint.config.value.percent",
            "server_waypoint.config.value.chunks",
            "server_waypoint.config.default",
            "server_waypoint.config.reset",
            "server_waypoint.config.reset_all",
            "server_waypoint.config.reset_all.title",
            "server_waypoint.config.reset_all.body",
            "server_waypoint.config.reset_all.confirm",
            "server_waypoint.config.reset_all.done",
            "server_waypoint.config.sync.title",
            "server_waypoint.config.sync.body",
            "server_waypoint.config.sync.stays",
            "server_waypoint.config.sync.stays.detail",
            "server_waypoint.config.sync.lost",
            "server_waypoint.config.sync.lost.detail",
            "server_waypoint.config.sync.done",
            "server_waypoint.config.sync.failed",
            "server_waypoint.config.sync.no_world",
            "server_waypoint.config.sync.map_mod_loading",
            "server_waypoint.manager.loading",
            "server_waypoint.no_serverside_support",
            "server_waypoint.incompatible_protocol_version",
            "server_waypoint.cancel.button",
            "server_waypoint.confirm.button"
    );
    private static final List<String> LABELS_WITHOUT_UNITS = List.of(
            "server_waypoint.config.waypoint_scale_factor",
            "server_waypoint.config.waypoint_vertical_offset",
            "server_waypoint.config.local_waypoint_view_distance"
    );
```

Then replace:
```java
    private JsonObject read(String locale) throws Exception {
```
With:
```java
    @Test
    void percentSignsAreEscapedInEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            assertEquals("%%", translated.get("server_waypoint.config.unit.percent").getAsString(), locale);
            assertTrue(translated.get("server_waypoint.config.value.percent").getAsString().contains("%s%%"), locale);
        }
    }

    @Test
    void labelsLeaveTheirUnitsToTheUnitColumn() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : LABELS_WITHOUT_UNITS) {
                String label = translated.get(key).getAsString();
                assertFalse(label.contains("(") || label.contains("（"), locale + ": " + key);
            }
        }
    }

    private JsonObject read(String locale) throws Exception {
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigTranslationTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `allSixLocalesDefineTheScreenKeysWithMatchingPlaceholders` fails on
`en_us: server_waypoint.config.enable_waypoint_render.tooltip`, and the two new tests fail.

- [ ] **Step 3: Update `en_us.json`**

Replace:
```json
  "server_waypoint.config.screen.title": "Server Waypoint %1$s Client Configurations",
  "server_waypoint.config.enable_waypoint_render": "Enable Waypoint Rendering",
  "server_waypoint.config.waypoint_scale_factor": "Waypoint Rendering Scaling Factor (Percentage)",
```
With:
```json
  "server_waypoint.config.screen.title": "Server Waypoint %1$s client settings",
  "server_waypoint.config.enable_waypoint_render": "Show in-world waypoints",
  "server_waypoint.config.waypoint_scale_factor": "Scale",
```

Replace:
```json
  "server_waypoint.config.waypoint_vertical_offset": "Waypoint Vertical Offset (Percentage)",
  "server_waypoint.config.local_waypoint_view_distance": "Local Waypoint View Distance (Chunks)",
  "server_waypoint.config.theme": "Color Theme",
```
With:
```json
  "server_waypoint.config.waypoint_vertical_offset": "Vertical offset",
  "server_waypoint.config.local_waypoint_view_distance": "Local waypoint range",
  "server_waypoint.config.theme": "Color theme",
```

Replace:
```json
  "server_waypoint.config.off": "Off",
```
With:
```json
  "server_waypoint.config.off": "Off",
  "server_waypoint.config.waypoint_bg_opacity": "Background opacity",
  "server_waypoint.config.section.rendering": "Waypoint rendering",
  "server_waypoint.config.section.map_mods": "Map mods",
  "server_waypoint.config.section.appearance": "Appearance",
  "server_waypoint.map_mod.xaeros_minimap": "Xaero's Minimap",
  "server_waypoint.map_mod.voxelmap": "VoxelMap",
  "server_waypoint.config.map_mod.auto_sync": "%s: auto sync",
  "server_waypoint.config.map_mod.sync_now": "%s: sync now",
  "server_waypoint.config.map_mod.sync_button": "Sync…",
  "server_waypoint.config.map_mod.not_installed": "Not installed",
  "server_waypoint.config.unit.percent": "%%",
  "server_waypoint.config.unit.chunks": "chunks",
  "server_waypoint.config.value.percent": "%s%%",
  "server_waypoint.config.value.chunks": "%s chunks",
  "server_waypoint.config.default": "Default: %s",
  "server_waypoint.config.reset": "Reset to default: %s",
  "server_waypoint.config.reset_all": "Reset to defaults…",
  "server_waypoint.config.reset_all.title": "Reset settings?",
  "server_waypoint.config.reset_all.body": "Waypoint rendering and map sync settings go back to their defaults. Your waypoints and color theme don't change.",
  "server_waypoint.config.reset_all.confirm": "Reset",
  "server_waypoint.config.reset_all.done": "Settings reset to defaults.",
  "server_waypoint.config.sync.title": "Sync to %s?",
  "server_waypoint.config.sync.body": "Replaces the waypoints Server Waypoint added to %s with this server's current waypoints.",
  "server_waypoint.config.sync.stays": "What stays:",
  "server_waypoint.config.sync.stays.detail": "Everything you created yourself, even with the same name as a server list.",
  "server_waypoint.config.sync.lost": "What is lost:",
  "server_waypoint.config.sync.lost.detail": "Changes you made to waypoints Server Waypoint added, and waypoints from lists removed on the server.",
  "server_waypoint.config.sync.done": "Synced waypoints to %s.",
  "server_waypoint.config.sync.failed": "Couldn't sync to %s. See the game log.",
  "server_waypoint.config.sync.no_world": "Join a world to sync.",
  "server_waypoint.config.sync.map_mod_loading": "%s is still loading.",
  "server_waypoint.config.enable_waypoint_render.tooltip": "Draws waypoint markers in the world. Turning this off only hides them.",
  "server_waypoint.config.waypoint_scale_factor.tooltip": "Size of waypoint markers in the world, as a percentage of their normal size.",
  "server_waypoint.config.waypoint_vertical_offset.tooltip": "Moves waypoint markers up or down by up to half a block.",
  "server_waypoint.config.waypoint_bg_opacity.tooltip": "Opacity of waypoint marker backgrounds and icons, from 0 (clear) to 255 (solid).",
  "server_waypoint.config.local_waypoint_view_distance.tooltip": "Waypoints with local visibility are drawn only within this many chunks. Global waypoints are always drawn.",
  "server_waypoint.config.map_mod.auto_sync.tooltip": "Keeps the waypoints Server Waypoint adds to %s up to date with the server. Your own waypoints are never changed.",
  "server_waypoint.config.map_mod.not_installed.tooltip": "Install %s to sync this server's waypoints to it.",
  "server_waypoint.config.theme.tooltip": "Colors of Server Waypoint's screens.",
```

- [ ] **Step 4: Update `zh_cn.json`**

Replace:
```json
  "server_waypoint.config.screen.title": "服务器路径点（%1$s）客户端配置",
  "server_waypoint.config.enable_waypoint_render": "启用路径点渲染",
  "server_waypoint.config.waypoint_scale_factor": "路径点缩放比例（百分比）",
  "server_waypoint.config.waypoint_vertical_offset": "路径点垂直偏移（百分比）",
```
With:
```json
  "server_waypoint.config.screen.title": "服务器路径点（%1$s）客户端设置",
  "server_waypoint.config.enable_waypoint_render": "在世界中显示路径点",
  "server_waypoint.config.waypoint_scale_factor": "缩放",
  "server_waypoint.config.waypoint_vertical_offset": "垂直偏移",
```

Replace:
```json
  "server_waypoint.config.local_waypoint_view_distance": "局部路径点渲染视距（区块）",
```
With:
```json
  "server_waypoint.config.local_waypoint_view_distance": "局部路径点范围",
```

Replace:
```json
  "server_waypoint.config.off": "关",
```
With:
```json
  "server_waypoint.config.off": "关",
  "server_waypoint.config.waypoint_bg_opacity": "背景不透明度",
  "server_waypoint.config.section.rendering": "路径点渲染",
  "server_waypoint.config.section.map_mods": "地图模组",
  "server_waypoint.config.section.appearance": "外观",
  "server_waypoint.map_mod.xaeros_minimap": "Xaero的小地图",
  "server_waypoint.map_mod.voxelmap": "VoxelMap",
  "server_waypoint.config.map_mod.auto_sync": "%s：自动同步",
  "server_waypoint.config.map_mod.sync_now": "%s：立即同步",
  "server_waypoint.config.map_mod.sync_button": "同步…",
  "server_waypoint.config.map_mod.not_installed": "未安装",
  "server_waypoint.config.unit.percent": "%%",
  "server_waypoint.config.unit.chunks": "区块",
  "server_waypoint.config.value.percent": "%s%%",
  "server_waypoint.config.value.chunks": "%s 区块",
  "server_waypoint.config.default": "默认值：%s",
  "server_waypoint.config.reset": "恢复默认值：%s",
  "server_waypoint.config.reset_all": "全部恢复默认…",
  "server_waypoint.config.reset_all.title": "要恢复默认设置吗？",
  "server_waypoint.config.reset_all.body": "路径点渲染和地图同步设置将恢复为默认值。你的路径点和颜色主题不会改变。",
  "server_waypoint.config.reset_all.confirm": "恢复",
  "server_waypoint.config.reset_all.done": "已恢复默认设置。",
  "server_waypoint.config.sync.title": "要同步到%s吗？",
  "server_waypoint.config.sync.body": "用此服务器当前的路径点替换本模组添加到%s的路径点。",
  "server_waypoint.config.sync.stays": "保留的内容：",
  "server_waypoint.config.sync.stays.detail": "你自己创建的所有内容，即使与服务器列表同名。",
  "server_waypoint.config.sync.lost": "丢失的内容：",
  "server_waypoint.config.sync.lost.detail": "你对本模组所添加路径点的修改，以及服务器上已删除列表中的路径点。",
  "server_waypoint.config.sync.done": "已将路径点同步到%s。",
  "server_waypoint.config.sync.failed": "无法同步到%s。请查看游戏日志。",
  "server_waypoint.config.sync.no_world": "进入世界后才能同步。",
  "server_waypoint.config.sync.map_mod_loading": "%s仍在加载。",
  "server_waypoint.config.enable_waypoint_render.tooltip": "在世界中绘制路径点标记。关闭后只会隐藏它们。",
  "server_waypoint.config.waypoint_scale_factor.tooltip": "路径点标记在世界中的大小，以正常大小的百分比表示。",
  "server_waypoint.config.waypoint_vertical_offset.tooltip": "将路径点标记上移或下移，最多半格。",
  "server_waypoint.config.waypoint_bg_opacity.tooltip": "路径点标记背景和图标的不透明度，从 0（透明）到 255（不透明）。",
  "server_waypoint.config.local_waypoint_view_distance.tooltip": "可见范围为局部的路径点只在此区块数范围内绘制。全局路径点始终绘制。",
  "server_waypoint.config.map_mod.auto_sync.tooltip": "让本模组添加到%s的路径点与服务器保持同步。你自己的路径点永远不会被修改。",
  "server_waypoint.config.map_mod.not_installed.tooltip": "安装%s后即可将此服务器的路径点同步到其中。",
  "server_waypoint.config.theme.tooltip": "本模组界面的颜色。",
```

- [ ] **Step 5: Update `es_es.json`**

Replace:
```json
  "server_waypoint.config.screen.title": "Configuración del cliente de Server Waypoint %1$s",
  "server_waypoint.config.enable_waypoint_render": "Habilitar el renderizado de waypoints",
  "server_waypoint.config.waypoint_scale_factor": "Factor de escala del renderizado de waypoints (porcentaje)",
```
With:
```json
  "server_waypoint.config.screen.title": "Ajustes del cliente de Server Waypoint %1$s",
  "server_waypoint.config.enable_waypoint_render": "Mostrar puntos de ruta en el mundo",
  "server_waypoint.config.waypoint_scale_factor": "Escala",
```

Replace:
```json
  "server_waypoint.config.waypoint_vertical_offset": "Desplazamiento vertical de los waypoints (porcentaje)",
  "server_waypoint.config.local_waypoint_view_distance": "Distancia de visualización de waypoints locales (chunks)",
```
With:
```json
  "server_waypoint.config.waypoint_vertical_offset": "Desplazamiento vertical",
  "server_waypoint.config.local_waypoint_view_distance": "Alcance de los puntos de ruta locales",
```

Replace:
```json
  "server_waypoint.config.off": "No",
```
With:
```json
  "server_waypoint.config.off": "No",
  "server_waypoint.config.theme": "Tema de colores",
  "server_waypoint.config.theme.open": "Configurar",
  "server_waypoint.config.waypoint_bg_opacity": "Opacidad del fondo",
  "server_waypoint.config.section.rendering": "Renderizado de puntos de ruta",
  "server_waypoint.config.section.map_mods": "Mods de mapas",
  "server_waypoint.config.section.appearance": "Apariencia",
  "server_waypoint.map_mod.xaeros_minimap": "Minimapa de Xaero",
  "server_waypoint.map_mod.voxelmap": "VoxelMap",
  "server_waypoint.config.map_mod.auto_sync": "%s: sincronización automática",
  "server_waypoint.config.map_mod.sync_now": "%s: sincronizar ahora",
  "server_waypoint.config.map_mod.sync_button": "Sincronizar…",
  "server_waypoint.config.map_mod.not_installed": "No instalado",
  "server_waypoint.config.unit.percent": "%%",
  "server_waypoint.config.unit.chunks": "chunks",
  "server_waypoint.config.value.percent": "%s%%",
  "server_waypoint.config.value.chunks": "%s chunks",
  "server_waypoint.config.default": "Predeterminado: %s",
  "server_waypoint.config.reset": "Restablecer al valor predeterminado: %s",
  "server_waypoint.config.reset_all": "Restablecer valores predeterminados…",
  "server_waypoint.config.reset_all.title": "¿Restablecer los ajustes?",
  "server_waypoint.config.reset_all.body": "Los ajustes de renderizado de puntos de ruta y de sincronización con mapas vuelven a sus valores predeterminados. Tus puntos de ruta y el tema de colores no cambian.",
  "server_waypoint.config.reset_all.confirm": "Restablecer",
  "server_waypoint.config.reset_all.done": "Ajustes restablecidos a sus valores predeterminados.",
  "server_waypoint.config.sync.title": "¿Sincronizar con %s?",
  "server_waypoint.config.sync.body": "Reemplaza los puntos de ruta que Server Waypoint añadió a %s por los puntos de ruta actuales de este servidor.",
  "server_waypoint.config.sync.stays": "Lo que se conserva:",
  "server_waypoint.config.sync.stays.detail": "Todo lo que hayas creado tú, aunque tenga el mismo nombre que una lista del servidor.",
  "server_waypoint.config.sync.lost": "Lo que se pierde:",
  "server_waypoint.config.sync.lost.detail": "Los cambios que hiciste en los puntos de ruta que añadió Server Waypoint y los puntos de ruta de listas eliminadas en el servidor.",
  "server_waypoint.config.sync.done": "Puntos de ruta sincronizados con %s.",
  "server_waypoint.config.sync.failed": "No se pudo sincronizar con %s. Consulta el registro del juego.",
  "server_waypoint.config.sync.no_world": "Entra en un mundo para sincronizar.",
  "server_waypoint.config.sync.map_mod_loading": "%s todavía se está cargando.",
  "server_waypoint.config.enable_waypoint_render.tooltip": "Dibuja los marcadores de puntos de ruta en el mundo. Desactivarlo solo los oculta.",
  "server_waypoint.config.waypoint_scale_factor.tooltip": "Tamaño de los marcadores de puntos de ruta en el mundo, como porcentaje de su tamaño normal.",
  "server_waypoint.config.waypoint_vertical_offset.tooltip": "Mueve los marcadores de puntos de ruta hacia arriba o hacia abajo hasta medio bloque.",
  "server_waypoint.config.waypoint_bg_opacity.tooltip": "Opacidad del fondo y los iconos de los marcadores de puntos de ruta, de 0 (transparente) a 255 (opaco).",
  "server_waypoint.config.local_waypoint_view_distance.tooltip": "Los puntos de ruta con visibilidad local solo se dibujan dentro de esta cantidad de chunks. Los globales se dibujan siempre.",
  "server_waypoint.config.map_mod.auto_sync.tooltip": "Mantiene actualizados con el servidor los puntos de ruta que Server Waypoint añade a %s. Tus propios puntos de ruta nunca se modifican.",
  "server_waypoint.config.map_mod.not_installed.tooltip": "Instala %s para sincronizar con él los puntos de ruta de este servidor.",
  "server_waypoint.config.theme.tooltip": "Colores de las pantallas de Server Waypoint.",
```

- [ ] **Step 6: Update `he_il.json`**

Replace:
```json
  "server_waypoint.config.enable_waypoint_render": "הפעלת הצגת נקודות ציון",
  "server_waypoint.config.waypoint_scale_factor": "מקדם קנה המידה של הצגת נקודות ציון (באחוזים)",
```
With:
```json
  "server_waypoint.config.enable_waypoint_render": "הצגת נקודות ציון בעולם",
  "server_waypoint.config.waypoint_scale_factor": "קנה מידה",
```

Replace:
```json
  "server_waypoint.config.waypoint_vertical_offset": "היסט אנכי של נקודות הציון (באחוזים)",
  "server_waypoint.config.local_waypoint_view_distance": "מרחק הצגה של נקודות ציון מקומיות (בצ'אנקים)",
```
With:
```json
  "server_waypoint.config.waypoint_vertical_offset": "היסט אנכי",
  "server_waypoint.config.local_waypoint_view_distance": "טווח נקודות ציון מקומיות",
```

Replace:
```json
  "server_waypoint.config.off": "כבוי",
```
With:
```json
  "server_waypoint.config.off": "כבוי",
  "server_waypoint.config.theme": "ערכת צבעים",
  "server_waypoint.config.theme.open": "הגדרה",
  "server_waypoint.config.waypoint_bg_opacity": "אטימות הרקע",
  "server_waypoint.config.section.rendering": "הצגת נקודות ציון",
  "server_waypoint.config.section.map_mods": "מודים של מפות",
  "server_waypoint.config.section.appearance": "מראה",
  "server_waypoint.map_mod.xaeros_minimap": "מפת המיני של Xaero",
  "server_waypoint.map_mod.voxelmap": "VoxelMap",
  "server_waypoint.config.map_mod.auto_sync": "%s: סנכרון אוטומטי",
  "server_waypoint.config.map_mod.sync_now": "%s: סנכרון עכשיו",
  "server_waypoint.config.map_mod.sync_button": "סנכרן…",
  "server_waypoint.config.map_mod.not_installed": "לא מותקן",
  "server_waypoint.config.unit.percent": "%%",
  "server_waypoint.config.unit.chunks": "צ'אנקים",
  "server_waypoint.config.value.percent": "%s%%",
  "server_waypoint.config.value.chunks": "%s צ'אנקים",
  "server_waypoint.config.default": "ברירת מחדל: %s",
  "server_waypoint.config.reset": "איפוס לברירת המחדל: %s",
  "server_waypoint.config.reset_all": "איפוס לברירות המחדל…",
  "server_waypoint.config.reset_all.title": "לאפס את ההגדרות?",
  "server_waypoint.config.reset_all.body": "הגדרות הצגת נקודות הציון והסנכרון עם מפות יחזרו לברירות המחדל. נקודות הציון וערכת הצבעים שלך לא ישתנו.",
  "server_waypoint.config.reset_all.confirm": "איפוס",
  "server_waypoint.config.reset_all.done": "ההגדרות אופסו לברירות המחדל.",
  "server_waypoint.config.sync.title": "לסנכרן עם %s?",
  "server_waypoint.config.sync.body": "מחליף את נקודות הציון ש-Server Waypoint הוסיף אל %s בנקודות הציון הנוכחיות של השרת הזה.",
  "server_waypoint.config.sync.stays": "מה נשאר:",
  "server_waypoint.config.sync.stays.detail": "כל מה שיצרת בעצמך, גם אם שמו זהה לשם של רשימה בשרת.",
  "server_waypoint.config.sync.lost": "מה נאבד:",
  "server_waypoint.config.sync.lost.detail": "שינויים שעשית בנקודות ציון ש-Server Waypoint הוסיף, ונקודות ציון מרשימות שנמחקו בשרת.",
  "server_waypoint.config.sync.done": "נקודות הציון סונכרנו עם %s.",
  "server_waypoint.config.sync.failed": "הסנכרון עם %s נכשל. ראו את יומן המשחק.",
  "server_waypoint.config.sync.no_world": "יש להיכנס לעולם כדי לסנכרן.",
  "server_waypoint.config.sync.map_mod_loading": "%s עדיין נטען.",
  "server_waypoint.config.enable_waypoint_render.tooltip": "מציג סמני נקודות ציון בעולם. כיבוי רק מסתיר אותם.",
  "server_waypoint.config.waypoint_scale_factor.tooltip": "גודל סמני נקודות הציון בעולם, באחוזים מהגודל הרגיל.",
  "server_waypoint.config.waypoint_vertical_offset.tooltip": "מזיז את סמני נקודות הציון למעלה או למטה, עד חצי בלוק.",
  "server_waypoint.config.waypoint_bg_opacity.tooltip": "אטימות הרקע והסמלים של סמני נקודות הציון, מ-0 (שקוף) עד 255 (אטום).",
  "server_waypoint.config.local_waypoint_view_distance.tooltip": "נקודות ציון עם תצוגה מקומית מוצגות רק בטווח של מספר הצ'אנקים הזה. נקודות ציון גלובליות מוצגות תמיד.",
  "server_waypoint.config.map_mod.auto_sync.tooltip": "שומר על נקודות הציון ש-Server Waypoint מוסיף אל %s מעודכנות מול השרת. נקודות הציון שלך לעולם לא משתנות.",
  "server_waypoint.config.map_mod.not_installed.tooltip": "התקינו את %s כדי לסנכרן אליו את נקודות הציון של השרת הזה.",
  "server_waypoint.config.theme.tooltip": "הצבעים של המסכים של Server Waypoint.",
```

- [ ] **Step 7: Update `zh_hk.json`**

Replace:
```json
  "server_waypoint.config.enable_waypoint_render": "啟用路徑點渲染",
  "server_waypoint.config.waypoint_scale_factor": "路徑點縮放比例 (百分比)",
  "server_waypoint.config.waypoint_vertical_offset": "路徑點垂直偏移 (百分比)",
```
With:
```json
  "server_waypoint.config.enable_waypoint_render": "喺世界中顯示路徑點",
  "server_waypoint.config.waypoint_scale_factor": "縮放",
  "server_waypoint.config.waypoint_vertical_offset": "垂直偏移",
```

Replace:
```json
  "server_waypoint.config.local_waypoint_view_distance": "區域路徑點渲染距離 (區塊)",
```
With:
```json
  "server_waypoint.config.local_waypoint_view_distance": "區域路徑點範圍",
```

Replace:
```json
  "server_waypoint.config.off": "關",
```
With:
```json
  "server_waypoint.config.off": "關",
  "server_waypoint.config.theme": "顏色主題",
  "server_waypoint.config.theme.open": "設定",
  "server_waypoint.config.waypoint_bg_opacity": "背景不透明度",
  "server_waypoint.config.section.rendering": "路徑點渲染",
  "server_waypoint.config.section.map_mods": "地圖模組",
  "server_waypoint.config.section.appearance": "外觀",
  "server_waypoint.map_mod.xaeros_minimap": "Xaero 小地圖",
  "server_waypoint.map_mod.voxelmap": "VoxelMap",
  "server_waypoint.config.map_mod.auto_sync": "%s：自動同步",
  "server_waypoint.config.map_mod.sync_now": "%s：即刻同步",
  "server_waypoint.config.map_mod.sync_button": "同步…",
  "server_waypoint.config.map_mod.not_installed": "未安裝",
  "server_waypoint.config.unit.percent": "%%",
  "server_waypoint.config.unit.chunks": "區塊",
  "server_waypoint.config.value.percent": "%s%%",
  "server_waypoint.config.value.chunks": "%s 區塊",
  "server_waypoint.config.default": "預設值：%s",
  "server_waypoint.config.reset": "重設為預設值：%s",
  "server_waypoint.config.reset_all": "全部重設為預設值…",
  "server_waypoint.config.reset_all.title": "要重設設定嗎？",
  "server_waypoint.config.reset_all.body": "路徑點渲染同地圖同步設定會還原為預設值。你嘅路徑點同顏色主題唔會改變。",
  "server_waypoint.config.reset_all.confirm": "重設",
  "server_waypoint.config.reset_all.done": "已重設為預設設定。",
  "server_waypoint.config.sync.title": "要同步到%s嗎？",
  "server_waypoint.config.sync.body": "用呢個伺服器而家嘅路徑點取代本模組加入%s嘅路徑點。",
  "server_waypoint.config.sync.stays": "保留項目：",
  "server_waypoint.config.sync.stays.detail": "你自己建立嘅所有內容，就算同伺服器清單同名都會保留。",
  "server_waypoint.config.sync.lost": "丟失項目：",
  "server_waypoint.config.sync.lost.detail": "你對本模組加入嘅路徑點所做嘅修改，同埋伺服器上已刪除清單入面嘅路徑點。",
  "server_waypoint.config.sync.done": "已將路徑點同步到%s。",
  "server_waypoint.config.sync.failed": "無法同步到%s。請查看遊戲日誌。",
  "server_waypoint.config.sync.no_world": "入咗世界先可以同步。",
  "server_waypoint.config.sync.map_mod_loading": "%s仲載入緊。",
  "server_waypoint.config.enable_waypoint_render.tooltip": "喺世界中繪製路徑點標記。關閉之後只會隱藏佢哋。",
  "server_waypoint.config.waypoint_scale_factor.tooltip": "路徑點標記喺世界中嘅大小，以正常大小嘅百分比表示。",
  "server_waypoint.config.waypoint_vertical_offset.tooltip": "將路徑點標記向上或者向下移，最多半格。",
  "server_waypoint.config.waypoint_bg_opacity.tooltip": "路徑點標記背景同圖示嘅不透明度，由 0（透明）到 255（不透明）。",
  "server_waypoint.config.local_waypoint_view_distance.tooltip": "可見範圍為區域嘅路徑點只會喺呢個區塊數範圍內繪製。全域路徑點會一直繪製。",
  "server_waypoint.config.map_mod.auto_sync.tooltip": "令本模組加入%s嘅路徑點同伺服器保持同步。你自己嘅路徑點永遠唔會被修改。",
  "server_waypoint.config.map_mod.not_installed.tooltip": "安裝%s之後就可以將呢個伺服器嘅路徑點同步過去。",
  "server_waypoint.config.theme.tooltip": "本模組介面嘅顏色。",
```

- [ ] **Step 8: Update `zh_tw.json`**

Replace:
```json
  "server_waypoint.config.enable_waypoint_render": "啓用路徑點渲染",
  "server_waypoint.config.waypoint_scale_factor": "路徑點縮放比例（百分比）",
  "server_waypoint.config.waypoint_vertical_offset": "路徑點垂直偏移（百分比）",
```
With:
```json
  "server_waypoint.config.enable_waypoint_render": "在世界中顯示路徑點",
  "server_waypoint.config.waypoint_scale_factor": "縮放",
  "server_waypoint.config.waypoint_vertical_offset": "垂直偏移",
```

Replace:
```json
  "server_waypoint.config.local_waypoint_view_distance": "局部路徑點渲染視距（區塊）",
```
With:
```json
  "server_waypoint.config.local_waypoint_view_distance": "局部路徑點範圍",
```

Replace:
```json
  "server_waypoint.config.off": "關閉",
```
With:
```json
  "server_waypoint.config.off": "關閉",
  "server_waypoint.config.theme": "色彩主題",
  "server_waypoint.config.theme.open": "設定",
  "server_waypoint.config.waypoint_bg_opacity": "背景不透明度",
  "server_waypoint.config.section.rendering": "路徑點渲染",
  "server_waypoint.config.section.map_mods": "地圖模組",
  "server_waypoint.config.section.appearance": "外觀",
  "server_waypoint.map_mod.xaeros_minimap": "Xaero的小地圖",
  "server_waypoint.map_mod.voxelmap": "VoxelMap",
  "server_waypoint.config.map_mod.auto_sync": "%s：自動同步",
  "server_waypoint.config.map_mod.sync_now": "%s：立即同步",
  "server_waypoint.config.map_mod.sync_button": "同步…",
  "server_waypoint.config.map_mod.not_installed": "未安裝",
  "server_waypoint.config.unit.percent": "%%",
  "server_waypoint.config.unit.chunks": "區塊",
  "server_waypoint.config.value.percent": "%s%%",
  "server_waypoint.config.value.chunks": "%s 區塊",
  "server_waypoint.config.default": "預設值：%s",
  "server_waypoint.config.reset": "重設為預設值：%s",
  "server_waypoint.config.reset_all": "全部重設為預設值…",
  "server_waypoint.config.reset_all.title": "要重設設定嗎？",
  "server_waypoint.config.reset_all.body": "路徑點渲染和地圖同步設定將還原為預設值。您的路徑點和色彩主題不會變更。",
  "server_waypoint.config.reset_all.confirm": "重設",
  "server_waypoint.config.reset_all.done": "已重設為預設設定。",
  "server_waypoint.config.sync.title": "要同步至%s嗎？",
  "server_waypoint.config.sync.body": "以此伺服器目前的路徑點取代本模組加入%s的路徑點。",
  "server_waypoint.config.sync.stays": "保留項目：",
  "server_waypoint.config.sync.stays.detail": "您自行建立的所有內容，即使與伺服器清單同名。",
  "server_waypoint.config.sync.lost": "遺失項目：",
  "server_waypoint.config.sync.lost.detail": "您對本模組所加入路徑點的修改，以及伺服器上已刪除清單中的路徑點。",
  "server_waypoint.config.sync.done": "已將路徑點同步至%s。",
  "server_waypoint.config.sync.failed": "無法同步至%s。請查看遊戲記錄檔。",
  "server_waypoint.config.sync.no_world": "進入世界後才能同步。",
  "server_waypoint.config.sync.map_mod_loading": "%s仍在載入中。",
  "server_waypoint.config.enable_waypoint_render.tooltip": "在世界中繪製路徑點標記。關閉後只會隱藏它們。",
  "server_waypoint.config.waypoint_scale_factor.tooltip": "路徑點標記在世界中的大小，以正常大小的百分比表示。",
  "server_waypoint.config.waypoint_vertical_offset.tooltip": "將路徑點標記上移或下移，最多半格。",
  "server_waypoint.config.waypoint_bg_opacity.tooltip": "路徑點標記背景與圖示的不透明度，從 0（透明）到 255（不透明）。",
  "server_waypoint.config.local_waypoint_view_distance.tooltip": "可見範圍為局部的路徑點只在此區塊數範圍內繪製。全域路徑點一律繪製。",
  "server_waypoint.config.map_mod.auto_sync.tooltip": "讓本模組加入%s的路徑點與伺服器保持同步。您自己的路徑點永遠不會被修改。",
  "server_waypoint.config.map_mod.not_installed.tooltip": "安裝%s後即可將此伺服器的路徑點同步至其中。",
  "server_waypoint.config.theme.tooltip": "本模組介面的色彩。",
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigTranslationTest" --tests "_959.server_waypoint.common.client.gui.screens.RemoteGuiTranslationTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`. Both tests parse all six files, so a JSON syntax error also fails here.

- [ ] **Step 10: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `git status --short` → the six language files and the test are modified.

---

### Task 8: Rewrite `ClientConfigScreen`

**Files:**
- Modify (replace): `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java`
- Modify: the six language files (remove retired keys)
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigTranslationTest.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: `SettingsListWidget` (Task 3); `ClientConfigSettings` (Task 4);
  `MapModIntegrations.find`, `MapModIntegration.isInstalled/isReady/syncAll` and `ClientConfigSync`
  (Task 5); `ConfirmationDialog(…, Component confirmLabel, …)`, `getCancelButton()`,
  `OnOffToggleButton`, `WidgetTextures.RESET_ICON` (Task 6); every translation key (Tasks 6–7);
  `WaypointManagerScreen.resolveViewState(boolean, ClientNetworkState)`.
- Produces: `public ClientConfigScreen(Screen parentScreen)`, unchanged. Tasks 10 and 11 use it.

- [ ] **Step 1: Retire the old keys in the test**

In `ClientConfigTranslationTest.java`, replace:
```java
    private static final List<String> RETIRED_KEYS = List.of(
            "server_waypoint.config.true",
            "server_waypoint.config.false"
    );
```
With:
```java
    private static final List<String> RETIRED_KEYS = List.of(
            "server_waypoint.config.true",
            "server_waypoint.config.false",
            "server_waypoint.config.waypoint_bg_alpha",
            "server_waypoint.config.auto_sync_to_xaeros",
            "server_waypoint.config.sync_to_xaeros",
            "server_waypoint.config.sync_to_xaeros.warn.1",
            "server_waypoint.config.sync_to_xaeros.warn.2",
            "server_waypoint.config.sync_to_xaeros.warn.3",
            "server_waypoint.config.sync_to_xaeros.warn.4",
            "server_waypoint.config.sync_to_xaeros.warn.5"
    );
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.ClientConfigTranslationTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `retiredKeysAreRemovedFromEveryLocale` fails on `en_us: server_waypoint.config.waypoint_bg_alpha`.

- [ ] **Step 3: Replace the screen**

Replace the whole content of `ClientConfigScreen.java` with:

```java
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.api.ButtonClickCallback;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.SettingsListLayout;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ConfirmationDialog;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.common.client.gui.widgets.OnOffToggleButton;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.integrations.MapModIntegration;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.upload.UploadTarget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * The client settings: waypoint rendering, map-mod sync and appearance in a
 * {@link SettingsListWidget}, with per-row and global reset and confirmation dialogs.
 */
public class ClientConfigScreen extends MovementAllowedScreen {
    private static final int SCREEN_MARGIN = 10;
    private static final int SECTION_SPACING = 6;
    private static final float TITLE_SCALE = 1.2F;
    private static final int FOOTER_BUTTON_GAP = 6;
    private static final int STATUS_GAP = 8;
    private static final int MIN_STATUS_WIDTH = 40;
    private static final int MIN_BUTTON_WIDTH = 50;
    private static final int BUTTON_TEXT_PADDING = 10;
    private static final int BUTTON_HEIGHT = 11;
    private static final int RESET_BUTTON_SIZE = 13;
    private static final int SLIDER_TRACK_WIDTH = 100;
    private static final int SLIDER_FIELD_WIDTH = 30;
    private static final int DIALOG_TEXT_WIDTH = 220;
    private static final int DIALOG_LINE_GAP = 5;
    private static final int NO_MOUSE = -10_000;

    private final Screen parentScreen;
    private final ClientConfig config = WaypointClientMod.getClientConfig();
    private final List<SettingControl> settingControls = new ArrayList<>();
    private final List<MapModControls> mapModControls = new ArrayList<>();
    private final WidgetPack footer = new WidgetPack(Orientation.HORIZONTAL);
    private final ScalableText titleText;
    private final SettingsListWidget settingsList;
    private final TranslucentButton themeButton;
    private final TranslucentButton resetAllButton;
    private final TranslucentButton doneButton;
    private final ScalableText statusText;
    private final ConfirmationDialog resetAllDialog;
    private final List<ClientConfigSettings.Setting> shownSettings;
    private @Nullable ConfirmationDialog openDialog;
    private @Nullable AbstractWidget dialogOpener;
    private @Nullable GuiEventListener pendingFocus;
    // True while the constructor builds the controls and while they're refreshed from the config,
    // so their change callbacks don't write the values straight back.
    private boolean updatingControls = true;
    private int contentWidth;
    private int contentHeight;

    public ClientConfigScreen(Screen parentScreen) {
        super(Component.translatable("server_waypoint.config.screen.title", ModInfo.MOD_VERSION));
        this.parentScreen = parentScreen;
        this.titleText = new ScalableText(0, 0, this.title, TITLE_SCALE, TEXT_PRIMARY, this.font);
        this.settingsList = new SettingsListWidget(this.font);
        this.themeButton = this.textButton(Component.translatable("server_waypoint.config.theme.open"),
                this::openThemeConfigScreen);
        this.resetAllButton = this.textButton(Component.translatable("server_waypoint.config.reset_all"),
                this::openResetAllDialog);
        this.doneButton = this.textButton(CommonComponents.GUI_DONE, this::onClose);
        this.statusText = new ScalableText(0, 0, Component.empty(), 1.0F, SUCCESS, MIN_STATUS_WIDTH, this.font);
        this.resetAllDialog = new ConfirmationDialog(
                0,
                0,
                Component.translatable("server_waypoint.config.reset_all.title"),
                this.dialogText(List.of(new DialogLine(
                        Component.translatable("server_waypoint.config.reset_all.body"), TEXT_PRIMARY))),
                Component.translatable("server_waypoint.config.reset_all.confirm"),
                this::confirmResetAll,
                this::closeDialog,
                this.font
        );

        Set<UploadTarget> installedMapMods = EnumSet.noneOf(UploadTarget.class);
        List<SettingsListWidget.Entry> entries = new ArrayList<>();
        entries.add(new SettingsListWidget.Header(Component.translatable("server_waypoint.config.section.rendering")));
        for (ClientConfigSettings.Setting setting : ClientConfigSettings.RENDERING) {
            entries.add(this.createSettingRow(setting));
        }
        entries.add(new SettingsListWidget.Header(Component.translatable("server_waypoint.config.section.map_mods")));
        for (UploadTarget target : ClientConfigSettings.MAP_MODS) {
            if (this.addMapModRows(entries, target)) {
                installedMapMods.add(target);
            }
        }
        entries.add(new SettingsListWidget.Header(Component.translatable("server_waypoint.config.section.appearance")));
        entries.add(new SettingsListWidget.Row(Component.translatable("server_waypoint.config.theme"), this.themeButton)
                .tooltip(() -> Component.translatable("server_waypoint.config.theme.tooltip")));
        this.settingsList.setEntries(entries);
        this.shownSettings = ClientConfigSettings.forScreen(installedMapMods);

        this.footer.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        this.footer.addChild(this.statusText, Direction.FORWARD);
        this.footer.addChild(this.doneButton, Direction.REVERSE);
        this.footer.addChild(SpacerElement.width(FOOTER_BUTTON_GAP), Direction.REVERSE);
        this.footer.addChild(this.resetAllButton, Direction.REVERSE);
        this.updatingControls = false;
    }

    @Override
    protected void init() {
        super.init();
        // Row widgets first and the list last, so the list can't take clicks meant for its rows.
        this.settingsList.visitWidgets(this::addRenderableWidget);
        this.addRenderableWidget(this.resetAllButton);
        this.addRenderableWidget(this.doneButton);
        for (ConfirmationDialog dialog : this.dialogs()) {
            dialog.visitWidgets(this::addRenderableWidget);
        }
        this.layoutContent();
        this.refreshControlStates();
        if (this.openDialog != null) {
            this.setFocused(this.openDialog.getCancelButton());
        }
    }

    @Override
    int getContentWidth() {
        return this.contentWidth;
    }

    @Override
    int getContentHeight() {
        return this.contentHeight;
    }

    /** Sizes and positions the title, the panel, the footer and the dialogs for the window. */
    private void layoutContent() {
        int panelWidth = Math.min(
                this.settingsList.getPreferredWidth() + SettingsListWidget.PANEL_PADDING * 2,
                Math.max(0, this.width - SCREEN_MARGIN * 2)
        );
        this.settingsList.setVisualWidth(panelWidth);
        this.titleText.setWidth(panelWidth);
        int buttonsWidth = this.resetAllButton.getVisualWidth() + FOOTER_BUTTON_GAP + this.doneButton.getVisualWidth();
        this.statusText.setMaxWidth(Math.max(MIN_STATUS_WIDTH, panelWidth - buttonsWidth - STATUS_GAP));
        int buttonHeight = Math.max(this.resetAllButton.getVisualHeight(), this.doneButton.getVisualHeight());
        int footerHeight = Math.max(buttonHeight, this.statusText.getHeight());
        int titleHeight = this.titleText.getHeight();
        int minimumPanelHeight = SettingsListWidget.PANEL_PADDING * 2 + SettingsListLayout.MIN_ROW_HEIGHT;
        int availablePanelHeight = this.height - SCREEN_MARGIN * 2 - titleHeight - footerHeight - SECTION_SPACING * 2;
        int panelHeight = Math.min(
                this.settingsList.getContentHeight() + SettingsListWidget.PANEL_PADDING * 2,
                Math.max(minimumPanelHeight, availablePanelHeight)
        );
        this.settingsList.setVisualHeight(panelHeight);
        this.contentWidth = panelWidth;
        this.contentHeight = titleHeight + SECTION_SPACING + panelHeight + SECTION_SPACING + footerHeight;

        int x = this.getCenteredX();
        int y = this.getCenteredY();
        this.titleText.setPosition(x, y);
        int panelY = y + titleHeight + SECTION_SPACING;
        this.settingsList.setPosition(x + SettingsListWidget.PANEL_PADDING, panelY + SettingsListWidget.PANEL_PADDING);
        this.footer.setDimensions(panelWidth, footerHeight);
        this.footer.setPosition(x, panelY + panelHeight + SECTION_SPACING);
        for (ConfirmationDialog dialog : this.dialogs()) {
            dialog.setPosition(centered(this.width, dialog.getWidth()), centered(this.height, dialog.getHeight()));
        }
    }

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Under an open dialog, nothing reacts to the mouse or shows a tooltip.
        int contentMouseX = this.openDialog != null ? NO_MOUSE : mouseX;
        int contentMouseY = this.openDialog != null ? NO_MOUSE : mouseY;
        this.titleText.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.settingsList.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.statusText.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.resetAllButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.doneButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        if (this.openDialog != null) {
            nextLayer(context);
            this.openDialog.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
            previousLayer(context);
        }
    }

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        this.applyPendingFocus();
        return handled;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        this.applyPendingFocus();
        return handled;
    }
    *///?}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Only the dialog's buttons can take focus while it's open, so there's no popup or text
        // entry for dismissFocusedInput() to handle first.
        if (keyCode == InputConstants.KEY_ESCAPE && this.openDialog != null) {
            this.closeDialog();
            this.pendingFocus = null;
            return true;
        }
        GuiEventListener focusedBefore = this.getFocused();
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        GuiEventListener focused = this.getFocused();
        if (focused != null && focused != focusedBefore) {
            this.settingsList.reveal(focused);
        }
        this.pendingFocus = null;
        return handled;
    }

    //? if <= 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (this.scrollSettingsList(mouseX, mouseY, 0, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollSettingsList(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    /** Gives the wheel to the list first, so it scrolls instead of changing a slider under the cursor. */
    private boolean scrollSettingsList(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.settingsList.isMouseOver(mouseX, mouseY)
                || !this.settingsList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return false;
        }
        if (this.getFocused() instanceof AbstractWidget widget && !widget.visible) {
            this.setFocused(null);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        this.refreshControlStates();
    }

    /** Every exit reaches this, including a disconnect, so the config is always saved. */
    @Override
    public void removed() {
        WaypointClientMod.getInstance().saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, this.parentScreen);
    }

    private SettingsListWidget.Row createSettingRow(ClientConfigSettings.Setting setting) {
        AbstractWidget widget;
        Component unit = null;
        if (setting instanceof ClientConfigSettings.IntSetting intSetting) {
            widget = new IntegerSlider(0, 0, SLIDER_TRACK_WIDTH, SLIDER_FIELD_WIDTH, intSetting.min(), intSetting.max(),
                    intSetting.get(this.config), value -> this.onIntChanged(intSetting, value), this.font);
            unit = intSetting.format().unit();
        } else {
            ClientConfigSettings.BooleanSetting booleanSetting = (ClientConfigSettings.BooleanSetting) setting;
            OnOffToggleButton toggle = new OnOffToggleButton(0, 0, value -> this.onBooleanChanged(booleanSetting, value));
            toggle.setState(booleanSetting.get(this.config));
            widget = toggle;
        }
        Component resetLabel = Component.translatable("server_waypoint.config.reset", setting.defaultText());
        IconButton resetButton = new IconButton(0, 0, RESET_BUTTON_SIZE, RESET_BUTTON_SIZE, resetLabel,
                WidgetTextures.RESET_ICON, () -> this.resetSetting(setting, widget));
        resetButton.setTooltip(Tooltip.create(resetLabel));
        this.settingControls.add(new SettingControl(setting, widget, resetButton));
        SettingsListWidget.Row row = new SettingsListWidget.Row(setting.text().label(), widget)
                .action(resetButton)
                .tooltip(() -> Component.empty()
                        .append(setting.text().description())
                        .append("\n")
                        .append(Component.translatable("server_waypoint.config.default", setting.defaultText())));
        if (unit != null) {
            row.suffix(unit);
        }
        return row;
    }

    /** Adds the rows of one map mod and reports whether it's installed. */
    private boolean addMapModRows(List<SettingsListWidget.Entry> entries, UploadTarget target) {
        Optional<MapModIntegration> integration = MapModIntegrations.find(target);
        boolean installed = integration.map(MapModIntegration::isInstalled).orElse(false);
        Component name = Component.translatable(ClientConfigSettings.mapModNameKey(target));
        ClientConfigSync.MapModRowState state = ClientConfigSync.resolveMapModRowState(integration.isPresent(), installed);
        if (state == ClientConfigSync.MapModRowState.HIDDEN) {
            return false;
        }
        if (state == ClientConfigSync.MapModRowState.NOT_INSTALLED) {
            ScalableText notInstalled = new ScalableText(0, 0,
                    Component.translatable("server_waypoint.config.map_mod.not_installed"), TEXT_MUTED, this.font);
            entries.add(new SettingsListWidget.Row(name, notInstalled)
                    .labelColor(TEXT_MUTED)
                    .tooltip(() -> Component.translatable("server_waypoint.config.map_mod.not_installed.tooltip", name)));
            return false;
        }
        entries.add(this.createSettingRow(ClientConfigSettings.autoSync(target)));
        MapModControls controls = this.createMapModControls(target, name, integration.orElseThrow());
        entries.add(new SettingsListWidget.Row(
                Component.translatable("server_waypoint.config.map_mod.sync_now", name), controls.syncButton())
                .tooltip(() -> this.syncTooltip(controls)));
        return true;
    }

    private MapModControls createMapModControls(UploadTarget target, Component name, MapModIntegration integration) {
        ConfirmationDialog dialog = new ConfirmationDialog(
                0,
                0,
                Component.translatable("server_waypoint.config.sync.title", name),
                this.dialogText(List.of(
                        new DialogLine(Component.translatable("server_waypoint.config.sync.body", name), TEXT_PRIMARY),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.stays"), SUCCESS),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.stays.detail"), TEXT_PRIMARY),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.lost"), DANGER),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.lost.detail"), TEXT_PRIMARY)
                )),
                Component.translatable("server_waypoint.config.confirm_sync"),
                () -> this.confirmSync(target),
                this::closeDialog,
                this.font
        );
        TranslucentButton syncButton = this.textButton(
                Component.translatable("server_waypoint.config.map_mod.sync_button"), () -> this.openSyncDialog(target));
        MapModControls controls = new MapModControls(target, name, integration, syncButton, dialog);
        this.mapModControls.add(controls);
        return controls;
    }

    private WidgetStack dialogText(List<DialogLine> lines) {
        WidgetStack stack = new WidgetStack(0, 0, DIALOG_LINE_GAP, true, false);
        for (int i = 0; i < lines.size(); i++) {
            DialogLine line = lines.get(i);
            ScalableText text = new ScalableText(0, 0, line.text(), 1.0F, line.color(), DIALOG_TEXT_WIDTH, this.font);
            if (i == 0) {
                stack.addChild(text, 0);
            } else {
                stack.addChild(text);
            }
        }
        return stack;
    }

    private TranslucentButton textButton(Component label, ButtonClickCallback callback) {
        int width = Math.max(MIN_BUTTON_WIDTH, this.font.width(label) + BUTTON_TEXT_PADDING);
        return new TranslucentButton(0, 0, width, BUTTON_HEIGHT, label, callback);
    }

    private void onIntChanged(ClientConfigSettings.IntSetting setting, int value) {
        if (this.updatingControls) {
            return;
        }
        setting.set(this.config, value);
        this.refreshControlStates();
    }

    private void onBooleanChanged(ClientConfigSettings.BooleanSetting setting, boolean value) {
        if (this.updatingControls) {
            return;
        }
        setting.set(this.config, value);
        this.refreshControlStates();
    }

    private void resetSetting(ClientConfigSettings.Setting setting, AbstractWidget widget) {
        setting.reset(this.config);
        this.syncControlsFromConfig();
        this.refreshControlStates();
        this.requestFocus(widget);
    }

    // A method reference rather than a lambda: javac rejects a lambda in the constructor that reads
    // final fields the constructor hasn't assigned yet.
    private void openResetAllDialog() {
        this.openDialog(this.resetAllDialog, this.resetAllButton);
    }

    private void confirmResetAll() {
        ClientConfigSettings.resetAll(this.config, this.shownSettings);
        this.syncControlsFromConfig();
        this.showStatus(Component.translatable("server_waypoint.config.reset_all.done"), SUCCESS);
        this.closeDialog();
    }

    private void openSyncDialog(UploadTarget target) {
        MapModControls controls = this.mapModControls(target);
        this.openDialog(controls.dialog(), controls.syncButton());
    }

    /** Checks the blocker again, because the connection may have changed while the dialog was open. */
    private void confirmSync(UploadTarget target) {
        MapModControls controls = this.mapModControls(target);
        ClientConfigSync.SyncBlocker blocker = this.syncBlocker(controls);
        if (blocker != null) {
            this.showStatus(this.blockerMessage(blocker, controls.name()), DANGER);
        } else {
            try {
                controls.integration().syncAll(WaypointClientMod.getInstance());
                this.showStatus(Component.translatable("server_waypoint.config.sync.done", controls.name()), SUCCESS);
            } catch (RuntimeException exception) {
                WaypointClientMod.LOGGER.error("Failed to sync waypoints to {}", controls.target(), exception);
                this.showStatus(Component.translatable("server_waypoint.config.sync.failed", controls.name()), DANGER);
            }
        }
        this.closeDialog();
    }

    private void openThemeConfigScreen() {
        MinecraftClientHelper.setScreen(this.minecraft, new WidgetThemeConfigScreen(this));
    }

    /** Sets every control's {@code active} flag from the dialog, the values and sync availability. */
    private void refreshControlStates() {
        boolean modal = this.openDialog != null;
        this.settingsList.active = !modal;
        for (SettingControl control : this.settingControls) {
            control.widget().active = !modal;
            control.resetButton().active = !modal && !control.setting().isDefault(this.config);
        }
        for (MapModControls controls : this.mapModControls) {
            controls.syncButton().active = !modal && this.syncBlocker(controls) == null;
        }
        this.themeButton.active = !modal;
        this.resetAllButton.active = !modal && !ClientConfigSettings.allDefault(this.config, this.shownSettings);
        this.doneButton.active = !modal;
        for (ConfirmationDialog dialog : this.dialogs()) {
            boolean open = dialog == this.openDialog;
            dialog.visible = open;
            dialog.visitWidgets(button -> button.active = open);
        }
    }

    private void syncControlsFromConfig() {
        this.updatingControls = true;
        try {
            for (SettingControl control : this.settingControls) {
                control.readFrom(this.config);
            }
        } finally {
            this.updatingControls = false;
        }
    }

    private @Nullable ClientConfigSync.SyncBlocker syncBlocker(MapModControls controls) {
        boolean inWorld = this.minecraft != null && this.minecraft.level != null;
        return ClientConfigSync.resolveSyncBlocker(
                inWorld,
                WaypointManagerScreen.resolveViewState(WaypointServerMod.runsWithClient(), WaypointClientMod.getNetworkState()),
                controls.integration().isReady()
        );
    }

    private Component blockerMessage(ClientConfigSync.SyncBlocker blocker, Component name) {
        return blocker.namesMapMod()
                ? Component.translatable(blocker.messageKey(), name)
                : Component.translatable(blocker.messageKey());
    }

    private Component syncTooltip(MapModControls controls) {
        MutableComponent tooltip = Component.translatable("server_waypoint.config.sync.body", controls.name());
        ClientConfigSync.SyncBlocker blocker = this.syncBlocker(controls);
        if (blocker != null) {
            tooltip.append("\n").append(this.blockerMessage(blocker, controls.name()));
        }
        return tooltip;
    }

    private void showStatus(Component message, WidgetThemeVariable color) {
        this.statusText.setColor(color);
        this.statusText.setText(message);
        // A wrapped status can change the footer's height, so the layout runs again.
        this.layoutContent();
    }

    private void openDialog(ConfirmationDialog dialog, AbstractWidget opener) {
        this.openDialog = dialog;
        this.dialogOpener = opener;
        this.refreshControlStates();
        this.requestFocus(dialog.getCancelButton());
    }

    private void closeDialog() {
        this.openDialog = null;
        this.refreshControlStates();
        AbstractWidget opener = this.dialogOpener;
        this.dialogOpener = null;
        this.requestFocus(opener != null && opener.active ? opener : this.doneButton);
    }

    private List<ConfirmationDialog> dialogs() {
        List<ConfirmationDialog> dialogs = new ArrayList<>();
        dialogs.add(this.resetAllDialog);
        for (MapModControls controls : this.mapModControls) {
            dialogs.add(controls.dialog());
        }
        return dialogs;
    }

    private MapModControls mapModControls(UploadTarget target) {
        for (MapModControls controls : this.mapModControls) {
            if (controls.target() == target) {
                return controls;
            }
        }
        throw new IllegalStateException("No controls for map mod " + target);
    }

    /**
     * Focuses {@code target} now, and again after the current click: vanilla focuses the clicked
     * widget after its callback runs, which would undo the change. Enter and Space don't move focus
     * after a button's callback, so {@link #keyPressed} only drops the pending request.
     */
    private void requestFocus(GuiEventListener target) {
        this.setFocused(target);
        this.pendingFocus = target;
    }

    private void applyPendingFocus() {
        if (this.pendingFocus != null) {
            this.setFocused(this.pendingFocus);
            this.pendingFocus = null;
        }
    }

    private record SettingControl(ClientConfigSettings.Setting setting, AbstractWidget widget, IconButton resetButton) {
        void readFrom(ClientConfig config) {
            if (this.setting instanceof ClientConfigSettings.IntSetting intSetting
                    && this.widget instanceof IntegerSlider slider) {
                slider.setValue(intSetting.get(config));
            } else if (this.setting instanceof ClientConfigSettings.BooleanSetting booleanSetting
                    && this.widget instanceof OnOffToggleButton toggle) {
                toggle.setState(booleanSetting.get(config));
            }
        }
    }

    private record MapModControls(UploadTarget target, Component name, MapModIntegration integration,
                                  TranslucentButton syncButton, ConfirmationDialog dialog) {
    }

    private record DialogLine(Component text, WidgetThemeVariable color) {
    }
}
```

- [ ] **Step 4: Remove the retired keys from all six locales**

The removed lines all end with a comma and sit between other entries, so deleting them keeps the
JSON valid. Run:

```bash
sed -i '' -E '/"server_waypoint\.config\.(waypoint_bg_alpha|auto_sync_to_xaeros|sync_to_xaeros(\.warn\.[1-5])?)":/d' mods/src/main/resources/assets/server_waypoint/lang/en_us.json mods/src/main/resources/assets/server_waypoint/lang/es_es.json mods/src/main/resources/assets/server_waypoint/lang/he_il.json mods/src/main/resources/assets/server_waypoint/lang/zh_cn.json mods/src/main/resources/assets/server_waypoint/lang/zh_hk.json mods/src/main/resources/assets/server_waypoint/lang/zh_tw.json
```

Run: `grep -c "server_waypoint.config.sync_to_xaeros\|waypoint_bg_alpha\|auto_sync_to_xaeros" mods/src/main/resources/assets/server_waypoint/lang/*.json`
Expected: every file reports `0`. Each file lost exactly eight lines; check with `git diff --stat`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`, including every test from Tasks 1–7.

- [ ] **Step 6: Compile the version branches**

The screen has `mouseClicked` and `mouseScrolled` branches. Run:
`./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.21.6-fabric:compileJava :mods:1.21.9-fabric:compileJava --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
- `ClientConfigScreen` demonstrates a scrollable `TreeViewWidget` of configuration rows and a modal `ConfirmationDialog` that disables the underlying controls.
```
With:
```markdown
- `ClientConfigScreen` demonstrates `SettingsListWidget` with per-row reset buttons, a footer built with a `WidgetPack`, and confirmation dialogs that disable the underlying controls, close on Escape and return focus to the button that opened them. Its Map mods rows depend on which map mods the loader supports (`MapModIntegrations.find`) and the player installed; the pure rules live in `ClientConfigSync`, and the settings themselves in `ClientConfigSettings`. It saves the config in `removed()`, which every exit reaches.
```

Then, under `### In-game theme editor`, the first paragraph starts with the sentence
``The Theme button on `ClientConfigScreen` opens `WidgetThemeConfigScreen`.`` Replace just that
sentence, in place, with
``The Configure button in the Appearance section of `ClientConfigScreen` opens `WidgetThemeConfigScreen`.``
The rest of that long line stays unchanged.

- [ ] **Step 8: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `grep -n "//?\|/\*?\|\*///?}" mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigScreen.java`
→ three balanced groups: the `MouseButtonEvent` import, `mouseClicked` and `mouseScrolled`.

---

### Task 9: Background without a world

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `MovementAllowedScreen` draws vanilla's background for screens outside a world under its
  themed overlay whenever `minecraft.level` is null. No API change for subclasses.

This task has no unit test: the background is drawn by vanilla code that needs a running client.
Compiling every version branch checks the calls; Task 12 checks the result in game.

- [ ] **Step 1: Route every background call through one method**

In `MovementAllowedScreen.java`, replace all five occurrences of:
```java
this.renderThemedBackground(context);
```
With:
```java
this.renderScreenBackground(context, deltaTicks);
```
One occurrence is inside the `< 1.21.6` branch of `extractRenderState` (it starts with `/*`); the
other four are in the background overrides. Each of those methods already has a `deltaTicks`
parameter.

- [ ] **Step 2: Draw vanilla's background when no world is loaded**

Replace:
```java
    private void renderThemedBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, this.width, this.height,
                WidgetThemeManager.getColor(WidgetThemeVariable.SCREEN_BACKGROUND));
    }
```
With:
```java
    /**
     * The themed overlay. Without a world, as when the screen opens from a mod list, vanilla's
     * background for screens outside a world goes underneath it.
     */
    private void renderScreenBackground(GuiGraphicsExtractor context, float deltaTicks) {
        if (this.minecraft.level == null) {
            this.renderBackgroundWithoutWorld(context, deltaTicks);
        }
        context.fill(0, 0, this.width, this.height,
                WidgetThemeManager.getColor(WidgetThemeVariable.SCREEN_BACKGROUND));
    }

    private void renderBackgroundWithoutWorld(GuiGraphicsExtractor context, float deltaTicks) {
        //? if < 1.20.5 {
        /*this.renderDirtBackground(context);
        *///?} elif < 1.21.2 {
        /*this.renderPanorama(context, deltaTicks);
        this.renderBlurredBackground(deltaTicks);
        this.renderMenuBackground(context);
        *///?} elif < 1.21.6 {
        /*this.renderPanorama(context, deltaTicks);
        this.renderBlurredBackground();
        this.renderMenuBackground(context);
        *///?} elif < 26 {
        /*this.renderPanorama(context, deltaTicks);
        this.renderBlurredBackground(context);
        this.renderMenuBackground(context);
        *///?} else {
        this.extractPanorama(context, deltaTicks);
        this.extractBlurredBackground(context);
        this.extractMenuBackground(context);
        //?}
    }
```

These are the vanilla methods each range has (checked in `minecraft_source_code/<version>/.../Screen.java`):
1.20.1–1.20.4 draw the dirt texture; 1.20.5–1.21 blur with a partial-tick argument; 1.21.2–1.21.5
blur without arguments; 1.21.6–1.21.11 blur with the graphics context; 26.x uses the `extract…`
names. The screen calls them itself, so vanilla's in-world transparent background and its subtitle
rendering aren't duplicated.

- [ ] **Step 3: Check the replacement**

Run: `grep -c "renderScreenBackground(context, deltaTicks)" mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java`
Expected: `5`.
Run: `grep -n "renderThemedBackground" mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java`
Expected: no output.

- [ ] **Step 4: Compile every branch**

Run: `./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.20.4-fabric:compileJava :mods:1.20.6-fabric:compileJava :mods:1.21-fabric:compileJava :mods:1.21.2-fabric:compileJava :mods:1.21.5-fabric:compileJava :mods:1.21.6-fabric:compileJava :mods:1.21.9-fabric:compileJava :mods:1.21.11-fabric:compileJava :mods:26.1.2-fabric:compileJava :mods:26.2-fabric:compileJava --max-workers=2 --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`. If a target reports a missing method, open that version's `Screen.java`
under `minecraft_source_code/` (the `generate-minecraft-sources` skill creates it if it's missing)
and move the branch boundary to the first version with the other signature. Don't add a new
branch for a single version.

- [ ] **Step 5: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
- Extend `MovementAllowedScreen` for the normal in-world GUI behavior used by this project. It provides the themed `SCREEN_BACKGROUND`, centering helpers, and optional movement-key forwarding.
```
With:
```markdown
- Extend `MovementAllowedScreen` for the normal in-world GUI behavior used by this project. It provides the themed `SCREEN_BACKGROUND`, centering helpers, and optional movement-key forwarding. When no world is loaded, as when a screen opens from a mod list, it draws vanilla's background for screens outside a world (the panorama on 1.20.5 and later) under the themed overlay.
```

- [ ] **Step 6: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `grep -n "//?\|/\*?\|\*///?}" mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java`
→ the new `renderBackgroundWithoutWorld` chain has one `//? if < 1.20.5 {`, three `*///?} elif …`
lines, one `*///?} else {` and one `//?}`, and the older chains are unchanged.

---

### Task 10: Escape leaves number fields

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/IntegerSlider.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreen.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/MovementAllowedScreenPopupEscapeTest.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: `MovementAllowedScreen.dismissFocusedInput()` from the base branch.
- Produces: `public boolean IntegerSlider.isEditingNumber()`. `dismissFocusedInput()` also yields
  focus when the focused widget is an `IntegerSlider` whose number field, not its track, has focus.
  The first Escape then leaves the field, which commits the typed number (`IntegerField` commits
  when it loses focus), and the next Escape closes the screen. `ClientConfigScreen` (Task 8) gets
  this through `super.keyPressed`. The theme editor's opacity and gallery sliders get it too,
  because `WidgetThemeConfigScreen` calls `dismissFocusedInput()` before its own Escape handling.

An `IntegerSlider` is one focusable widget. It holds a slider track and an `IntegerField` and
remembers which of the two was selected last: the field at first, the track after a click on it,
the field again after a click on the field. Focusing the slider, by Tab or a click, focuses that
part. The slider sends every key to the field, which consumes keys only while it has focus, so only
a focused field counts as text entry. With the track focused, Escape still closes the screen.

- [ ] **Step 1: Write the failing tests**

In `MovementAllowedScreenPopupEscapeTest.java`, replace:
```java
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import com.mojang.blaze3d.platform.InputConstants;
```
With:
```java
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IntegerField;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import com.mojang.blaze3d.platform.InputConstants;
```

Replace:
```java
        inputField.set(comboBox, input);
        assertEscapeLeavesTextEntry(comboBox);
        assertFalse(input.isFocused());
    }
```
With:
```java
        inputField.set(comboBox, input);
        assertEscapeLeavesTextEntry(comboBox);
        assertFalse(input.isFocused());
    }

    @Test
    void escapeLeavesAnIntegerSliderNumberFieldBeforeClosingTheScreen() throws ReflectiveOperationException {
        FocusOnlyField field = allocate(FocusOnlyField.class);
        IntegerSlider slider = integerSlider(field, field);
        assertEscapeLeavesTextEntry(slider);
        assertFalse(field.isFocused());
    }

    @Test
    void escapeClosesTheScreenWhenAnIntegerSliderTrackHasFocus() throws ReflectiveOperationException {
        FocusOnlyListener track = new FocusOnlyListener();
        IntegerSlider slider = integerSlider(allocate(FocusOnlyField.class), track);
        TestScreen screen = TestScreen.create();
        screen.setFocused(slider);
        assertTrue(track.isFocused());

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(screen.closed);
    }

    /** An {@code IntegerSlider} whose number field and selected part are test doubles. */
    private static IntegerSlider integerSlider(IntegerField field, GuiEventListener selectedPart)
            throws ReflectiveOperationException {
        IntegerSlider slider = allocate(IntegerSlider.class);
        setField(slider, "integerField", field);
        setField(slider, "focused", selectedPart);
        return slider;
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
```

Then replace:
```java
        @Override
        protected void renderMenuItem(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }
    }
}
```
With:
```java
        @Override
        protected void renderMenuItem(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }
    }

    /** A number field that only records focus; {@code allocate} skips the text state a real one needs. */
    private static final class FocusOnlyField extends IntegerField {
        private boolean focusedForTest;

        // Never runs: the tests create this double with allocate(), which skips constructors.
        private FocusOnlyField() {
            super(0, 0, 0, Component.empty(), null);
        }

        @Override
        public void setFocused(boolean focused) {
            this.focusedForTest = focused;
        }

        @Override
        public boolean isFocused() {
            return this.focusedForTest;
        }
    }

    /** Stands in for the slider track, which only needs to take and lose focus here. */
    private static final class FocusOnlyListener implements GuiEventListener {
        private boolean focused;

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return this.focused;
        }
    }
}
```

The doubles replace the parts `IntegerSlider.setFocused` touches: the private `integerField` and
`focused` fields, which the existing ComboBox test reaches the same way. The real `IntegerField`
commit on focus loss needs a running client, so the tests check only where focus goes.

- [ ] **Step 2: Run the tests to verify the new one fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.MovementAllowedScreenPopupEscapeTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `escapeLeavesAnIntegerSliderNumberFieldBeforeClosingTheScreen` fails at
`assertNull(screen.getFocused())`, because the first Escape closes the screen. The other five tests
pass, including `escapeClosesTheScreenWhenAnIntegerSliderTrackHasFocus`.

- [ ] **Step 3: Report whether the number field has focus**

In `IntegerSlider.java`, replace:
```java
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) updateFocused(this.focused);
        else this.focused.setFocused(false);
    }
```
With:
```java
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) updateFocused(this.focused);
        else this.focused.setFocused(false);
    }

    /**
     * Whether typing goes to the number field: the slider has focus, and its field rather than its
     * track was selected last.
     */
    public boolean isEditingNumber() {
        return this.integerField.isFocused();
    }
```

- [ ] **Step 4: Leave the number field on Escape**

In `MovementAllowedScreen.java`, replace:
```java
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
```
With:
```java
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
```

Replace:
```java
    /**
     * Vanilla closes the screen on Escape before the focused widget receives the key. Dismiss
     * an open popup or leave text entry first, even when no suggestions or choices are showing.
     */
    protected boolean dismissFocusedInput() {
        GuiEventListener focused = this.getFocused();
        boolean closedPopup = focused instanceof PopupOwner owner && owner.closePopupIfOpen();
        if (closedPopup || focused instanceof EditBox || focused instanceof ComboBoxWidget) {
```
With:
```java
    /**
     * Vanilla closes the screen on Escape before the focused widget receives the key. Dismiss
     * an open popup or leave text entry first, even when no suggestions or choices are showing.
     * An {@link IntegerSlider} is text entry while its number field has focus; leaving the field
     * commits the typed number.
     */
    protected boolean dismissFocusedInput() {
        GuiEventListener focused = this.getFocused();
        boolean closedPopup = focused instanceof PopupOwner owner && owner.closePopupIfOpen();
        if (closedPopup || focused instanceof EditBox || focused instanceof ComboBoxWidget
                || focused instanceof IntegerSlider slider && slider.isEditingNumber()) {
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.MovementAllowedScreenPopupEscapeTest" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL`; all six tests pass.

- [ ] **Step 6: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
  `PopupOwner`'s open menu or suggestion list and clear focus. A focused `EditBox` or
  `ComboBoxWidget` also yields focus and consumes Escape when no popup is open, so the first
  Escape leaves text entry and a second Escape closes the screen. Do not add per-widget Escape
```
With:
```markdown
  `PopupOwner`'s open menu or suggestion list and clear focus. A focused `EditBox` or
  `ComboBoxWidget` also yields focus and consumes Escape when no popup is open, and so does an
  `IntegerSlider` whose number field rather than its track has focus (`isEditingNumber()`); an
  `IntegerField` commits its number when it loses focus. So the first Escape leaves text entry
  and a second Escape closes the screen. Do not add per-widget Escape
```

- [ ] **Step 7: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `grep -rn "isEditingNumber" mods/src docs/tips/gui/local-guide.md`
→ the method in `IntegerSlider`, its use in `MovementAllowedScreen.dismissFocusedInput()`, and the
guide's Input section.

---

### Task 11: Mod-list entry points

**Files:**
- Modify: `mods/versions/{1.20.1,1.20.2,1.20.4,1.20.6,1.21,1.21.2,1.21.3,1.21.5,1.21.6,1.21.9,1.21.11,26.1.2,26.2,26.3}-fabric/gradle.properties`
- Modify: `mods/fabric.gradle.kts`, `mods/fabric-unobfuscated.gradle.kts`
- Modify: `mods/src/main/resources/fabric.mod.json`
- Create: `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointModMenu.java`
- Modify: `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java`
- Modify: `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java`
- Modify: `README.md`, `README_zh.md`

**Interfaces:**
- Consumes: `public ClientConfigScreen(Screen parentScreen)`.
- Produces: the config button in Mod Menu (Fabric) and in the NeoForge and Forge Mods screens opens
  `ClientConfigScreen` with the mod list as its parent.

- [ ] **Step 1: Add the Mod Menu version to each Fabric target**

Each file ends with its `voxelmap_fabric=` line and a newline. Run these fourteen commands from the
repository root; the versions are the latest Mod Menu release on Modrinth for each target
(checked on 2026-09-28):

```bash
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=7.2.2\n' >> mods/versions/1.20.1-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=8.0.1\n' >> mods/versions/1.20.2-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=9.2.0\n' >> mods/versions/1.20.4-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=10.0.0\n' >> mods/versions/1.20.6-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=11.0.5\n' >> mods/versions/1.21-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=12.0.1\n' >> mods/versions/1.21.2-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=12.0.1\n' >> mods/versions/1.21.3-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=14.0.2\n' >> mods/versions/1.21.5-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=15.0.2\n' >> mods/versions/1.21.6-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=16.0.1\n' >> mods/versions/1.21.9-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=17.0.1\n' >> mods/versions/1.21.11-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=18.0.2\n' >> mods/versions/26.1.2-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=20.0.3\n' >> mods/versions/26.2-fabric/gradle.properties
printf '\n# Mod Menu (compile only; opens the client settings from the mod list)\nmodmenu=21.0.0\n' >> mods/versions/26.3-fabric/gradle.properties
```

Run: `grep -c "^modmenu=" mods/versions/*-fabric/gradle.properties`
Expected: every one of the fourteen files reports `1`.

- [ ] **Step 2: Add the compile-only dependency**

In `mods/fabric.gradle.kts`, replace:
```kotlin
    val voxelmap_fabric: String by project
```
With:
```kotlin
    val voxelmap_fabric: String by project
    val modmenu: String by project
```

Then replace:
```kotlin
    // Use Modrinth version IDs because some VoxelMap version numbers collide with Forge uploads.
    modImplementation("maven.modrinth:voxelmap-updated:$voxelmap_fabric")
```
With:
```kotlin
    // Use Modrinth version IDs because some VoxelMap version numbers collide with Forge uploads.
    modImplementation("maven.modrinth:voxelmap-updated:$voxelmap_fabric")

    // Mod Menu loads ServerWaypointModMenu only when it's installed, so it's needed only to compile.
    modCompileOnly("maven.modrinth:modmenu:$modmenu")
```

In `mods/fabric-unobfuscated.gradle.kts`, replace:
```kotlin
    val voxelmap_fabric: String by project
```
With:
```kotlin
    val voxelmap_fabric: String by project
    val modmenu: String by project
```

Then replace:
```kotlin
    // Use Modrinth version IDs because some VoxelMap version numbers collide with Forge uploads.
    implementation("maven.modrinth:voxelmap-updated:$voxelmap_fabric")
```
With:
```kotlin
    // Use Modrinth version IDs because some VoxelMap version numbers collide with Forge uploads.
    implementation("maven.modrinth:voxelmap-updated:$voxelmap_fabric")

    // Mod Menu loads ServerWaypointModMenu only when it's installed, so it's needed only to compile.
    compileOnly("maven.modrinth:modmenu:$modmenu")
```

- [ ] **Step 3: Register the Mod Menu entry point**

Create `ServerWaypointModMenu.java`:

```java
//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.gui.screens.ClientConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's config button opens the client settings. Mod Menu loads this class only when installed. */
public class ServerWaypointModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClientConfigScreen::new;
    }
}
//?}
```

In `fabric.mod.json`, replace:
```json
        "client": [
            "_959.server_waypoint.fabric.ServerWaypointFabricClient"
        ]
    },
```
With:
```json
        "client": [
            "_959.server_waypoint.fabric.ServerWaypointFabricClient"
        ],
        "modmenu": [
            "_959.server_waypoint.fabric.ServerWaypointModMenu"
        ]
    },
```

Fabric creates the client mod at `CLIENT_STARTED`, before any mod list can open, so the factory
needs no extra initialization.

- [ ] **Step 4: Register the NeoForge hook**

This file is inside `//? if neoforge {`, so its body is a block comment in the active Fabric tree:
inactive nested branches use `/^ … ^/`, and the Javadoc is written `/^* … ^/`, which Stonecutter
turns back into `/** … */` on NeoForge targets. Copy the text exactly; a literal `*/` here breaks
every build.

In `ServerWaypointNeoForgeClient.java`, replace:
```java
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
```
With:
```java
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.ClientConfigScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
```

Replace:
```java
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
```
With:
```java
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
//? if >= 1.20.5 {
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//?} else {
/^import net.neoforged.neoforge.client.ConfigScreenHandler;
^///?}
```

Replace:
```java
        NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onClientTick);
//? if <= 1.20.4 {
        /^NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onRenderGui);
^///?}
    }
```
With:
```java
        NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onClientTick);
//? if <= 1.20.4 {
        /^NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onRenderGui);
^///?}
        registerConfigScreen();
    }

    /^* The Mods screen's config button opens the client settings. ^/
    private static void registerConfigScreen() {
//? if >= 1.21 {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (container, parent) -> createConfigScreen(parent));
//?} elif >= 1.20.5 {
        /^ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (minecraft, parent) -> createConfigScreen(parent));
^///?} else {
        /^ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> createConfigScreen(parent)));
^///?}
    }

    /^* NeoForge creates the client mod on the first client tick; make sure it exists before the screen. ^/
    private static Screen createConfigScreen(Screen parent) {
        ensureClientStarted();
        return new ClientConfigScreen(parent);
    }
```

The factory signatures come from NeoForge's sources: `IConfigScreenFactory.createScreen(ModContainer,
Screen)` on 21.0 and later (NeoForge 21.0.167, published after the change on 2024-07-19, includes
it), `createScreen(Minecraft, Screen)` on 20.6, and `ConfigScreenHandler.ConfigScreenFactory` on 20.2
and 20.4. `initialize` runs during mod construction, where `ModLoadingContext.get()` refers to this
mod.

- [ ] **Step 5: Register the Forge hook**

In `ServerWaypointForgeClient.java`, replace:
```java
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
```
With:
```java
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.ClientConfigScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
```

Replace:
```java
import net.minecraft.client.KeyMapping;
import net.minecraft.network.FriendlyByteBuf;
```
With:
```java
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
```

Replace:
```java
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
```
With:
```java
//? if = 1.20.2
/*import net.minecraftforge.client.ConfigScreenHandler;*/
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
```

Replace:
```java
import net.minecraftforge.fml.ModList;
```
With:
```java
import net.minecraftforge.fml.ModList;
//? if = 1.20.2
/*import net.minecraftforge.fml.ModLoadingContext;*/
```

Replace:
```java
        MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::onClientTick);
        //? if < 1.20.5
        /^MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::onRenderGui);^/
    }
```
With:
```java
        MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::onClientTick);
        //? if < 1.20.5
        /^MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::onRenderGui);^/
        registerConfigScreen();
    }
```

Replace:
```java
        TickEvent.ClientTickEvent.Post.BUS.addListener(ServerWaypointForgeClient::onClientTick);
    }
```
With:
```java
        TickEvent.ClientTickEvent.Post.BUS.addListener(ServerWaypointForgeClient::onClientTick);
        registerConfigScreen();
    }
```

Replace:
```java
        WaypointClientMod.createInstance(net.minecraft.client.Minecraft.getInstance(), FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        OptimizedWaypointRenderer.init();
    }

    public static void registerClientPayloadHandlers() {
```
With:
```java
        WaypointClientMod.createInstance(net.minecraft.client.Minecraft.getInstance(), FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        OptimizedWaypointRenderer.init();
    }

    /** The Mods screen's config button opens the client settings. */
    private static void registerConfigScreen() {
        //? if = 1.20.2 {
        /*ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> createConfigScreen(parent))
        );
        *///?} else {
        MinecraftForge.registerConfigScreen(ServerWaypointForgeClient::createConfigScreen);
        //?}
    }

    /** Forge creates the client mod on the first client tick; make sure it exists before the screen. */
    private static Screen createConfigScreen(Screen parent) {
        ensureClientStarted();
        return new ClientConfigScreen(parent);
    }

    public static void registerClientPayloadHandlers() {
```

`MinecraftForge.registerConfigScreen(Function<Screen, Screen>)` is Forge's helper for this hook. It
exists, without a deprecation, in every Forge release series the build uses (checked at the `47.4`,
`49.2`, `50.2`, `51.0`, `53.1`, `55.1`, `56.0`, `59.0`, `61.1`, `64.0` and `65.0` tags) except
`48.1`, the 1.20.2 target, which doesn't have it. On 1.20.2 the branch makes the call the helper
makes internally: `ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, …)`.
Calling it directly elsewhere would add a `[removal]` warning, because Forge deprecates
`ModLoadingContext.get()` for removal. The method reference has one parameter, so it picks the
`Function` overload, not the `BiFunction<Minecraft, Screen, Screen>` one.

- [ ] **Step 6: Update the READMEs**

In `README.md`, replace:
```markdown
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

## Keybinds
```
With:
```markdown
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)
  - [VoxelMap](https://modrinth.com/mod/voxelmap-updated) (Fabric)
  - [Mod Menu](https://modrinth.com/mod/modmenu) (Fabric): opens the client settings from the mod list

## Keybinds
```

Replace:
```markdown
- In the waypoint manager screen, press `C` to open client configuration screen.
```
With:
```markdown
- In the waypoint manager screen, press `C` to open the client settings. The config button in Mod Menu (Fabric) or the Mods screen (NeoForge and Forge) opens them too.
```

Then replace everything from the line `## Client Configurations` up to, but not including, the line
`Remote catalog synchronization and the remote GUI require matching client and backend versions.`
with (keep one blank line before that line):

```markdown
## Client Configurations

Open the client settings with `C` in the waypoint manager, or with the config button in Mod Menu
(Fabric) or the Mods screen (NeoForge and Forge). Changes apply immediately and are saved when the
screen closes. Hover a row to see what it does and its default. The ↺ button next to a setting resets
it, and **Reset to defaults…** resets them all.

- #### Waypoint rendering
  - **Show in-world waypoints**: draws waypoint markers in the world. Default: `On`.
  - **Scale**: size of the markers, from `0` to `500` percent. Default: `100%`.
  - **Vertical offset**: moves the markers up or down by up to half a block, from `-100` to `100` percent. Default: `0%`.
  - **Background opacity**: opacity of marker backgrounds and icons, from `0` (clear) to `255` (solid). Default: `128`.
  - **Local waypoint range**: waypoints with local visibility are drawn only within this many chunks, from `0` to `1024`; global waypoints are always drawn. Default: `12` chunks.
- #### Map mods
  Xaero's Minimap is supported on every loader and VoxelMap on Fabric. A supported map mod that isn't installed is listed as not installed.
  - **Auto sync**: keeps the waypoints Server Waypoint adds to the map mod up to date as they change on the server. Default: `On`.
  - **Sync now**: after a confirmation, replaces the waypoints Server Waypoint added with the server's current waypoints. Available once you're in a world whose waypoints have synced.

  Server Waypoint marks what it adds: Xaero's Minimap sets and VoxelMap waypoint names carry an internal `sw␟` prefix. Sync only touches these, so your own waypoints are never changed, even ones named like a server list. Changes you made to synced waypoints, and waypoints from lists removed on the server, are replaced. Upload maps the managed names back to their server list and waypoint names.
- #### Appearance
  - **Color theme**: opens the theme editor.

```

In `README_zh.md`, replace:
```markdown
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

## 快捷键
```
With:
```markdown
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)
  - [VoxelMap](https://modrinth.com/mod/voxelmap-updated)（Fabric）
  - [Mod Menu](https://modrinth.com/mod/modmenu)（Fabric）：从模组列表打开客户端设置

## 快捷键
```

Replace:
```markdown
- 在路径点管理界面按下 `C` 可打开客户端配置界面。
```
With:
```markdown
- 在路径点管理界面按下 `C` 可打开客户端设置。也可以通过 Mod Menu（Fabric）或模组列表（NeoForge 和 Forge）中的配置按钮打开。
```

Then replace everything from the line `## 客户端配置` up to, but not including, the line that starts
with `远程目录同步和远程图形界面需要匹配的客户端与后端版本。` with (keep one blank line before that line):

```markdown
## 客户端配置

在路径点管理界面按 `C`，或点击 Mod Menu（Fabric）或模组列表（NeoForge 和 Forge）中的配置按钮，即可打开客户端设置。更改会立即生效，并在关闭界面时保存。将鼠标悬停在某一行上可查看说明和默认值。设置旁的 ↺ 按钮可将其恢复默认，**全部恢复默认…** 会恢复所有设置。

- #### 路径点渲染
  - **在世界中显示路径点**：在世界中绘制路径点标记。默认值：`开`。
  - **缩放**：标记的大小，范围 `0` 到 `500`（百分比）。默认值：`100%`。
  - **垂直偏移**：将标记上移或下移，最多半格，范围 `-100` 到 `100`（百分比）。默认值：`0%`。
  - **背景不透明度**：标记背景和图标的不透明度，从 `0`（透明）到 `255`（不透明）。默认值：`128`。
  - **局部路径点范围**：可见范围为局部的路径点只在此区块数范围内绘制，范围 `0` 到 `1024`；全局路径点始终绘制。默认值：`12` 区块。
- #### 地图模组
  所有加载器都支持Xaero的小地图，Fabric 还支持 VoxelMap。受支持但未安装的地图模组会显示为未安装。
  - **自动同步**：随服务器上的变化，保持本模组添加到地图模组的路径点为最新。默认值：`开`。
  - **立即同步**：确认后，用服务器当前的路径点替换本模组添加的路径点。进入世界且路径点同步完成后可用。

  本模组会标记它添加的内容：Xaero的小地图中的集合和 VoxelMap 中的路径点名称带有内部 `sw␟` 前缀。同步只会修改这些内容，因此你自己的路径点永远不会被更改，即使与服务器列表同名。你对已同步路径点所做的修改，以及服务器上已删除列表中的路径点会被替换。上传时会将这些管理名称映射回服务端的列表和路径点名称。
- #### 外观
  - **颜色主题**：打开主题编辑器。

```

- [ ] **Step 7: Compile every Fabric target and the loader boundaries**

Run: `./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.20.4-fabric:compileJava :mods:1.20.6-fabric:compileJava :mods:1.21-fabric:compileJava :mods:1.21.2-fabric:compileJava :mods:1.21.3-fabric:compileJava :mods:1.21.5-fabric:compileJava :mods:1.21.6-fabric:compileJava :mods:1.21.9-fabric:compileJava :mods:1.21.11-fabric:compileJava :mods:26.1.2-fabric:compileJava :mods:26.2-fabric:compileJava :mods:26.3-fabric:compileJava --max-workers=2 --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Run: `./gradlew :mods:1.20.2-neoforge:compileJava :mods:1.20.4-neoforge:compileJava :mods:1.20.6-neoforge:compileJava :mods:1.21-neoforge:compileJava :mods:26.3-neoforge:compileJava :mods:1.20.1-forge:compileJava :mods:1.20.2-forge:compileJava :mods:1.21.11-forge:compileJava :mods:26.2-forge:compileJava --max-workers=2 --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

Expected: `BUILD SUCCESSFUL` for both. If a Mod Menu version doesn't resolve for a target, pick the
latest release listed for that Minecraft version on Modrinth (the `search-modrinth-metadata` skill
does this) and record the substitution in the validation record.

- [ ] **Step 8: Review checkpoint (no commit)**

Run: `git diff --check` → no output.
Run: `grep -n "//?\|/\^\|\^/" mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java | head -40`
→ the new import and `registerConfigScreen` branches are balanced: every branch's `/^` has a
matching `^/` immediately before its `//?}` line, and both Javadoc lines open with `/^*` and end
with `^/`.
Run: `grep -c "\*/" mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java`
→ `1` (only the closing `*///?}` on the last line).
Run: `grep -n "1.20.2" mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java`
→ three `//? if = 1.20.2` lines: the two single-line import conditions and the `{` branch in
`registerConfigScreen`, whose `*///?} else {` and `//?}` follow the `ModLoadingContext` call.

---

### Task 12: Verification and validation record

**Files:**
- Create: `docs/features/client-config/validation/YYYY-MM-DD-results.md` (the date the checks run)
- Delete: `docs/features/client-config/validation/.gitkeep`
- Modify: `docs/features/client-config/README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the validation record.

- [ ] **Step 1: Run the full automated suite**

Run: `./gradlew :mods:26.1.2-fabric:test --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`
Expected: `BUILD SUCCESSFUL`. Note the test count from `mods/versions/26.1.2-fabric/build/test-results/test`.

- [ ] **Step 2: Run the compile matrix**

Run both commands from Task 11, Step 7 again. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check whitespace and Stonecutter markers**

Run: `git diff --check` → no output.
For every touched Java file with Stonecutter comments (`SettingsListWidget`, `ClientConfigScreen`,
`MovementAllowedScreen`, `WidgetTextures`, `MapModIntegrationsTest`, `ServerWaypointModMenu`,
`ServerWaypointNeoForgeClient`, `ServerWaypointForgeClient`), run `grep -n "//?\|/\*?\|\*///?}\|/\^\|\^/" <file>` and confirm each
`//? if` has its `//?}` (or `*///?}`/`^///?}`) and each `/*` or `/^` branch opener has its closer.

- [ ] **Step 4: Launch clients for the in-game checks**

Fabric (Mod Menu, Xaero's Minimap and VoxelMap available in the dev runtime):
`./gradlew :mods:26.1.2-fabric:runClient --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

NeoForge and Forge, for the Mods-screen hooks:
`./gradlew :mods:26.3-neoforge:runClient --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`
`./gradlew :mods:26.2-forge:runClient --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`

If a target has no `runClient` task, use the `stonecutter-gradle-version-tasks` skill to find its run
task. Mod Menu is compile-only, so for check 8 put the target's Mod Menu jar in the run's `mods`
folder. A player must look at the screen for these checks; if nobody can, record them as not run.

- [ ] **Step 5: In-game checks**

Note the Minecraft version and loader for each item. Compiling can't prove these:

1. **Layout:** at GUI widths of 480, 378 and 320 pixels and heights of 240 and 270, the title, panel
   and footer fit inside the margins; at 320, long labels wrap and their rows grow; with Spanish
   selected at 320, nothing overlaps the unit or reset columns or the footer buttons; the list
   scrolls when it doesn't fit.
2. **Tooltips:** resting on a row for half a second shows its description and "Default: …"; the
   reset icon shows "Reset to default: …"; nothing shows through an open dialog.
3. **Keyboard:** with the list scrolled, Tab and Shift-Tab reach every control in order and the list
   scrolls to show it and its neighbors; the list itself never takes focus. In a world, after Tab,
   Space presses the focused button instead of jumping; after a mouse click, Space jumps and Enter
   still presses the button.
4. **Mouse wheel:** over a slider while the list overflows, the wheel scrolls the list; with the
   window tall enough for everything, it changes the slider.
5. **Reset:** changing each rendering setting changes the waypoints in the world immediately; each
   row's ↺ restores its default and then disables itself; "Reset to defaults…" asks first, resets
   everything shown, shows "Settings reset to defaults." and leaves focus on Done.
6. **Sync:** with a personal Xaero's Minimap set and a personal VoxelMap waypoint named like a server
   list, "Sync now" asks first, then shows "Synced waypoints to …" and leaves the personal ones
   untouched. The Sync button is disabled with the right reason on the title screen ("Join a world
   to sync."), while waypoints are still syncing, and on a server without Server Waypoint.
7. **Escape and focus:** Escape closes an open dialog, not the screen, and focus returns to the button
   that opened it (or Done after a reset); a second Escape closes the screen. The same holds from the
   keyboard: Enter on "Sync…" or "Reset to defaults…" opens its dialog with Cancel focused, Enter on
   Cancel or the confirm button closes it with the same focus result, and Enter on a row's ↺ resets
   the row and focuses its control. Escape while typing in a number field leaves the field, keeps
   the typed value (the waypoints in the world change) and keeps the screen open; the next Escape
   closes it. After dragging a slider's track, Escape closes the screen at once. The theme editor's
   opacity field behaves the same way.
8. **Mod lists:** the config button in Mod Menu (Fabric) and in the NeoForge and Forge Mods screens
   opens the screen on the title screen (panorama under the overlay, Sync disabled) and from the
   pause menu in a world; Done returns to the mod list.
9. **Saving:** after a disconnect with the screen open, the changed values are still set after
   rejoining.
10. **Themes:** after switching between Translucent Dark, Modern Dark and High Contrast in the theme
    editor, the screen's panel, rows and dialogs follow the theme.
11. **Missing mods:** without Xaero's Minimap or VoxelMap, its row reads "Not installed" in muted
    text with the install tooltip; on NeoForge and Forge there's no VoxelMap row at all.

- [ ] **Step 6: Write the validation record**

Create `docs/features/client-config/validation/YYYY-MM-DD-results.md` with the actual date. Fill in
every section from what you observed; don't claim a check you didn't run:

```markdown
# Client config screen validation

## Automated checks

- `./gradlew :mods:26.1.2-fabric:test`: <result and test count>.
- Fabric compile matrix (14 targets): <result>.
- NeoForge 1.20.2, 1.20.4, 1.20.6, 1.21 and 26.3, and Forge 1.20.1, 1.20.2, 1.21.11 and 26.2 compile: <result>.
- `git diff --check` and Stonecutter marker review: <result>.
- Mod Menu versions: <"as planned", or each substitution with its reason>.

## In-game checks

<One line per item from Task 12, Step 5: version and loader, then passed, failed (with what was
seen) or not run.>

## Known behavior

On servers where VoxelMap asks the player to choose a world, a sync before that choice goes to
VoxelMap's current world, as auto sync already does. The non-English strings are drafts that need
a native speaker's review before release.
```

Delete `docs/features/client-config/validation/.gitkeep`.

In `docs/features/client-config/README.md`, replace:
```markdown
- [Implementation plan](plans/2026-09-28-client-config-screen.md)

Validation records don't exist yet; `validation/` holds a `.gitkeep` until they do.
```
With:
```markdown
- [Implementation plan](plans/2026-09-28-client-config-screen.md)
- [Validation results](validation/YYYY-MM-DD-results.md)
```
(using the record's real file name).

- [ ] **Step 7: Final review checkpoint (no commit)**

Run: `git status --short` and compare with the File Map: every listed file is changed, created or
deleted, and nothing else is. Report the results to the user; commit only if they ask.
