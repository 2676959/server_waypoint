//~ gui_graphics_26
//? if >=26 {
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A dropdown among text fields, as the waypoint form's Collection row is, looks like them: the theme's control
 * background with its hover and disabled fills, and an outline that takes the focus ring while the dropdown is
 * hovered or focused and active. A dropdown that doesn't ask for this keeps the popup's fill and its hover
 * wash, and its outline lights only while it is focused.
 */
class TextChoiceDropdownSurfaceTest {
    private static final int POPUP = 0xFF010101;
    private static final int WASH = 0xFF020202;
    private static final int CONTROL = 0xFF030303;
    private static final int CONTROL_HOVER = 0xFF040404;
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
    void aFieldTriggerIsFilledWithTheControlBackgroundAtRest() throws Exception {
        assertEquals(CONTROL, render(new Dropdown(true), AWAY, AWAY).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void aFieldTriggerTakesTheControlHoverFillWhileHovered() throws Exception {
        assertEquals(CONTROL_HOVER, render(new Dropdown(true), INSIDE_X, INSIDE_Y).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void aDisabledFieldTriggerTakesTheDisabledFill() throws Exception {
        Dropdown dropdown = new Dropdown(true);
        dropdown.active = false;

        assertEquals(CONTROL_DISABLED, render(dropdown, AWAY, AWAY).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void aFocusedFieldTriggerKeepsItsFillAndShowsTheFocusRing() throws Exception {
        Dropdown dropdown = new Dropdown(true);
        dropdown.setFocused(true);

        RecordingGraphics graphics = render(dropdown, AWAY, AWAY);

        assertEquals(CONTROL, graphics.colorAt(INSIDE_X, INSIDE_Y), "focus does not wash the fill");
        assertEquals(FOCUS_RING, graphics.colorAt(EDGE_X, INSIDE_Y), "the outline shows focus");
    }

    @Test
    void aFieldTriggerLightsItsOutlineWhileHovered() throws Exception {
        assertEquals(FOCUS_RING, render(new Dropdown(true), INSIDE_X, INSIDE_Y).colorAt(EDGE_X, INSIDE_Y));
    }

    @Test
    void aFieldTriggerKeepsItsRestingOutlineOtherwise() throws Exception {
        assertEquals(BORDER, render(new Dropdown(true), AWAY, AWAY).colorAt(EDGE_X, INSIDE_Y));
    }

    @Test
    void aDisabledFieldTriggerDoesNotLightItsOutlineEvenWhenHoveredAndFocused() throws Exception {
        Dropdown dropdown = new Dropdown(true);
        dropdown.active = false;
        dropdown.setFocused(true);

        assertEquals(BORDER, render(dropdown, INSIDE_X, INSIDE_Y).colorAt(EDGE_X, INSIDE_Y));
    }

    @Test
    void theChoicesOfAFieldTriggerKeepThePopupFill() throws Exception {
        Dropdown dropdown = new Dropdown(true);
        dropdown.setExpanded(true);
        RecordingGraphics graphics = RecordingGraphics.create();

        dropdown.renderPopup(graphics, AWAY, AWAY, 0);

        // The first choice is the row under the trigger, which ends at y 31; it must stay opaque over the rows beneath.
        assertEquals(POPUP, graphics.colorAt(INSIDE_X, 36));
    }

    @Test
    void aDefaultTriggerKeepsThePopupFillAndItsHoverWash() throws Exception {
        assertEquals(POPUP, render(new Dropdown(false), AWAY, AWAY).colorAt(INSIDE_X, INSIDE_Y));
        assertEquals(WASH, render(new Dropdown(false), INSIDE_X, INSIDE_Y).colorAt(INSIDE_X, INSIDE_Y));
    }

    @Test
    void aDefaultTriggerLightsItsOutlineOnlyWhileFocused() throws Exception {
        assertEquals(BORDER, render(new Dropdown(false), INSIDE_X, INSIDE_Y).colorAt(EDGE_X, INSIDE_Y), "hovered");
        Dropdown focused = new Dropdown(false);
        focused.setFocused(true);

        assertEquals(FOCUS_RING, render(focused, AWAY, AWAY).colorAt(EDGE_X, INSIDE_Y), "focused");
    }

    private static RecordingGraphics render(Dropdown dropdown, int mouseX, int mouseY) throws Exception {
        RecordingGraphics graphics = RecordingGraphics.create();
        dropdown.extractRenderState(graphics, mouseX, mouseY, 0);
        return graphics;
    }

    private static final class Dropdown extends TextChoiceDropdown {
        private final boolean fieldSurface;

        private Dropdown(boolean fieldSurface) {
            super(60, Component.literal("Choices"), new TestFont());
            this.fieldSurface = fieldSurface;
            this.setPosition(10, 20);
            this.addMenuItem(new TextChoice(Component.literal("One"), () -> { }));
        }

        @Override
        protected Component triggerLabel() {
            return Component.literal("Choices");
        }

        @Override
        protected boolean usesFieldSurface() {
            return this.fieldSurface;
        }
    }

    private record Fill(int left, int top, int right, int bottom, int color) {
    }

    /** Records actual rectangle drawing without needing the Minecraft atlas or a GPU. */
    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<Fill> fills;
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
            graphics.matrices = new Matrix3x2fStack(16);
            return graphics;
        }

        /** The color painted last at a pixel, or 0 when nothing covers it. */
        private int colorAt(int x, int y) {
            int color = 0;
            for (Fill fill : this.fills) {
                if (x >= fill.left && x < fill.right && y >= fill.top && y < fill.bottom) {
                    color = fill.color;
                }
            }
            return color;
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
        }
    }
}
//?}
