package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointSortButtonLabelTest {
    @Test
    void inactiveSortButtonsHaveNoDirectionArrow() {
        assertEquals("", WaypointSortButtonLabel.directionSuffix(
                WaypointSorting.SortMode.NAME,
                WaypointSorting.SortMode.COLOR,
                false
        ));
    }

    @Test
    void activeSortButtonShowsAscendingArrow() {
        assertEquals(" ↑", WaypointSortButtonLabel.directionSuffix(
                WaypointSorting.SortMode.NAME,
                WaypointSorting.SortMode.NAME,
                false
        ));
    }

    @Test
    void activeSortButtonShowsDescendingArrowWhenReversed() {
        assertEquals(" ↓", WaypointSortButtonLabel.directionSuffix(
                WaypointSorting.SortMode.NAME,
                WaypointSorting.SortMode.NAME,
                true
        ));
    }
}
