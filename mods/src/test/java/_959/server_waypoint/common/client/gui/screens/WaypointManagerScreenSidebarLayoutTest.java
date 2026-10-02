package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerScreenSidebarLayoutTest {
    // The content rectangle of an 854x480 viewport.
    private static final int CONTENT_Y = 43;
    private static final int CONTENT_HEIGHT = 394;

    @Test
    void sixControlsLeaveTheRailTheSpaceAboveThem() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 6, 400, 16, false);

        assertTrue(layout.controlsVisible());
        assertEquals(321, layout.controlsY());
        assertEquals(CONTENT_Y, layout.dimensionY());
        assertEquals(272, layout.dimensionHeight());
        assertFalse(layout.serverVisible());
    }

    @Test
    void aHiddenControlReleasesItsSlotToTheRail() {
        WaypointManagerScreen.SidebarLayout six =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 6, 400, 16, false);
        WaypointManagerScreen.SidebarLayout five =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 400, 16, false);

        assertEquals(six.controlsY() + 20, five.controlsY());
        assertEquals(six.dimensionHeight() + 20, five.dimensionHeight());
    }

    @Test
    void visibleControlsStackToTheContentBottom() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 52, 16, false);

        assertEquals(341, layout.controlY(0));
        assertEquals(361, layout.controlY(1));
        assertEquals(CONTENT_Y + CONTENT_HEIGHT, layout.controlY(4) + 16);
    }

    @Test
    void localRailStopsAtItsPreferredHeight() {
        assertEquals(52, WaypointManagerScreen.calculateSidebarLayout(
                CONTENT_Y, CONTENT_HEIGHT, 6, 52, 16, false).dimensionHeight());
    }

    @Test
    void remoteRailsGrowFromOppositeEndsWithSeparatorsMidGap() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 52, 100, true);

        assertEquals(52, layout.dimensionHeight());
        assertEquals(100, layout.serverHeight());
        assertEquals(235, layout.serverY());
        assertEquals(165, layout.railSeparatorY());
        assertEquals(338, layout.controlSeparatorY());
    }

    @Test
    void constrainedRemoteRailsShareTheSpaceEqually() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 400, 400, true);

        assertEquals(143, layout.dimensionHeight());
        assertEquals(143, layout.serverHeight());
    }

    @Test
    void shortContentHidesTheControlsAndBothRails() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(10, 90, 5, 52, 52, true);

        assertFalse(layout.controlsVisible());
        assertFalse(layout.dimensionVisible());
        assertFalse(layout.serverVisible());
    }

    @Test
    void aRailBelowOneIconHides() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(0, 110, 5, 52, 16, false);

        assertTrue(layout.controlsVisible());
        assertFalse(layout.dimensionVisible());
    }
}
