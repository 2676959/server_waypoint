package _959.server_waypoint.common.client.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import _959.server_waypoint.common.client.gui.TestFont;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointDetailsWidgetInputTest {
    @Test
    void overflowingPanelIgnoresContentClicksWithoutAccessingClientSound() {
        WaypointDetailsWidget panel = new WaypointDetailsWidget(10, 10, 200, 20, new TestFont());

        assertTrue(panel.overflows());
        assertFalse(assertDoesNotThrow(() -> panel.mouseClicked(20, 15, InputConstants.MOUSE_BUTTON_LEFT)));
        assertFalse(panel.mouseDragged(20, 18, InputConstants.MOUSE_BUTTON_LEFT, 0, 3));
        assertEquals(0.0, panel.getScrollY());
    }

    @Test
    void scrollbarDraggingAndWheelScrollingRemainAvailable() {
        WaypointDetailsWidget panel = new WaypointDetailsWidget(10, 10, 200, 20, new TestFont());

        assertTrue(panel.mouseClicked(208, 15, InputConstants.MOUSE_BUTTON_LEFT));
        assertTrue(panel.mouseDragged(208, 40, InputConstants.MOUSE_BUTTON_LEFT, 0, 25));
        assertEquals(panel.getMaxScroll(), panel.getScrollY());
        panel.mouseReleased(208, 40, InputConstants.MOUSE_BUTTON_LEFT);
        assertFalse(panel.mouseDragged(208, 15, InputConstants.MOUSE_BUTTON_LEFT, 0, -25));
        assertTrue(panel.mouseScrolled(20, 15, 0, 1));
        assertEquals(Math.max(0.0, panel.getMaxScroll() - panel.getDeltaYPerScroll()), panel.getScrollY());
    }
}
