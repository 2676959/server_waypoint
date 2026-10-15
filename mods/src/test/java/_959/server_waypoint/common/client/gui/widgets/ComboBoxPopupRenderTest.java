//~ gui_graphics_26
//? if >=26 {
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboBoxPopupRenderTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @ParameterizedTest
    @EnumSource(LayoutFlow.Direction.class)
    void hoveredRowDrawsAllFourEdgesBesideTheScrollbar(LayoutFlow.Direction direction) throws Exception {
        ComboBoxWidget combo = combo(direction);
        RecordingGraphics graphics = RecordingGraphics.create();
        var row = combo.getMenuItems().get(0);
        int x = row.getX();
        int y = row.getY();

        combo.renderPopup(graphics, x + 10, y + 4, 0);

        assertEquals(0xFFFFFFFF, graphics.colorAt(x + 10, y), "upper edge");
        assertEquals(0xFFFFFFFF, graphics.colorAt(x + 10, y + 10), "lower edge");
        assertEquals(0xFFFFFFFF, graphics.colorAt(x, y + 4), "left edge");
        assertEquals(0xFFFFFFFF, graphics.colorAt(x + 55, y + 4), "right edge before scrollbar");
        assertTrue(graphics.fills.stream().filter(fill -> fill.color == 0xFFFFFFFF)
                .allMatch(fill -> fill.right <= x + 56), "highlight must not extend beneath the scrollbar");
        assertTrue(graphics.fills.stream().anyMatch(fill -> fill.color == 0x80606060),
                "hover fill must retain the theme opacity");
    }

    @Test
    void hoveringTheScrollbarDoesNotHighlightTheRowUnderIt() throws Exception {
        ComboBoxWidget combo = combo(LayoutFlow.Direction.FORWARD);
        RecordingGraphics graphics = RecordingGraphics.create();

        combo.renderPopup(graphics, 65, 33, 0);

        assertFalse(combo.getMenuItems().get(0).isHovered());
        assertFalse(graphics.fills.stream().anyMatch(fill -> fill.color == 0xFFFFFFFF));
    }

    @Test
    void keyboardHighlightedRowAlsoHasACompleteOutline() throws Exception {
        ComboBoxWidget combo = combo(LayoutFlow.Direction.FORWARD);
        var row = combo.getMenuItems().get(0);
        row.setFocused(true);
        RecordingGraphics graphics = RecordingGraphics.create();

        combo.renderPopup(graphics, -100, -100, 0);

        assertEquals(0xFFFFFFFF, graphics.colorAt(18, 29));
        assertEquals(0xFFFFFFFF, graphics.colorAt(63, 33));
    }

    @Test
    void aPopupThatFitsHighlightsItsFullWidth() throws Exception {
        ComboBoxWidget combo = combo(LayoutFlow.Direction.FORWARD);
        combo.setMaxPopupHeight(100);
        RecordingGraphics graphics = RecordingGraphics.create();

        combo.renderPopup(graphics, 65, 33, 0);

        assertTrue(combo.getMenuItems().get(0).isHovered());
        assertEquals(0xFFFFFFFF, graphics.colorAt(67, 33));
        assertEquals(0xFFFFFFFF, graphics.colorAt(18, 29));
    }

    private static ComboBoxWidget combo(LayoutFlow.Direction direction) {
        WidgetThemeManager.setColor(WidgetThemeVariable.FOCUS_RING, 0xFFFFFFFF);
        WidgetThemeManager.setColor(WidgetThemeVariable.BORDER, 0xFF112233);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0x80606060);
        ComboBoxWidget combo = new ComboBoxWidget(10, 20, 60, Component.empty(), new TestFont(),
                List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"), "", value -> { });
        combo.setMaxPopupHeight(22);
        combo.setExpansionDirection(direction);
        combo.setExpanded(true);
        return combo;
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
