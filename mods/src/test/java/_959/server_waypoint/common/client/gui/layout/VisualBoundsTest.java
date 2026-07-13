package _959.server_waypoint.common.client.gui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualBoundsTest {
    @Test
    void toggleBoundsMatchFilledBackgroundArea() {
        VisualBounds bounds = new VisualBounds(0, 1, 0, -1);

        assertEquals(20, bounds.x(20));
        assertEquals(29, bounds.y(30));
        assertEquals(100, bounds.width(100));
        assertEquals(11, bounds.height(11));
        assertEquals(100, bounds.contentWidth(100));
        assertEquals(11, bounds.contentHeight(11));
    }

    @Test
    void translucentButtonBoundsMatchRenderedOutlineArea() {
        VisualBounds bounds = new VisualBounds(1, 2, 1, 0);

        assertEquals(19, bounds.x(20));
        assertEquals(28, bounds.y(30));
        assertEquals(102, bounds.width(100));
        assertEquals(13, bounds.height(11));
        assertEquals(100, bounds.contentWidth(102));
        assertEquals(11, bounds.contentHeight(13));
    }
}
