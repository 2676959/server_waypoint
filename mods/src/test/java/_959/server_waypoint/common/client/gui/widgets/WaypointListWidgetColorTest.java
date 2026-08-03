package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointListWidgetColorTest {
    @Test
    void renderedWaypointTextKeepsItsOriginalColor() {
        assertEquals(0xC0123456, WaypointListWidget.applyWaypointTextOpacity(0xC0123456, true));
    }

    @Test
    void disabledWaypointTextKeepsRgbAndHalvesAlpha() {
        assertEquals(0x60123456, WaypointListWidget.applyWaypointTextOpacity(0xC0123456, false));
        assertEquals(0x80123456, WaypointListWidget.applyWaypointTextOpacity(0xFF123456, false));
    }
}
