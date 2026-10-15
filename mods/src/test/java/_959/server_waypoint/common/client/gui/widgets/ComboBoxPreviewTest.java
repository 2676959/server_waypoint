package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboBoxPreviewTest {
    @ParameterizedTest
    @EnumSource(LayoutFlow.Direction.class)
    void hoveredChoiceIsAvailableBeforeRenderingInEitherDirection(LayoutFlow.Direction direction) {
        ComboBoxWidget combo = combo();
        combo.setExpansionDirection(direction);
        combo.setExpanded(true);
        var first = combo.getMenuItems().get(0);

        assertEquals("s1", combo.getHoveredValue(first.getX() + 10, first.getY() + 4));
        assertEquals("", combo.getValue());
        assertNull(combo.getHoveredValue(combo.getX() + 10, combo.getY() + 4));
        assertNull(combo.getHoveredValue(first.getX() + 57, first.getY() + 4));
    }

    @Test
    void previewTracksScrollingAndSkipsHiddenRows() {
        ComboBoxWidget combo = combo();
        combo.setExpanded(true);

        assertTrue(combo.mouseScrolled(20, 33, 0, -1.0));
        assertEquals("s2", combo.getHoveredValue(20, 33));
        assertNull(combo.getHoveredValue(20, 53));
        combo.closeMenuIfOpen();
        assertNull(combo.getHoveredValue(20, 33));
    }

    @Test
    void inactiveAndHiddenControlsHaveNoPreviewChoice() {
        ComboBoxWidget combo = combo();
        combo.setExpanded(true);
        combo.active = false;
        assertNull(combo.getHoveredValue(20, 33));
        combo.active = true;
        combo.visible = false;
        assertNull(combo.getHoveredValue(20, 33));
    }

    private static ComboBoxWidget combo() {
        ComboBoxWidget combo = new ComboBoxWidget(10, 20, 60, Component.empty(), new TestFont(),
                List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"), "", value -> { });
        combo.setMaxPopupHeight(22);
        return combo;
    }
}
