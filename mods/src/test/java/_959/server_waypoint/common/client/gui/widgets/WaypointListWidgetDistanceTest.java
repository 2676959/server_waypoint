package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointListWidgetDistanceTest {
    @Test
    void formatsShortDistancesInWholeMeters() {
        assertEquals("0 m", WaypointListWidget.formatDistance(0.0D));
        assertEquals("999 m", WaypointListWidget.formatDistance(999.4D));
    }

    @Test
    void formatsLongDistancesInCompactKilometers() {
        assertEquals("1 km", WaypointListWidget.formatDistance(999.5D));
        assertEquals("1.3 km", WaypointListWidget.formatDistance(1250.0D));
        assertEquals("10 km", WaypointListWidget.formatDistance(10000.0D));
    }
}
