package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.core.waypoint.WaypointSorting;

final class WaypointSortButtonLabel {
    private WaypointSortButtonLabel() {
    }

    static String directionSuffix(
            WaypointSorting.SortMode buttonMode,
            WaypointSorting.SortMode activeMode,
            boolean reversed
    ) {
        if (buttonMode != activeMode || activeMode == WaypointSorting.SortMode.DEFAULT) {
            return "";
        }
        return reversed ? " ↓" : " ↑";
    }
}
