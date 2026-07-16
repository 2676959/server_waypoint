package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointManagerScreenIconControlTest {
    @Test
    void focusedClosedDropdownUsesTheNormalBorder() {
        assertEquals(
                WidgetThemeVariable.BORDER,
                WaypointManagerScreen.resolveIconControlBorder(true, true, false, false)
        );
    }

    @Test
    void expandedFocusAndHoverStillUseTheFocusRing() {
        assertEquals(
                WidgetThemeVariable.FOCUS_RING,
                WaypointManagerScreen.resolveIconControlBorder(true, true, false, true)
        );
        assertEquals(
                WidgetThemeVariable.FOCUS_RING,
                WaypointManagerScreen.resolveIconControlBorder(true, false, true, false)
        );
    }

    @Test
    void inactiveControlAlwaysUsesTheNormalBorder() {
        assertEquals(
                WidgetThemeVariable.BORDER,
                WaypointManagerScreen.resolveIconControlBorder(false, true, true, true)
        );
    }
}
