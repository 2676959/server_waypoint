//~ gui_graphics_26
//? if >=26 {
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Text choice triggers use the combobox's control colors, and their choices use popup colors. */
class TextChoiceDropdownSurfaceTest {
    private static final int POPUP = 0xFF010101;
    private static final int WASH = 0xFF020202;
    private static final int CONTROL = 0xFF030303;
    private static final int CONTROL_HOVER = 0x80040404;
    private static final int CONTROL_DISABLED = 0xFF050505;
    private static final int FOCUS_RING = 0xFF060606;
    private static final int BORDER = 0xFF070707;
    /** The trigger sits at (10, 20), 60 wide and 11 high; this point is inside its outline, left of its label. */
    private static final int INSIDE_X = 12;
    private static final int INSIDE_Y = 25;
    /** A pixel of the outline's left edge. */
    private static final int EDGE_X = 10;
    private static final int AWAY = -100;

    @BeforeEach
    void giveEverySurfaceItsOwnColor() {
        WidgetThemeManager.setColor(WidgetThemeVariable.POPUP_BACKGROUND, POPUP);
        WidgetThemeManager.setColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND, WASH);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_BACKGROUND, CONTROL);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, CONTROL_HOVER);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, CONTROL_DISABLED);
        WidgetThemeManager.setColor(WidgetThemeVariable.FOCUS_RING, FOCUS_RING);
        WidgetThemeManager.setColor(WidgetThemeVariable.BORDER, BORDER);
    }

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void theTriggerTakesTheControlBackgroundAtRest() throws Exception {
        assertEquals(CONTROL, render(new Dropdown(), AWAY, AWAY).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void theTriggerTakesTheControlHoverFillWhileHovered() throws Exception {
        assertEquals(CONTROL_HOVER, render(new Dropdown(), INSIDE_X, INSIDE_Y).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void aDisabledTriggerTakesTheDisabledFill() throws Exception {
        Dropdown dropdown = new Dropdown();
        dropdown.active = false;

        assertEquals(CONTROL_DISABLED, render(dropdown, AWAY, AWAY).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void aFocusedTriggerTakesTheControlHoverFillAndShowsTheFocusRing() throws Exception {
        Dropdown dropdown = new Dropdown();
        dropdown.setFocused(true);

        RecordingGraphics graphics = render(dropdown, AWAY, AWAY);

        assertEquals(CONTROL_HOVER, graphics.colorAt(INSIDE_X, INSIDE_Y), "focus highlights the fill");
        assertEquals(FOCUS_RING, graphics.colorAt(EDGE_X, INSIDE_Y), "the outline shows focus");
    }

    @Test
    void theTriggerLightsItsOutlineWhileHovered() throws Exception {
        assertEquals(FOCUS_RING, render(new Dropdown(), INSIDE_X, INSIDE_Y).colorAt(EDGE_X, INSIDE_Y));
    }

    @Test
    void theTriggerKeepsItsRestingOutlineOtherwise() throws Exception {
        assertEquals(BORDER, render(new Dropdown(), AWAY, AWAY).colorAt(EDGE_X, INSIDE_Y));
    }

    @Test
    void aDisabledTriggerDoesNotLightItsOutlineEvenWhenHoveredAndFocused() throws Exception {
        Dropdown dropdown = new Dropdown();
        dropdown.active = false;
        dropdown.setFocused(true);

        assertEquals(BORDER, render(dropdown, INSIDE_X, INSIDE_Y).colorAt(EDGE_X, INSIDE_Y));
    }

    @Test
    void dropdownArrowKeepsItsRightPaddingAtBothTextScales() throws Exception {
        assertArrowRightPadding(new Dropdown());
        assertArrowRightPadding(new Dropdown(0.85F));
    }

    @Test
    void comboBoxArrowUsesTheSameRightPadding() throws Exception {
        ComboBoxWidget combo = new ComboBoxWidget(10, 20, 60, Component.empty(), new TestFont(),
                List.of("One"), "", value -> { });
        assertArrowRightPadding(combo);
    }

    private static void assertArrowRightPadding(AbstractDropdownMenuWidget dropdown) throws Exception {
        dropdown.setRenderPopupSeparately(true);
        assertEquals(dropdown.getX() + dropdown.getWidth() - 4,
                render(dropdown, AWAY, AWAY).textRightEdges.get("⏷"), 0.5F);
        dropdown.setWidth(80);
        dropdown.setExpanded(true);
        assertEquals(dropdown.getX() + dropdown.getWidth() - 4,
                render(dropdown, AWAY, AWAY).textRightEdges.get("⏶"), 0.5F);
    }

    @Test
    void theChoicesKeepThePopupFill() throws Exception {
        Dropdown dropdown = new Dropdown();
        dropdown.setExpanded(true);
        RecordingGraphics graphics = RecordingGraphics.create();

        dropdown.renderPopup(graphics, AWAY, AWAY, 0);

        // The first choice is the row under the trigger, which ends at y 31; it must stay opaque over the rows beneath.
        assertEquals(POPUP, graphics.colorAt(INSIDE_X, 36));
    }

    @Test
    void aHoveredChoiceDrawsTheControlHoverFillAndCompleteFocusRing() throws Exception {
        Dropdown dropdown = new Dropdown();
        dropdown.setExpanded(true);
        int rowY = dropdown.getMenuItems().get(0).getY();
        RecordingGraphics graphics = RecordingGraphics.create();

        dropdown.renderPopup(graphics, INSIDE_X, rowY + 5, 0);

        assertEquals(List.of(CONTROL_HOVER), graphics.colorsAt(INSIDE_X, rowY + 5));
        assertEquals(FOCUS_RING, graphics.colorAt(INSIDE_X, rowY));
        assertEquals(FOCUS_RING, graphics.colorAt(INSIDE_X, rowY + dropdown.getHeight() - 1));
    }

    @Test
    void aKeyboardHighlightedChoiceTakesTheControlHoverFill() throws Exception {
        Dropdown dropdown = new Dropdown();
        dropdown.setExpanded(true);
        var row = dropdown.getMenuItems().get(0);
        row.setFocused(true);
        RecordingGraphics graphics = RecordingGraphics.create();

        dropdown.renderPopup(graphics, AWAY, AWAY, 0);

        assertEquals(List.of(CONTROL_HOVER), graphics.colorsAt(INSIDE_X, row.getY() + 5));
        assertEquals(FOCUS_RING, graphics.colorAt(INSIDE_X, row.getY()));
    }

    private static RecordingGraphics render(AbstractDropdownMenuWidget dropdown, int mouseX, int mouseY) throws Exception {
        RecordingGraphics graphics = RecordingGraphics.create();
        dropdown.extractRenderState(graphics, mouseX, mouseY, 0);
        return graphics;
    }

    private static final class Dropdown extends TextChoiceDropdown {
        private Dropdown() {
            this(1.0F);
        }

        private Dropdown(float textScale) {
            super(60, Component.literal("Choices"), new TestFont(), textScale);
            this.setPosition(10, 20);
            this.addMenuItem(new TextChoice(Component.literal("One"), () -> { }));
        }

        @Override
        protected Component triggerLabel() {
            return Component.literal("Choices");
        }
    }

    private record Fill(int left, int top, int right, int bottom, int color) {
    }

    /** Records actual rectangle drawing without needing the Minecraft atlas or a GPU. */
    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<Fill> fills;
        private Map<String, Float> textRightEdges;
        private Matrix3x2fStack matrices;

        private RecordingGraphics() {
            super(null, null, 0, 0);
        }

        private static RecordingGraphics create() throws Exception {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            RecordingGraphics graphics = (RecordingGraphics) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(field.get(null), RecordingGraphics.class);
            graphics.fills = new ArrayList<>();
            graphics.textRightEdges = new HashMap<>();
            graphics.matrices = new Matrix3x2fStack(16);
            return graphics;
        }

        /** The color painted last at a pixel, or 0 when nothing covers it. */
        private int colorAt(int x, int y) {
            List<Integer> colors = this.colorsAt(x, y);
            return colors.isEmpty() ? 0 : colors.get(colors.size() - 1);
        }

        private List<Integer> colorsAt(int x, int y) {
            return this.fills.stream()
                    .filter(fill -> x >= fill.left && x < fill.right && y >= fill.top && y < fill.bottom)
                    .map(Fill::color)
                    .toList();
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            this.fills.add(new Fill(left, top, right, bottom, color));
        }

        @Override
        public Matrix3x2fStack pose() {
            return this.matrices;
        }

        @Override
        public boolean containsPointInScissor(int x, int y) {
            return true;
        }

        @Override
        public void nextStratum() {
        }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) {
        }

        @Override
        public void disableScissor() {
        }

        @Override
        public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
            this.textRightEdges.put(text.getString(), this.matrices.m20() + (x + font.width(text)) * this.matrices.m00());
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        }
    }
}
//?}
