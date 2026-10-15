package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointListWidgetEmptyStateTest {
    @Test
    void searchMissWinsInEveryScope() {
        assertEquals(WaypointListWidget.EmptyReason.NO_MATCHES,
                WaypointListWidget.resolveEmptyReason("tower", false));
        assertEquals(WaypointListWidget.EmptyReason.NO_MATCHES,
                WaypointListWidget.resolveEmptyReason("tower", true));
    }

    @Test
    void blankSearchReportsTheScope() {
        assertEquals(WaypointListWidget.EmptyReason.NO_WAYPOINTS_IN_DIMENSION,
                WaypointListWidget.resolveEmptyReason("", false));
        assertEquals(WaypointListWidget.EmptyReason.NO_WAYPOINTS,
                WaypointListWidget.resolveEmptyReason("   ", true));
    }

    @Test
    void reasonsUseTheSharedTranslationKeys() {
        assertEquals("waypoint.empty.no_matches", WaypointListWidget.EmptyReason.NO_MATCHES.translationKey());
        assertEquals("waypoint.empty.all", WaypointListWidget.EmptyReason.NO_WAYPOINTS.translationKey());
        assertEquals("waypoint.empty.dimension",
                WaypointListWidget.EmptyReason.NO_WAYPOINTS_IN_DIMENSION.translationKey());
    }
}
