package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslucentTextFieldTest {
    @Test
    void outlineAnchorPositionsTextInsideOutlinePadding() {
        assertEquals(12, AnchorMode.OUTLINE.getContentX(10, TranslucentTextField.OUTLINE_PADDING));
        assertEquals(22, AnchorMode.OUTLINE.getContentY(20, TranslucentTextField.OUTLINE_PADDING));
    }

    @Test
    void textAreaAnchorKeepsExistingTextPosition() {
        assertEquals(10, AnchorMode.CONTENT.getContentX(10, TranslucentTextField.OUTLINE_PADDING));
        assertEquals(20, AnchorMode.CONTENT.getContentY(20, TranslucentTextField.OUTLINE_PADDING));
    }
}
