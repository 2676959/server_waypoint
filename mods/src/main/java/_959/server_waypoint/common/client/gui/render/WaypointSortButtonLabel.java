package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.core.waypoint.WaypointSorting;

public final class WaypointSortButtonLabel {
    private WaypointSortButtonLabel() {
    }

    public static String directionSuffix(
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
