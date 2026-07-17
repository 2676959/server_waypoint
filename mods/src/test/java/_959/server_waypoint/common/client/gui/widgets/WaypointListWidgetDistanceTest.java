package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void distanceSortRefreshesWhenPlayerMovesToAnotherBlock() {
        assertTrue(WaypointListWidget.shouldRefreshDistanceSort(
                WaypointSorting.SortMode.DISTANCE,
                new WaypointPos(0, 64, 0),
                new WaypointPos(1, 64, 0),
                null,
                null
        ));
    }

    @Test
    void distanceSortDoesNotRefreshWhilePlayerRemainsInTheSameBlock() {
        assertFalse(WaypointListWidget.shouldRefreshDistanceSort(
                WaypointSorting.SortMode.DISTANCE,
                new WaypointPos(0, 64, 0),
                new WaypointPos(0, 64, 0),
                null,
                null
        ));
    }

    @Test
    void otherSortModesDoNotRefreshAfterPlayerMovement() {
        assertFalse(WaypointListWidget.shouldRefreshDistanceSort(
                WaypointSorting.SortMode.NAME,
                new WaypointPos(0, 64, 0),
                new WaypointPos(1, 64, 0),
                null,
                null
        ));
    }

    @Test
    void allDimensionsDistanceSortRefreshesAfterDimensionChange() {
        assertTrue(WaypointListWidget.shouldRefreshDistanceSort(
                WaypointSorting.SortMode.DISTANCE,
                new WaypointPos(0, 64, 0),
                new WaypointPos(0, 64, 0),
                "minecraft:overworld",
                "minecraft:the_nether"
        ));
    }
}
