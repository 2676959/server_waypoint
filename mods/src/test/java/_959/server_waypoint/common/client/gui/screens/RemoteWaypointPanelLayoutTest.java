package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteWaypointPanelLayoutTest {
    @Test
    void footerSitsBelowTheTreeAfterAGap() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(200, 9, true);

        assertTrue(split.footerVisible());
        assertEquals(187, split.treeHeight());
    }

    @Test
    void wrappedFooterTakesMoreHeight() {
        assertEquals(178, RemoteWaypointPanel.splitListArea(200, 18, true).treeHeight());
    }

    @Test
    void footerStaysWhileTheTreeKeepsOneRow() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(33, 9, true);

        assertTrue(split.footerVisible());
        assertEquals(20, split.treeHeight());
    }

    @Test
    void footerHidesWhenTheTreeWouldLoseItsLastRow() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(32, 9, true);

        assertFalse(split.footerVisible());
        assertEquals(32, split.treeHeight());
    }

    @Test
    void footerHidesWithoutASelectedServer() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(200, 9, false);

        assertFalse(split.footerVisible());
        assertEquals(200, split.treeHeight());
    }
}
