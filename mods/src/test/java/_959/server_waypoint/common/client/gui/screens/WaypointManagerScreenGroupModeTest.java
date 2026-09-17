package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointManagerScreenGroupModeTest {
    @Test
    void flatModeUsesNameSortingWhenCurrentSortIsDefault() {
        assertEquals(
                WaypointSorting.SortMode.NAME,
                WaypointManagerScreen.resolveSortModeForGroupMode(
                        WaypointSorting.SortMode.DEFAULT,
                        false
                )
        );
    }

    @Test
    void groupedModeKeepsDefaultSorting() {
        assertEquals(
                WaypointSorting.SortMode.DEFAULT,
                WaypointManagerScreen.resolveSortModeForGroupMode(
                        WaypointSorting.SortMode.DEFAULT,
                        true
                )
        );
    }

    @Test
    void flatModeKeepsAnExistingNonDefaultSort() {
        assertEquals(
                WaypointSorting.SortMode.DISTANCE,
                WaypointManagerScreen.resolveSortModeForGroupMode(
                        WaypointSorting.SortMode.DISTANCE,
                        false
                )
        );
    }
}
