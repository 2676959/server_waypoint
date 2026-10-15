package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void railControlsPaintNoFillAtRestButKeepTheirStateFills() {
        // The panel under a rail control already paints a translucent fill, so a second fill at rest
        // would stack on it. Hover and selection keep their fills; disabled icons use a tint instead.
        assertNull(WaypointManagerScreen.resolveIconControlFill(true, false, false, false));
        assertEquals(
                WidgetThemeVariable.CONTROL_HOVER_BACKGROUND,
                WaypointManagerScreen.resolveIconControlFill(true, false, true, false)
        );
        assertEquals(
                WidgetThemeVariable.SELECTION_BACKGROUND,
                WaypointManagerScreen.resolveIconControlFill(true, true, false, false)
        );
        assertNull(WaypointManagerScreen.resolveIconControlFill(false, false, false, false));
    }

    @Test
    void popupRowsKeepTheirRestingFillBecauseTheyFloatOverOtherWidgets() {
        assertEquals(
                WidgetThemeVariable.CONTROL_BACKGROUND,
                WaypointManagerScreen.resolveIconControlFill(true, false, false, true)
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
