package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnchorModeTest {
    @Test
    void outlineModeConvertsBackFromContentPositionToAnchorPosition() {
        assertEquals(10, AnchorMode.OUTLINE.getAnchorX(12, 2));
        assertEquals(20, AnchorMode.OUTLINE.getAnchorY(22, 2));
    }

    @Test
    void contentModeUsesSamePositionForContentAndAnchor() {
        assertEquals(10, AnchorMode.CONTENT.getAnchorX(10, 2));
        assertEquals(20, AnchorMode.CONTENT.getAnchorY(20, 2));
    }
}
