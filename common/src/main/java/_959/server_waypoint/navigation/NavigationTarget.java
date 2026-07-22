package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;

import java.util.Objects;

/**
 * An immutable copy of the waypoint properties needed by navigation.
 */
public record NavigationTarget(
        String dimensionName,
        String listName,
        String waypointName,
        WaypointPos position,
        int rgb
) {
    public NavigationTarget {
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(waypointName, "waypointName");
        Objects.requireNonNull(position, "position");
    }

    public NavigationTarget(String dimensionName, String listName, SimpleWaypoint waypoint) {
        this(
                dimensionName,
                listName,
                Objects.requireNonNull(waypoint, "waypoint").name(),
                waypoint.pos(),
                waypoint.rgb()
        );
    }
}
