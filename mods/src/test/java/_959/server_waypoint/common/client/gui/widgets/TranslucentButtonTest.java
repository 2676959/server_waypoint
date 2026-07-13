package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslucentButtonTest {
    @Test
    void outlineAnchorPositionsButtonInsideOutlinePadding() {
        assertEquals(11, AnchorMode.OUTLINE.getContentX(10, TranslucentButton.OUTLINE_LEFT_PADDING));
        assertEquals(22, AnchorMode.OUTLINE.getContentY(20, TranslucentButton.OUTLINE_TOP_PADDING));
    }

    @Test
    void contentAnchorKeepsExistingButtonPosition() {
        assertEquals(10, AnchorMode.CONTENT.getContentX(10, TranslucentButton.OUTLINE_LEFT_PADDING));
        assertEquals(20, AnchorMode.CONTENT.getContentY(20, TranslucentButton.OUTLINE_TOP_PADDING));
    }
}
