# Themed Tooltips Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every vanilla tooltip in the mod's own `MovementAllowedScreen` screens with a themed `TranslucentTooltip`, keeping vanilla's text, wrapping, timing, placement and narration.

**Architecture:** Controls store a `Component` tooltip and request it from a static, per-frame `TooltipLayer` at the end of their renderer; lists and forms request pointer tooltips with `TooltipLayer.scheduleAtPointer`. `MovementAllowedScreen` clears the layer when a frame starts and draws the one winning request last, on its own layer, through a shared `TranslucentTooltip` placed by the pure `TooltipPlacement` rules: vanilla's three positioners plus a top clamp. No mixin.

**Tech Stack:** Java 17; Minecraft client GUI on Fabric, Forge and NeoForge, 1.20.1 to 26.3, through Stonecutter 0.9 versioned comments; JUnit 5; Gradle Kotlin DSL.

**Spec:** [`docs/features/gui-tooltips/specs/2026-10-09-themed-tooltips-design.md`](../specs/2026-10-09-themed-tooltips-design.md), approved on 2026-10-09. Read it alongside this plan; "§n" below means its section n.

Paths below use two prefixes:

- `<gui>` = `mods/src/main/java/_959/server_waypoint/common/client/gui`
- `<gui-test>` = `mods/src/test/java/_959/server_waypoint/common/client/gui`

## Global Constraints

- Work in the main checkout, `/Volumes/ssd/fabric_mods_repo/server_waypoint`, on branch `4.0.0`, not in a worktree. Every target there already has build outputs, so Task 9's full compile is quick and cheap on disk, and Task 10's dev-client worlds live in its git-ignored `mods/versions/*/run/saves`. Leave the active Stonecutter target at `26.1.2-fabric`.
- Java 17 features only. Indent with 4 spaces, never tabs. No backward-compatibility code (AGENTS.md).
- Don't commit. AGENTS.md forbids it unless the user asks, so each task ends with a checkpoint instead, with a commit message to use if the user does ask.
- Other sessions edit files and switch branches in this checkout. At the start of each task, run `git branch --show-current` (expect `4.0.0`) and `git status --short`. Leave files you didn't change alone, and never revert them.
- No new theme variables, translation keys, Stonecutter swaps or replacements, no mixin, and no AGENTS.md change (spec Scope and §10). Tooltip texts don't change.
- Colors (§1): fill `WidgetThemeVariable.POPUP_BACKGROUND`, 1-pixel outline `WidgetThemeVariable.BORDER`, text `WidgetThemeVariable.TEXT_PRIMARY` with a shadow. Resolve them every time the tooltip is drawn.
- Geometry (§1): wrap at 170 pixels, lines `font.lineHeight` (9) apart, text block `9 × lines − 1` high, box 4 pixels outside the text block on every side. Draw the fill, then the outline, then the text.
- One request per frame (§3): the first wins, and only a focused control's request replaces it.
- Every placement rule ends with `y = max(y, 4)` (§4).
- Layer (§5): `nextStratum()` from 1.21.6, and a z translation of 400 before that.
- Narration (§6): the tooltip is added as `NarratedElementType.HINT`. Pointer tooltips aren't narrated.
- Remove a tooltip with `setTooltip((Component) null)`. Plain `setTooltip(null)` is ambiguous next to vanilla's `setTooltip(Tooltip)` and doesn't compile (§2).
- Don't name a new no-argument method `getTooltip`: `AbstractWidget.getTooltip()` exists on 1.20.4 to 1.21.5.
- Stonecutter: a file that names `GuiGraphicsExtractor` starts with `//~ gui_graphics_26`, before any non-comment line. Keep `//$ render_method_swap` and `//$ render_widget_method_swap` attached to the method name. After each versioned edit, check the touched file's `//? if`, `//?}`, `/*?`, `/*?}*/` and `*//*?` markers for balance.

## Running Gradle here

Gradle on this machine needs the SSD Gradle home, the Homebrew JDK 25 path for 26.x targets and a disk watchdog. Shell variables on the Bash tool's command line get mangled, so create this script once per session in your scratchpad directory (not in the repo), as `gw.zsh`:

```zsh
#!/bin/zsh
# Gradle with this machine's settings. Usage: zsh gw.zsh <tasks and flags>
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home
cd /Volumes/ssd/fabric_mods_repo/server_waypoint || exit 1
# Stop Gradle if the SSD drops under 1.5 GiB free or the system volume under 2 GiB.
( while sleep 5; do
    ssd=$(df -k /Volumes/ssd | awk 'NR==2 {print $4}')
    sys=$(df -k /System/Volumes/Data | awk 'NR==2 {print $4}')
    if (( ssd < 1572864 || sys < 2097152 )); then
        pkill -f org.gradle.wrapper.GradleWrapperMain; ./gradlew --stop; exit
    fi
done ) &
watchdog=$!
./gradlew "$@" -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
gradle_exit=$?
kill $watchdog 2>/dev/null
exit $gradle_exit
```

- Run it as `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipPlacementTest'`. Quote test patterns, since zsh aborts on an unmatched glob.
- `--tests` applies only to the task just before it. To filter two tasks, give each its own `--tests`.
- Don't pass `--offline`: Forge and NeoForge targets can't resolve `io.netty:netty-buffer:4.1.+` offline. If an online run fails with "NeoForged Releases is disabled due to earlier error" on that dependency, rerun it once.
- Run only Fabric tests. Plain JUnit can't bootstrap Minecraft on many Forge and NeoForge targets, so those targets are only compiled.

## Review Focus

These five cases follow from the spec but none of its listed tests covers them. Each one gets a test in the task named:

1. A control that is inactive but hovered still shows its tooltip. The remote teleport button depends on this to explain why it's disabled. Test: `inactiveControlStillShowsItsTooltip` (Task 4).
2. A control focused by a click and still hovered replaces an earlier request in the same frame ("whichever input focused it", §3). Test: `controlFocusedByAClickAndHoveredReplacesAnEarlierRequest` (Task 4).
3. Switching the game's language while a tooltip's message stays equal wraps it again, in the new language's text. Test: `languageChangeWrapsAnEqualMessageAgain` (Task 2).
4. When drawing a tooltip throws, the request doesn't show again in a later frame. Test: `renderDropsTheRequestEvenWhenDrawingFails` (Task 3).
5. A control with zero height doesn't break the rule for tooltips beside a control, whose vanilla formula divides by the height. Test: `besideControlCountsAZeroHeightControlAsOnePixelHigh` (Task 1).

---

### Task 1: Placement rules

**Files:**
- Create: `<gui>/layout/TooltipPlacement.java`
- Test: `<gui-test>/layout/TooltipPlacementTest.java`

**Interfaces:**
- Produces, in package `_959.server_waypoint.common.client.gui.layout`:
  ```java
  public final class TooltipPlacement {
      public record Position(int x, int y) { }
      public static Position atPointer(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height);
      public static Position besideControl(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height, ScreenRectangle control);
      public static Position belowOrAboveControl(int screenWidth, int screenHeight, int width, int height, ScreenRectangle control);
  }
  ```
  `width` and `height` are the text block's size. Each method returns the text block's top-left corner. `ScreenRectangle` is `net.minecraft.client.gui.navigation.ScreenRectangle`.

- [ ] **Step 1: Write the failing test**

```java
package _959.server_waypoint.common.client.gui.layout;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooltipPlacementTest {
    private static final int W = 320;
    private static final int H = 240;
    /** Left 100, top 50, right 140, bottom 70. */
    private static final ScreenRectangle CONTROL = new ScreenRectangle(100, 50, 40, 20);

    private static TooltipPlacement.Position at(int x, int y) {
        return new TooltipPlacement.Position(x, y);
    }

    @Test
    void atPointerSitsRightOfAndAboveThePointer() {
        assertEquals(at(112, 88), TooltipPlacement.atPointer(W, H, 100, 100, 50, 8));
    }

    @Test
    void atPointerFlipsLeftAtTheRightEdge() {
        assertEquals(at(238, 88), TooltipPlacement.atPointer(W, H, 300, 100, 50, 8));
        assertEquals(at(4, 88), TooltipPlacement.atPointer(W, H, 40, 100, 300, 8));
    }

    @Test
    void atPointerStaysAboveTheBottomEdge() {
        assertEquals(at(112, 220), TooltipPlacement.atPointer(W, H, 100, 235, 50, 17));
    }

    @Test
    void atPointerKeepsTheBoxTopOnTheScreen() {
        assertEquals(at(112, 4), TooltipPlacement.atPointer(W, H, 100, 10, 50, 8));
    }

    @Test
    void besideControlGoesBelowItByWhereThePointerIs() {
        // y0 = my + 3. Pointer 10 pixels into the control: off = round(lerp(13 / 20, 17, 5)) = 9.
        assertEquals(at(122, 72), TooltipPlacement.besideControl(W, H, 110, 60, 50, 8, CONTROL));
        // Pointer on the top edge: off = round(lerp(3 / 20, 17, 5)) = 15.
        assertEquals(at(122, 68), TooltipPlacement.besideControl(W, H, 110, 50, 50, 8, CONTROL));
        // Pointer on the bottom edge: off = 5.
        assertEquals(at(122, 77), TooltipPlacement.besideControl(W, H, 110, 69, 50, 8, CONTROL));
    }

    @Test
    void besideControlGoesAboveItNearTheBottom() {
        ScreenRectangle low = new ScreenRectangle(100, 200, 40, 20); // bottom 220
        // 220 + 3 + 17 + (8 + 6) > 240 - 5, so above: y = 213 - 14 - round(lerp(7 / 20, 17, 5)) = 186.
        assertEquals(at(122, 186), TooltipPlacement.besideControl(W, H, 110, 210, 50, 8, low));
    }

    @Test
    void besideControlFlipsLeftAtTheRightEdge() {
        assertEquals(at(228, 72), TooltipPlacement.besideControl(W, H, 290, 60, 50, 8, CONTROL));
        assertEquals(at(9, 72), TooltipPlacement.besideControl(W, H, 30, 60, 300, 8, CONTROL));
    }

    @Test
    void besideControlKeepsTheBoxTopOnTheScreen() {
        ScreenRectangle control = new ScreenRectangle(100, 30, 40, 20); // bottom 50
        // A 60-pixel screen and a 26-pixel text block: above, y = 43 - 32 - 13 = -2.
        assertEquals(at(112, 4), TooltipPlacement.besideControl(W, 60, 100, 40, 50, 26, control));
    }

    @Test
    void besideControlCountsAZeroHeightControlAsOnePixelHigh() {
        ScreenRectangle flat = new ScreenRectangle(100, 50, 40, 0);
        // With height 1, off = round(lerp(min(3, 1) / 1, -2, 5)) = 5. Without the guard it would be 0.
        assertEquals(at(122, 58), TooltipPlacement.besideControl(W, H, 110, 50, 50, 8, flat));
    }

    @Test
    void belowOrAboveControlGoesBelowIt() {
        assertEquals(at(103, 74), TooltipPlacement.belowOrAboveControl(W, H, 50, 8, CONTROL));
    }

    @Test
    void belowOrAboveControlGoesAboveItNearTheBottom() {
        ScreenRectangle low = new ScreenRectangle(100, 220, 40, 16); // bottom 236
        assertEquals(at(103, 208), TooltipPlacement.belowOrAboveControl(W, H, 50, 8, low));
    }

    @Test
    void belowOrAboveControlShiftsLeftAtTheRightEdge() {
        ScreenRectangle right = new ScreenRectangle(290, 50, 20, 20); // right 310
        assertEquals(at(257, 74), TooltipPlacement.belowOrAboveControl(W, H, 50, 8, right));
        assertEquals(at(4, 74), TooltipPlacement.belowOrAboveControl(W, H, 330, 8, right));
    }

    @Test
    void belowOrAboveControlKeepsTheBoxTopOnTheScreen() {
        ScreenRectangle top = new ScreenRectangle(100, 10, 40, 16); // bottom 26
        assertEquals(at(103, 4), TooltipPlacement.belowOrAboveControl(W, 30, 50, 8, top));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipPlacementTest'`
Expected: FAIL. `compileTestJava` reports `cannot find symbol` for `TooltipPlacement`.

- [ ] **Step 3: Implement `TooltipPlacement`**

Use the formulas in §4, in that order, and end each method with `y = Math.max(y, 4)`. The class is `final`, with a private constructor and no state. Its Javadoc says the rules are vanilla's `DefaultTooltipPositioner`, `MenuTooltipPositioner` and `BelowOrAboveWidgetTooltipPositioner`, the same from 1.20.1 to 26.3, plus the top clamp and the zero-height guard.

Compute `off(a, b)` with float arithmetic, exactly as vanilla's `MenuTooltipPositioner.getOffset` does, so rounding matches on every version:

```java
int height = Math.max(1, control.height()); // the zero-height guard
int distance = Math.min(Math.abs(a - b), height);
return Math.round(Mth.lerp((float) distance / (float) height, (float) (height - 3), 5.0F));
```

The guard changes only this height. `control.top()` and `control.bottom()` stay as given.

- [ ] **Step 4: Run it to make sure it passes**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipPlacementTest'`
Expected: `BUILD SUCCESSFUL`, 13 tests.

- [ ] **Step 5: Checkpoint**

`git status --short` lists the two files above. Commit message, if the user asks for commits: `feat(mods): add tooltip placement rules`.

---

### Task 2: The tooltip surface

**Files:**
- Modify: `<gui>/widgets/ScalableText.java`: add `getTextWidth()` after `getHeight()` (about line 96)
- Create: `<gui>/widgets/TranslucentTooltip.java`
- Test: `<gui-test>/widgets/ScalableTextTest.java` (new), `<gui-test>/widgets/TranslucentTooltipTest.java` (new)
- Modify: `<gui-test>/widgets/PaddingWidgetContractTest.java`: add `assertPadding(TranslucentTooltip.class);` at the end of the list

**Interfaces:**
- Produces:
  - `public int getTextWidth()` on `ScalableText`: the widest wrapped line's width, scaled and rounded like `getWidth()`. Without a maximum width, it's the whole text's width. If there are no lines, it's 0. `getWidth()` doesn't change.
  - `public final class TranslucentTooltip extends ShiftableWidget implements Padding`, in package `widgets`:
    - `public TranslucentTooltip(Font font)`
    - `public void setMessage(Component message)`
    - `public boolean isEmpty()`
    - `getWidth()` and `getHeight()` give the text block. `getVisualX()`, `getVisualY()`, `getVisualWidth()` and `getVisualHeight()` add 4 pixels on every side.
    - The render method uses `//$ render_method_swap`.

- [ ] **Step 1: Write the failing tests**

`ScalableTextTest` uses the shared `TestFont` from `<gui-test>`: 6 pixels per character, never wraps.

```java
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScalableTextTest {
    @Test
    void textWidthWithoutWrappingIsTheWholeText() {
        assertEquals(24, new ScalableText(0, 0, Component.literal("abcd"), 0xFFFFFFFF, new TestFont()).getTextWidth());
    }

    @Test
    void textWidthIsScaled() {
        assertEquals(12, new ScalableText(0, 0, Component.literal("abcd"), 0.5F, 0xFFFFFFFF, new TestFont()).getTextWidth());
    }

    @Test
    void textWidthWhileWrappingIsTheWidestLineNotTheMaximumWidth() {
        ScalableText text = new ScalableText(0, 0, Component.literal("abcd"), 1.0F, 0xFFFFFFFF, 100, new TestFont());
        assertEquals(100, text.getWidth());
        assertEquals(24, text.getTextWidth());
    }
}
```

`TranslucentTooltipTest` needs a font that really wraps. Write a private nested `WrappingFont extends Font` with this contract:

- Every character is 6 pixels wide, for all three `width` overloads: `String`, `FormattedText` and `FormattedCharSequence` (count the code points the sequence visits, as `TestFont` does).
- `split(FormattedText text, int maxWidth)` increments `int splitCalls` and stores `maxWidth` in `int lastMaxWidth`.
- An empty text splits into no lines. Otherwise the text is cut at every `\n`, and an empty piece becomes one `FormattedCharSequence.EMPTY` line. Each other piece is cut into lines of `Math.max(1, maxWidth / 6)` whole characters, each made with `FormattedCharSequence.forward(chunk, Style.EMPTY)`.
- Use `TestFont`'s constructor branch verbatim:
  ```java
          //? if >= 1.21.9 {
          super((Font.Provider) null);
          //?} else {
          /*super(null, false);
          *///?}
  ```

Also write a private nested `DelegatingLanguage extends Language`. It passes its four abstract methods (`getOrDefault(String, String)`, `has(String)`, `isDefaultRightToLeft()`, `getVisualOrder(FormattedText)`) to a wrapped `Language`. These four are the same from 1.20.1 to 26.3.

```java
    @Test
    void wrapsAt170Pixels() {
        WrappingFont font = new WrappingFont();
        TranslucentTooltip tooltip = new TranslucentTooltip(font);
        tooltip.setMessage(Component.literal("x".repeat(60)));
        assertEquals(170, font.lastMaxWidth);
        // 28 characters fit in 170 pixels: lines of 28, 28 and 4.
        assertEquals(168, tooltip.getWidth());
        assertEquals(26, tooltip.getHeight());
    }

    @Test
    void newlineStartsANewLine() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.literal("abc\ndefgh"));
        assertEquals(30, tooltip.getWidth());
        assertEquals(17, tooltip.getHeight());
    }

    @Test
    void oneLineIsEightPixelsHigh() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.literal("abc"));
        assertEquals(18, tooltip.getWidth());
        assertEquals(8, tooltip.getHeight());
    }

    @Test
    void boxExtendsFourPixelsPastTheText() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.literal("abc"));
        tooltip.setPosition(20, 30);
        assertEquals(16, tooltip.getVisualX());
        assertEquals(26, tooltip.getVisualY());
        assertEquals(26, tooltip.getVisualWidth());
        assertEquals(16, tooltip.getVisualHeight());
    }

    @Test
    void equalMessageIsNotWrappedAgain() {
        WrappingFont font = new WrappingFont();
        TranslucentTooltip tooltip = new TranslucentTooltip(font);
        tooltip.setMessage(Component.literal("abc"));
        int splits = font.splitCalls;
        tooltip.setMessage(Component.literal("abc"));
        assertEquals(splits, font.splitCalls);
        tooltip.setMessage(Component.literal("abd"));
        assertEquals(splits + 1, font.splitCalls);
    }

    @Test
    void languageChangeWrapsAnEqualMessageAgain() {
        WrappingFont font = new WrappingFont();
        TranslucentTooltip tooltip = new TranslucentTooltip(font);
        tooltip.setMessage(Component.literal("abc"));
        int splits = font.splitCalls;
        Language original = Language.getInstance();
        Language.inject(new DelegatingLanguage(original));
        try {
            tooltip.setMessage(Component.literal("abc"));
            assertEquals(splits + 1, font.splitCalls);
        } finally {
            Language.inject(original);
        }
    }

    @Test
    void messagesWithoutCharactersAreEmpty() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.empty());
        assertTrue(tooltip.isEmpty());
        tooltip.setMessage(Component.literal("\n"));
        assertTrue(tooltip.isEmpty());
        tooltip.setMessage(Component.literal("a"));
        assertFalse(tooltip.isEmpty());
    }
```

`Language.getInstance()` works in plain JUnit: vanilla's `en_us.json` is on the 26.1.2 and 1.20.1 Fabric test classpaths.

- [ ] **Step 2: Run them to make sure they fail**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*ScalableTextTest' --tests '*TranslucentTooltipTest' --tests '*PaddingWidgetContractTest'`
Expected: FAIL. `cannot find symbol` for `getTextWidth` and `TranslucentTooltip`.

- [ ] **Step 3: Add `ScalableText.getTextWidth()`**

Give it a one-line Javadoc that states the contract in Interfaces.

- [ ] **Step 4: Implement `TranslucentTooltip`**

- The file starts with `//~ gui_graphics_26`. Fields: `private static final int MAX_WIDTH = 170;`, `private static final VisualBounds VISUAL_BOUNDS = new VisualBounds(4, 4, 4, 4);`, a retained `ScalableText`, the current `@Nullable Component message` and the `@Nullable Language` used for the last wrap.
- The constructor calls `super(0, 0, 0, 0)` and makes `new ScalableText(0, 0, Component.empty(), 1.0F, WidgetThemeVariable.TEXT_PRIMARY, MAX_WIDTH, font)`.
- `setMessage` calls `text.setText(message)` only when `!message.equals(this.message)` or `Language.getInstance() != this.wrappedWith`. It then records both.
- `isEmpty()` returns `text.getTextWidth() == 0`. Only characters take width, so this is §2's "the wrapped text has no characters".
- `getWidth()` returns `text.getTextWidth()`. `getHeight()` returns `text.getHeight() - 1`, since `ScalableText` is 9 pixels per line at scale 1. The `Padding` methods go through `VISUAL_BOUNDS`.
- The renderer fills the visual rectangle with `WidgetThemeManager.getColor(POPUP_BACKGROUND)` and draws `DrawContextHelper.renderOutline` over that rectangle in `getColor(BORDER)`. It then moves the `ScalableText` to `(getX(), getY())` with `setPosition` and renders it through its own `//$ render_method_swap` call.
- The class Javadoc says the surface is never registered with a screen, takes no input, and is drawn by `TooltipLayer`.

- [ ] **Step 5: Run them to make sure they pass, on both sides of 1.21.9**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*ScalableTextTest' --tests '*TranslucentTooltipTest' --tests '*PaddingWidgetContractTest' :mods:1.20.1-fabric:test --tests '*TranslucentTooltipTest'`
Expected: `BUILD SUCCESSFUL`. On 26.1.2: 3 + 7 + 1 tests. On 1.20.1: 7, which checks the `super(null, false)` branch.

- [ ] **Step 6: Checkpoint**

`git status --short` lists the five files above. Commit message, if asked: `feat(mods): add the themed tooltip surface`.

---

### Task 3: The tooltip layer

**Files:**
- Modify: `<gui>/render/DrawContextHelper.java`: add the layer pair after `previousItemOverlayLayer` (about line 207)
- Create: `<gui>/widgets/TooltipLayer.java`
- Test: `<gui-test>/widgets/TooltipLayerTest.java` (new)

**Interfaces:**
- Consumes: `TooltipPlacement` (Task 1), `TranslucentTooltip` (Task 2).
- Produces:
  - `public static void nextTooltipLayer(GuiGraphicsExtractor context)` and `public static void previousTooltipLayer(GuiGraphicsExtractor context)` on `DrawContextHelper`.
  - In package `widgets`:
    ```java
    public final class TooltipLayer {
        enum Anchor { POINTER, BESIDE_CONTROL, BELOW_OR_ABOVE_CONTROL }
        record Request(Component text, Anchor anchor, int mouseX, int mouseY, @Nullable ScreenRectangle control) { }

        public static void scheduleAtPointer(Component text, int mouseX, int mouseY);
        static void scheduleForControl(Component text, ScreenRectangle control, boolean hovered, boolean focused,
                                       boolean keyboardNavigating, int mouseX, int mouseY);
        public static void render(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight);
        public static void clear();
        static @Nullable Request scheduled();
    }
    ```
    `control` is `null` for `POINTER` requests.

- [ ] **Step 1: Write the failing test**

```java
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TooltipLayerTest {
    private static final ScreenRectangle CONTROL = new ScreenRectangle(100, 50, 40, 20);

    @BeforeEach
    @AfterEach
    void clearLayer() {
        TooltipLayer.clear();
    }

    @Test
    void firstPointerRequestWins() {
        TooltipLayer.scheduleAtPointer(Component.literal("first"), 10, 20);
        TooltipLayer.scheduleAtPointer(Component.literal("second"), 30, 40);
        assertEquals(new TooltipLayer.Request(Component.literal("first"), TooltipLayer.Anchor.POINTER, 10, 20, null),
                TooltipLayer.scheduled());
    }

    @Test
    void focusedControlReplacesAnEarlierRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, false, true, true, 10, 20);
        assertEquals(new TooltipLayer.Request(Component.literal("control"),
                TooltipLayer.Anchor.BELOW_OR_ABOVE_CONTROL, 10, 20, CONTROL), TooltipLayer.scheduled());
    }

    @Test
    void unfocusedControlDoesNotReplaceAnEarlierRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, true, false, false, 110, 60);
        assertEquals(Component.literal("pointer"), TooltipLayer.scheduled().text());
    }

    @Test
    void pointerRequestDoesNotReplaceAControlRequest() {
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, true, false, false, 110, 60);
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        assertEquals(new TooltipLayer.Request(Component.literal("control"),
                TooltipLayer.Anchor.BESIDE_CONTROL, 110, 60, CONTROL), TooltipLayer.scheduled());
    }

    @Test
    void focusedControlWithoutKeyboardNavigationRequestsNothing() {
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, false, true, false, 10, 20);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void clearDropsTheRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipLayer.clear();
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void renderDrawsNothingWithoutARequestOrForEmptyText() {
        // A null context fails on the first draw call, so returning normally means nothing was drawn.
        TooltipLayer.render(null, new TestFont(), 320, 240);
        TooltipLayer.scheduleAtPointer(Component.empty(), 10, 20);
        TooltipLayer.render(null, new TestFont(), 320, 240);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void renderDropsTheRequestEvenWhenDrawingFails() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        assertThrows(NullPointerException.class, () -> TooltipLayer.render(null, new TestFont(), 320, 240));
        assertNull(TooltipLayer.scheduled());
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipLayerTest'`
Expected: FAIL. `cannot find symbol` for `TooltipLayer`.

- [ ] **Step 3: Add the layer pair to `DrawContextHelper`**

It follows the `nextItemOverlayLayer` pair:

```java
    /**
     * Moves the tooltip above everything drawn earlier in the frame. Newer versions start a new render
     * stratum; older versions translate to vanilla's tooltip depth, above GUI item models and the item
     * overlay layer. Pair each call with {@link #previousTooltipLayer}.
     */
    public static void nextTooltipLayer(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        context.nextStratum();
        //?} else {
        /*context.pose().translate(0.0F, 0.0F, 400.0F);
        *///?}
    }

    public static void previousTooltipLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -400.0F);
        *///?}
    }
```

- [ ] **Step 4: Implement `TooltipLayer`**

- The file starts with `//~ gui_graphics_26`. The class is `final`, with a private constructor, `private static @Nullable Request scheduled` and `private static @Nullable TranslucentTooltip surface`. The class Javadoc says it's static frame state used on the render thread only, cleared by `MovementAllowedScreen` when a frame starts and drawn at the frame's end.
- `scheduleAtPointer` stores a `POINTER` request only when nothing is scheduled. Its Javadoc says the coordinates are screen-space, never coordinates after a render translation.
- `scheduleForControl`: if `hovered`, the anchor is `BESIDE_CONTROL`. Otherwise, if `focused && keyboardNavigating`, it's `BELOW_OR_ABOVE_CONTROL`. Otherwise it returns without a request. It stores the request when nothing is scheduled or `focused` is true.
- `render` takes the request out and clears `scheduled` before doing anything else, so a frame whose drawing throws leaves nothing behind. It returns if there was no request. It creates `surface` with `font` on first use and calls `surface.setMessage(request.text())`, returning if `surface.isEmpty()`. It places the text block with the `TooltipPlacement` rule for the anchor, using `surface.getWidth()` and `getHeight()`, then calls `surface.setPosition(position.x(), position.y())`. Then it draws:
  ```java
          nextTooltipLayer(context);
          try {
              surface.
              //$ render_method_swap
              extractRenderState
                      (context, NO_MOUSE, NO_MOUSE, 0.0F);
          } finally {
              previousTooltipLayer(context);
          }
  ```

- [ ] **Step 5: Run it to make sure it passes, and compile the branch before 1.21.6**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipLayerTest' :mods:1.20.1-fabric:compileJava`
Expected: `BUILD SUCCESSFUL`, 8 tests.

Also run `grep -n "//?\|/\*?\|\*///?" <gui>/render/DrawContextHelper.java`. Each `//? if` in the two new methods has its `//?}` or `*///?}`.

- [ ] **Step 6: Checkpoint**

`git status --short` lists the three files above. Commit message, if asked: `feat(mods): add the per-frame tooltip layer`.

---

### Task 4: Tooltips for controls

**Files:**
- Modify: `<gui>/widgets/ShiftableClickableWidget.java`
- Modify: `<gui>/widgets/IconListWidget.java:154`: delete the dead `this.setTooltip(null);`. It stops compiling once the new overload exists.
- Test: `<gui-test>/widgets/TooltipLayerTest.java`: add `//~ gui_graphics_26` as its first line, a test widget and six tests

**Interfaces:**
- Consumes: `TooltipLayer.scheduleForControl(...)` (Task 3), and `VisualPositioning.getVisualX/Y/Width/Height(LayoutElement)` (existing).
- Produces, on `ShiftableClickableWidget`:
  - `public void setTooltip(@Nullable Component tooltip)` stores the tooltip in a private `@Nullable Component tooltip` field. `null` removes it. Use `org.jetbrains.annotations.Nullable`, as the package does.
  - `protected final void scheduleTooltip(int mouseX, int mouseY)` returns at once without a tooltip. Otherwise it calls `TooltipLayer.scheduleForControl(tooltip, new ScreenRectangle(visualX, visualY, visualWidth, visualHeight), isHovered(), isFocused(), isKeyboardNavigating(), mouseX, mouseY)`, with the four visual values from `VisualPositioning`.
  - `protected boolean isKeyboardNavigating()` returns `Minecraft.getInstance().getLastInputType().isKeyboard()`.

- [ ] **Step 1: Write the failing tests**

Add a private nested test double, `TooltipWidget extends ShiftableClickableWidget implements Padding`:

- Its content is at (100, 50), 40 × 20: `super(100, 50, 40, 20, Component.literal("Control"))`, then `setX(100)` and `setY(50)` so the shifted coordinates are set. Its visual bounds are one pixel outside the content, like `ColorSquareButton`'s: `getX() - 1`, `getY() - 1`, `width + 2`, `height + 2`.
- `hover()` sets the protected `isHovered` field to `true`.
- It has two flags, `keyboardNavigating` and `readingInputFails`. Its `isKeyboardNavigating()` override throws `AssertionError("The widget read the game's input state")` while `readingInputFails` is set, and otherwise returns `keyboardNavigating`.
- Its renderer is empty and written with `//$ render_widget_method_swap` attached to `extractWidgetRenderState`, as in the guide's example widget. Its `updateWidgetNarration` is empty.

The new tests, plus `private static final ScreenRectangle VISUAL = new ScreenRectangle(99, 49, 42, 22);`:

```java
    @Test
    void hoveredControlRequestsItsTooltipBesideItsVisualBounds() {
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.hover();
        widget.scheduleTooltip(110, 60);
        assertEquals(new TooltipLayer.Request(Component.literal("tip"), TooltipLayer.Anchor.BESIDE_CONTROL, 110, 60, VISUAL),
                TooltipLayer.scheduled());
    }

    @Test
    void keyboardFocusedControlRequestsItsTooltipBelowOrAbove() {
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.setFocused(true);
        widget.keyboardNavigating = true;
        widget.scheduleTooltip(10, 20);
        assertEquals(new TooltipLayer.Request(Component.literal("tip"),
                TooltipLayer.Anchor.BELOW_OR_ABOVE_CONTROL, 10, 20, VISUAL), TooltipLayer.scheduled());
    }

    @Test
    void mouseFocusedControlThatIsNotHoveredRequestsNothing() {
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.setFocused(true);
        widget.scheduleTooltip(10, 20);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void controlWithoutATooltipNeverReadsTheInputState() {
        TooltipWidget widget = new TooltipWidget();
        widget.readingInputFails = true;
        widget.hover();
        widget.setFocused(true);
        widget.scheduleTooltip(110, 60);
        widget.setTooltip(Component.literal("tip"));
        widget.setTooltip((Component) null);
        widget.scheduleTooltip(110, 60);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void inactiveControlStillShowsItsTooltip() {
        TooltipWidget widget = new TooltipWidget();
        widget.active = false;
        widget.setTooltip(Component.literal("Why it is disabled"));
        widget.hover();
        widget.scheduleTooltip(110, 60);
        assertEquals(Component.literal("Why it is disabled"), TooltipLayer.scheduled().text());
    }

    @Test
    void controlFocusedByAClickAndHoveredReplacesAnEarlierRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.hover();
        widget.setFocused(true);
        widget.scheduleTooltip(110, 60);
        assertEquals(new TooltipLayer.Request(Component.literal("tip"), TooltipLayer.Anchor.BESIDE_CONTROL, 110, 60, VISUAL),
                TooltipLayer.scheduled());
    }
```

New imports: `_959.server_waypoint.common.client.gui.layout.Padding`, `net.minecraft.client.gui.GuiGraphicsExtractor`, `net.minecraft.client.gui.narration.NarrationElementOutput`. The test is in package `widgets`, so it can call the protected `scheduleTooltip` directly.

- [ ] **Step 2: Run it to make sure it fails**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipLayerTest'`
Expected: FAIL. `setTooltip(Component)`, `scheduleTooltip` and `isKeyboardNavigating` are missing (`cannot find symbol`, or `incompatible types: Component cannot be converted to Tooltip`).

- [ ] **Step 3: Add the API to `ShiftableClickableWidget` and delete `IconListWidget`'s dead call**

Javadoc, for now: `setTooltip` stores the control's tooltip and `null` removes it. `scheduleTooltip` is the last step of a renderer that supports tooltips. Task 5 completes `setTooltip`'s Javadoc.

- [ ] **Step 4: Run it to make sure it passes, and compile the older test sources**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipLayerTest' :mods:1.20.1-fabric:compileTestJava`
Expected: `BUILD SUCCESSFUL`, 14 tests. On 1.20.1 the test widget's `renderWidget`/`GuiGraphics` form compiles.

- [ ] **Step 5: Checkpoint**

`git status --short` lists the three files above. Commit message, if asked: `feat(mods): let controls schedule themed tooltips`.

---

### Task 5: Narration, and the classes that pass tooltips on

**Files:**
- Modify: `<gui>/widgets/ShiftableClickableWidget.java`: add `narrateTooltip` and complete `setTooltip`'s Javadoc
- Modify: `<gui>/widgets/TranslucentButton.java:158-177`, `<gui>/widgets/IconButton.java:93-138`, `<gui>/widgets/ColorSquareButton.java:39-61`
- Modify: `<gui>/widgets/AbstractDropdownMenuWidget.java:396-405`, `:460-463`, and the nested `AbstractMenuItem` at `:781-799`
- Modify: `<gui>/screens/WaypointManagerScreen.java`, `IconToggleButton` (about lines 1579-1596)
- Test: `<gui-test>/widgets/TooltipNarrationTest.java` (new)

**Interfaces:**
- Consumes: `setTooltip` and `scheduleTooltip` (Task 4).
- Produces: `protected final void narrateTooltip(NarrationElementOutput output)` on `ShiftableClickableWidget`. With a tooltip it calls `output.add(NarratedElementType.HINT, tooltip)`; without one it does nothing.

- [ ] **Step 1: Write the failing test**

`TooltipNarrationTest` starts with `//~ gui_graphics_26`. It contains:

- `TIP = Component.literal("tip")`.
- A private nested `RecordingOutput implements NarrationElementOutput`. It overrides `add(NarratedElementType, Component)` to record the type and the component as given, so no text is resolved. `add(NarratedElementType, NarrationThunk<?>)` throws an `AssertionError`, `nest()` returns `this`, and it exposes `List<NarratedElementType> types()` and `List<Component> hints()` (the HINT components, in order). Vanilla's real signature is `NarrationThunk<?>` on every target; the 26.x decompiled sources show a raw type, which is a decompiler artifact. 26.3 adds the abstract `narrationTrigger()`, so add:
  ```java
  //? if >= 26.3
  /*import net.minecraft.client.gui.narration.NarrationTrigger;*/
  ```
  ```java
          //? if >= 26.3 {
          /*@Override
          public NarrationTrigger narrationTrigger() {
              return NarrationTrigger.MOUSE;
          }
          *///?}
  ```
- `TestDropdown extends AbstractDropdownMenuWidget`, built with `super(0, 0, 16, 16, Component.literal("Dropdown"), LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD)` and an empty `renderDropdownControl`. `TestMenuItem extends AbstractDropdownMenuWidget.AbstractMenuItem`, built with `super(16, 16, Component.literal("Item"))`, with an empty `onSelected` and `renderMenuItem`.
- `allocate(Class<T>)`, copied from `MovementAllowedScreenPopupEscapeTest`.

```java
    private static List<Component> hints(Consumer<NarrationElementOutput> narration) {
        RecordingOutput output = new RecordingOutput();
        narration.accept(output);
        return output.hints();
    }

    private static void assertNarratesTheTooltipExactlyWhenSet(ShiftableClickableWidget widget,
                                                              Consumer<NarrationElementOutput> narration) {
        assertEquals(List.of(), hints(narration));
        widget.setTooltip(TIP);
        assertEquals(List.of(TIP), hints(narration));
        widget.setTooltip((Component) null);
        assertEquals(List.of(), hints(narration));
    }

    @Test
    void translucentButtonNarratesItsTooltip() {
        // The constructor reads the game's font, which unit tests don't have, so skip it. updateNarration
        // would then read vanilla's tooltip holder, which the skipped constructor leaves unset, so call
        // the button's own narration instead.
        TranslucentButton button = allocate(TranslucentButton.class);
        assertNarratesTheTooltipExactlyWhenSet(button, button::updateWidgetNarration);
    }

    @Test
    void iconButtonNarratesItsTooltip() {
        IconButton button = new IconButton(0, 0, 16, 16, Component.literal("Add"), null, () -> {
        });
        assertNarratesTheTooltipExactlyWhenSet(button, button::updateNarration);
    }

    @Test
    void colorSquareButtonNarratesItsTooltip() {
        ColorSquareButton button = new ColorSquareButton(0, 0, 10, () -> {
        });
        assertNarratesTheTooltipExactlyWhenSet(button, button::updateNarration);
    }

    @Test
    void dropdownNarratesItsTooltipAfterItsButtonText() {
        TestDropdown dropdown = new TestDropdown();
        assertNarratesTheTooltipExactlyWhenSet(dropdown, dropdown::updateNarration);
        dropdown.setTooltip(TIP);
        RecordingOutput output = new RecordingOutput();
        dropdown.updateNarration(output);
        assertEquals(List.of(NarratedElementType.TITLE, NarratedElementType.USAGE, NarratedElementType.HINT), output.types());
    }

    @Test
    void menuItemNarratesItsTooltipAfterItsButtonText() {
        TestMenuItem item = new TestMenuItem();
        assertNarratesTheTooltipExactlyWhenSet(item, item::updateNarration);
        item.setTooltip(TIP);
        RecordingOutput output = new RecordingOutput();
        item.updateNarration(output);
        assertEquals(List.of(NarratedElementType.TITLE, NarratedElementType.USAGE, NarratedElementType.HINT), output.types());
    }
```

`IconToggleButton` is private to `WaypointManagerScreen`, so review covers its two calls (§8).

- [ ] **Step 2: Run it to make sure it fails**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test --tests '*TooltipNarrationTest'`
Expected: FAIL. Each test fails its second assertion: `expected: <[tip]> but was: <[]>`.

- [ ] **Step 3: Add `narrateTooltip`, and make the two calls in each class**

| Class | Renderer: add `this.scheduleTooltip(mouseX, mouseY);` | `updateWidgetNarration`: add `this.narrateTooltip(builder);` |
| --- | --- | --- |
| `TranslucentButton` | after `drawScaledText(...)` | as its only statement |
| `IconButton` | at the end, after the icon block | as its only statement |
| `ColorSquareButton` | at the end, after the outline block | as its only statement |
| `AbstractDropdownMenuWidget` | at the end of the final renderer, after the `if (!this.renderPopupSeparately)` block | after `defaultButtonNarrationText` |
| `AbstractDropdownMenuWidget.AbstractMenuItem` | at the end of the final renderer, after `renderMenuItem(...)` | after `defaultButtonNarrationText` |
| `WaypointManagerScreen.IconToggleButton` | after `renderIconControl(...)` | as its only statement |

`RandomColorSquareButton` inherits both calls. `ComboBoxWidget` and the theme selector inherit the dropdown's; they have no tooltips, so their calls do nothing.

Complete `setTooltip`'s Javadoc. It names the classes in the table. It says a class passes the tooltip on by calling `scheduleTooltip` at the end of its renderer and `narrateTooltip` at the end of `updateWidgetNarration`, and that other classes ignore the tooltip without an error. It also says to remove a tooltip with `setTooltip((Component) null)`.

- [ ] **Step 4: Run the tests, and compile both narration branches**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test :mods:26.3-fabric:compileTestJava :mods:1.20.1-fabric:compileTestJava`
Expected: `BUILD SUCCESSFUL`. The whole 26.1.2 suite passes, including 5 `TooltipNarrationTest` tests. 26.3 compiles the `narrationTrigger()` branch.

- [ ] **Step 5: Checkpoint**

`git status --short` lists the seven files above. Commit message, if asked: `feat(mods): narrate and schedule tooltips in the controls that have them`.

---

### Task 6: Draw the layer, and move the control tooltips

**Files:**
- Modify: `<gui>/screens/MovementAllowedScreen.java:64-74`
- Modify: `<gui>/screens/RemoteWaypointPanel.java:76`, `:148`. Keep its `Tooltip` import: `BrowserTree` uses it until Task 7.
- Modify: `<gui>/screens/WaypointManagerScreen.java:35`, `:1403`, `:1409`, `:1509`, `:1576`
- Modify: `<gui>/screens/WidgetThemeConfigScreen.java:27`, `:306-307`
- Modify: `<gui>/screens/ClientConfigScreen.java:34`, `:375`
- Modify: `<gui>/widgets/SwatchWidget.java:11`, `:151`, `:160`, `:161`
- Modify: `<gui>/widgets/WaypointIconPicker.java:8`, `:54`

**Interfaces:**
- Consumes: `TooltipLayer.clear()` and `TooltipLayer.render(...)` (Task 3), and `setTooltip(Component)` (Task 4).

No unit test can drive a screen's render without the game. This task's check is the search below, which fails now and must pass afterwards.

- [ ] **Step 1: Run the search to see the sites**

Run: `grep -rn "Tooltip.create\|components.Tooltip" <gui>/screens/WaypointManagerScreen.java <gui>/screens/WidgetThemeConfigScreen.java <gui>/screens/ClientConfigScreen.java <gui>/widgets/SwatchWidget.java <gui>/widgets/WaypointIconPicker.java`
Expected: 15 lines, the imports and the 10 `setTooltip(Tooltip.create(...))` calls in these files.

- [ ] **Step 2: Draw the layer in `MovementAllowedScreen`**

Import `_959.server_waypoint.common.client.gui.widgets.TooltipLayer`. The final render method becomes:

```java
    @Override
    public final void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        TooltipLayer.clear();
        //? if < 1.21.6 {
        /*this.renderScreenBackground(context, deltaTicks);
        *///?}
        this.renderScreenContents(context, mouseX, mouseY, deltaTicks);
        TooltipLayer.render(context, this.font, this.width, this.height);
    }
```

- [ ] **Step 3: Pass each control tooltip as a `Component`**

Each `setTooltip(Tooltip.create(x))` becomes `setTooltip(x)`, with the same text (§7):

- `RemoteWaypointPanel` constructor: `setTooltip(Component.translatable("waypoint.remote.gui.teleport_hint"))`. `updateTeleportAction`: `setTooltip(Component.translatable(tooltip))`.
- `WaypointManagerScreen`: `IconDropdownMenu`'s constructor and `setMessage`, `IconMenuItem`'s constructor, and `IconToggleButton.updatePresentation`: `setTooltip(message)`.
- `WidgetThemeConfigScreen`: `setTooltip(Component.translatable("server_waypoint.theme.color_picker"))`.
- `ClientConfigScreen`: `setTooltip(resetLabel)`.
- `SwatchWidget`: `setTooltip(Component.nullToEmpty("🎲"))`, `setTooltip(Component.translatable("waypoint.edit.screen.current_color.hover"))` and `setTooltip(Component.translatable("waypoint.edit.screen.previous_color.hover"))`.
- `WaypointIconPicker`: `setTooltip(clearLabel)`.

Delete the `net.minecraft.client.gui.components.Tooltip` import from every file above except `RemoteWaypointPanel`.

- [ ] **Step 4: Run the search again, the tests and an older compile**

Run the search from Step 1. Expected: no output.
Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test :mods:1.20.1-fabric:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Checkpoint**

`git status --short` lists the seven files above. Commit message, if asked: `feat(mods): draw themed tooltips for the mod's controls`.

---

### Task 7: Move the pointer tooltips

**Files:**
- Modify: `<gui>/widgets/IconListWidget.java:16`, `:169-179`
- Modify: `<gui>/screens/RemoteWaypointPanel.java:15`, `:261`, `:286-303`
- Modify: `<gui>/widgets/SettingsListWidget.java:20`, `:26`, `:37`, `:310`, `:317-338`
- Modify: `<gui>/screens/AbstractWaypointPropertiesScreen.java:49`, `:75`, `:1187`, `:1224-1238`
- Modify: `<gui>/render/DrawContextHelper.java:7`, `:209-223`
- Modify: `<gui>/widgets/ServerListWidget.java:61`

**Interfaces:**
- Consumes: `TooltipLayer.scheduleAtPointer(Component, int, int)` (Task 3).
- Removes: `DrawContextHelper.scheduleTooltipAtPointer`.

- [ ] **Step 1: Run the search to see the sites**

Run: `grep -rn "components\.Tooltip\|Tooltip\.create\|setTooltipForNextFrame\|setTooltipForNextRenderPass\|scheduleTooltipAtPointer" mods/src/main/java`
Expected: hits in the first five files above, and nowhere else. `ServerListWidget`'s comment doesn't match.

- [ ] **Step 2: Schedule each pointer tooltip with `TooltipLayer`**

Each owner keeps deciding when its tooltip shows (§3). Only the call changes, and the screen-space `mouseX`/`mouseY` pass through unchanged.

- `IconListWidget`: the hover block becomes:
  ```java
          if (hoverIndex >= 0) {
              // Anchor the entry label to the cursor, not the entire scrollable icon rail.
              TooltipLayer.scheduleAtPointer(entryLabel(entries.get(hoverIndex)), mouseX, mouseY);
              this.renderIconBackground(context, hoverIndex, viewport, getColor(ROW_HOVER_BACKGROUND), false);
          }
  ```
  Delete the `Tooltip` import. Keep the `Minecraft` import, which line 135 uses.
- `RemoteWaypointPanel.BrowserTree`: rename `renderHoveredTooltip(GuiGraphicsExtractor, int, int)` to `private void scheduleHoveredTooltip(int mouseX, int mouseY)`. Keep how it builds `identity`, then call `TooltipLayer.scheduleAtPointer(identity, mouseX, mouseY)`. Drop the `client` variable and both version branches. `render` calls `tree.scheduleHoveredTooltip(mouseX, mouseY)`. Delete the `Tooltip` import; the `widgets.*` import already covers `TooltipLayer`.
- `SettingsListWidget`: rename the private `scheduleTooltip(GuiGraphicsExtractor, Row, int, int)` to `private void scheduleRowTooltip(@Nullable Row hovered, int mouseX, int mouseY)`, so it no longer overloads the inherited `scheduleTooltip(int, int)`. Its call at line 310 becomes `this.scheduleRowTooltip(hovered, mouseX, mouseY)`. Its last lines become `TooltipLayer.scheduleAtPointer(text, mouseX, mouseY)`, keeping the comment above them. Delete the `Minecraft` and `Tooltip` imports and the static import of `scheduleTooltipAtPointer`.
- `AbstractWaypointPropertiesScreen`: rename `renderFieldTooltip(GuiGraphicsExtractor, int, int)` to `private void scheduleFieldTooltip(int mouseX, int mouseY)` and keep its Javadoc. Its call becomes `TooltipLayer.scheduleAtPointer(this.tooltipText(field), mouseX, mouseY)`, and the caller passes `(contentMouseX, contentMouseY)`. Delete the `Tooltip` import and the static import of `scheduleTooltipAtPointer`. Keep `Minecraft`, which lines 959 and 973 use. Import `TooltipLayer` unless the file already imports `widgets.*`.
- `DrawContextHelper`: delete `scheduleTooltipAtPointer` with its Javadoc, and `import java.util.List;`, which only that method used. `FormattedCharSequence` stays; the `drawText` overloads use it.
- `ServerListWidget:61`: the comment becomes `// Tooltip wrapping treats the newline as a line break.`

- [ ] **Step 3: Run the search again, the tests, and both sides of 1.21.6**

Run the search from Step 1. Expected: no output.
Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:test :mods:1.20.1-fabric:compileJava :mods:1.21.6-fabric:compileJava`
Expected: `BUILD SUCCESSFUL`. The removed inline branches covered both sides of 1.21.6.
Check the markers in `IconListWidget.java`, `RemoteWaypointPanel.java` and `DrawContextHelper.java` with the Task 3 `grep`. No orphan `//?}` or `*///?}` remains where the branches were.

- [ ] **Step 4: Checkpoint**

`git status --short` lists the six files above. Commit message, if asked: `feat(mods): draw pointer tooltips through the tooltip layer`.

---

### Task 8: Documentation

**Files:**
- Modify: `docs/tips/gui/local-guide.md`
- Modify: `CHANGELOG.md:82-86`

Line numbers are from before this change.

- [ ] **Step 1: Add the "Tooltips" section to the guide**

Add `### Tooltips` under "## `widgets`: choosing and extending components", after "### Color pickers" (about line 951) and before "### New interactive widget checklist". It covers the items §10 lists, each in a sentence or two:

- The look, from §1.
- `setTooltip(Component)`, the two calls and the classes that make them, from §2's table. Use `setTooltip((Component) null)` to remove a tooltip. A class without the calls ignores the tooltip; today that includes toggles, sliders, text fields and the swatch widget, so add the calls to the class before giving one a tooltip (Known limits).
- `TooltipLayer.scheduleAtPointer` takes screen-space coordinates, and its owner decides when to call it.
- One tooltip per frame (§3).
- Placement: vanilla's three positioners; the request anchors to the visual bounds; the one change from vanilla is the top clamp. Describe the rule for tooltips beside a control as "below the control, lower the further down the control the pointer is, or above it near the screen's bottom". Don't repeat §4's "at most 3 pixels" and "at most 5 pixels" figures; see the spec problem reported with this plan.
- Layering: `MovementAllowedScreen` draws the layer last, through `nextTooltipLayer`/`previousTooltipLayer`.
- Narration: `narrateTooltip` adds the tooltip as a hint. Pointer tooltips aren't narrated.
- Never use vanilla's `Tooltip`, `setTooltipForNextFrame` or `setTooltipForNextRenderPass` in the mod's screens. Vanilla's `setTooltip(Tooltip)` still exists on every widget and would bring back the vanilla box.

- [ ] **Step 2: Update the rest of the guide**

- Package table (lines 30 and 33): add `TooltipPlacement` to `client.gui.layout`, and tooltips (`TranslucentTooltip`, `TooltipLayer`) to `client.gui.widgets`.
- Component table (lines 589-608): add a row whose need is "Tooltip for a control or a hovered item" and whose start is "`setTooltip(Component)`, or `TooltipLayer.scheduleAtPointer`".
- `DrawContextHelper` notes in "#### 3. Render: use one rendering owner" (lines 1062-1075): add "5. The tooltip, drawn last by `MovementAllowedScreen`" to the order list. Beside the `nextItemOverlayLayer` sentence, say `nextTooltipLayer` translates 400 before 1.21.6, vanilla's tooltip depth, and starts a stratum from 1.21.6.
- New interactive widget checklist (lines 953-964): add an item: "If it can show a tooltip, end its renderer with `scheduleTooltip(mouseX, mouseY)` and `updateWidgetNarration` with `narrateTooltip(output)`; without both calls, `setTooltip` does nothing."
- Review checklist (lines 1456-1478): add "Tooltips use `setTooltip(Component)` on a class that makes both calls, or `TooltipLayer.scheduleAtPointer`; nothing imports vanilla's `Tooltip`."
- "Tooltip position for scrollable widgets" (lines 1369-1387): `setTooltip(Component)` describes a whole control, and `TooltipLayer` places it beside the control's visual bounds. Hovered items pass screen-space coordinates to `TooltipLayer.scheduleAtPointer`, which needs no version branch. The last bullet names all four owners (`IconListWidget`, `RemoteWaypointPanel.BrowserTree`, `SettingsListWidget`, the waypoint form) and no longer mentions inline branches or clearing a whole-widget tooltip.
- Sentences that still call a tooltip vanilla's:
  - Line 919: "which keeps its own vanilla tooltip" becomes "which shows its own tooltip".
  - Line 1288: "has vanilla's "Remove icon" tooltip" becomes "has a "Remove icon" tooltip".
  - Lines 832-833: "use the current hovered entry and vanilla cursor positioning after the panel render pass" becomes "are scheduled at the pointer with `TooltipLayer.scheduleAtPointer` for the hovered entry after the panel render pass".
  - Line 1036: "through `DrawContextHelper.scheduleTooltipAtPointer`" becomes "through `TooltipLayer.scheduleAtPointer`".

- [ ] **Step 3: Add the CHANGELOG line**

Under `### Client settings and themes` in `## Server Waypoint 4.0.0`, after the "Colour themes" bullet, add:

```markdown
- Tooltips in the mod's screens follow the colour theme.
```

- [ ] **Step 4: Check for leftovers**

Run: `grep -n "scheduleTooltipAtPointer\|keeps its own vanilla tooltip\|vanilla's \"Remove icon\"\|vanilla cursor positioning\|still carry the" docs/tips/gui/local-guide.md`
Expected: no output.
Run: `git diff --check`
Expected: no output.

- [ ] **Step 5: Checkpoint**

`git status --short` adds the two files above. Commit message, if asked: `docs: document themed tooltips`.

---

### Task 9: Build verification

**Files:**
- Create: `docs/features/gui-tooltips/validation/<YYYY-MM-DD>-themed-tooltips.md`, using the date you run this
- Modify: `docs/features/gui-tooltips/README.md`: add `- [Validation record](validation/<YYYY-MM-DD>-themed-tooltips.md)`

- [ ] **Step 1: Check the tree**

Run: `git branch --show-current` and `git status --short`.
Expected: `4.0.0`, and only the files this plan's tasks change. Name any other file in the report, and don't touch it.

- [ ] **Step 2: Whitespace**

Run: `git diff --check`
Expected: no output.

- [ ] **Step 3: Tests on the active target and on Java 17 before 1.21.6**

Run: `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test :mods:1.20.1-fabric:cleanTest :mods:1.20.1-fabric:test`
Expected: `BUILD SUCCESSFUL`. Read the test, failure and skip counts from `mods/versions/<version>/build/test-results/test/*.xml` for the report.

- [ ] **Step 4: Compile every mods target**

`settings.gradle.kts` lists 40 targets: 14 Fabric, 14 NeoForge (including 1.21.7) and 12 Forge. Write `compile-all.zsh` in your scratchpad:

```zsh
#!/bin/zsh
# compileJava and compileTestJava for every mods target.
tasks=()
for dir in /Volumes/ssd/fabric_mods_repo/server_waypoint/mods/versions/*(/); do
    tasks+=(":mods:${dir:t}:compileJava" ":mods:${dir:t}:compileTestJava")
done
print -r -- "targets: $(( ${#tasks} / 2 ))"
zsh <scratchpad>/gw.zsh "${tasks[@]}" --continue
```

Run: `zsh <scratchpad>/compile-all.zsh`
Expected: `targets: 40` and `BUILD SUCCESSFUL`. If the count isn't 40, compare `mods/versions` with `settings.gradle.kts` before going on. It took about 1 to 4 minutes warm in earlier sessions.

- [ ] **Step 5: No vanilla tooltip left**

Run: `grep -rn "components\.Tooltip\|setTooltipForNextFrame\|setTooltipForNextRenderPass" mods/src/main/java`
Expected: no output.

- [ ] **Step 6: Write the report and link it**

The report lists each command above with its result and the test counts. Its "In-game checks" heading says "pending Task 10". Add the link to the README.

- [ ] **Step 7: Checkpoint**

Commit message, if asked: `docs: record themed tooltip build checks`.

---

### Task 10: In-game check

Compiling can't confirm placement or layering (§9). This task runs the two dev clients, `26.1.2-fabric` for the stratum path and `1.20.1-fabric` for the z-translation path, and takes a screenshot per item.

**Files:**
- Create: `docs/features/gui-tooltips/validation/screenshots/<version>/<nn>-<item>.png`
- Modify: the Task 9 report

- [ ] **Step 1: Ask for approval**

Ask the user in chat whether you may drive the dev clients with computer use. Wait for a clear yes. If they decline, write "not run (no approval)" under "In-game checks" and stop.

- [ ] **Step 2: Start the 26.1.2 client**

Run `zsh <scratchpad>/gw.zsh :mods:26.1.2-fabric:runClient` in the background. Request computer-use access for the game window's app (find its name with `list_apps`). Open singleplayer, then the world "New World" from `mods/versions/26.1.2-fabric/run/saves`.

- [ ] **Step 3: Check each item and take a screenshot**

Right Shift opens the manager. In the manager, C opens Client settings while no text field has focus, and the Appearance section's "Configure" button there opens the theme editor. The manager's + button opens the add form. Press F2 for each screenshot; vanilla saves it to `mods/versions/<version>/run/screenshots/`.

1. Manager: a dimension rail entry; the sort dropdown and one of its open choices; the scope, grouping and sort-order toggles; Tab to a toggle, so its tooltip shows below or above it.
2. Client settings: a row's tooltip after 500 ms; a reset button's; a tooltip near the right edge and one near the bottom edge, each flipped.
3. Add form: a field's tooltip after 500 ms; the remove-icon button's. With the swatch open: its buttons' tooltips show above the swatch and the item preview, and no field tooltip shows.
4. Theme editor: the color button's tooltip. Switch to High Contrast, then Classic; the fill and outline change at once.

For each item, note what you saw: the box's colors against §1's table, its position against §4, and whether it was on top.

- [ ] **Step 4: Repeat on 1.20.1**

Quit the client normally. Run `:mods:1.20.1-fabric:runClient` and repeat Step 3 with the 1.20.1 "New World".

- [ ] **Step 5: Store the screenshots and finish the report**

Copy each screenshot to `docs/features/gui-tooltips/validation/screenshots/<version>/<nn>-<item>.png` and shrink it with `sips -Z 1600 <file>`. Under "In-game checks", list each item per version with its screenshot link and what you saw. Note that the remote teleport button, the server rail and the remote tree weren't checked: they need a cross-server catalog, and unit tests and review cover them unless a Velocity setup is available (§9).

- [ ] **Step 6: Checkpoint**

`git status --short` adds the screenshots and the report. Commit message, if asked: `docs: record themed tooltip in-game checks`.
