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
        this(dimensionName, listName, snapshot(waypoint));
    }

    private NavigationTarget(
            String dimensionName,
            String listName,
            WaypointSnapshot waypointSnapshot
    ) {
        this(
                dimensionName,
                listName,
                waypointSnapshot.name(),
                waypointSnapshot.position(),
                waypointSnapshot.rgb()
        );
    }

    private static WaypointSnapshot snapshot(SimpleWaypoint waypoint) {
        SimpleWaypoint snapshot = new SimpleWaypoint(Objects.requireNonNull(waypoint, "waypoint"));
        return new WaypointSnapshot(snapshot.name(), snapshot.pos(), snapshot.rgb());
    }

    private record WaypointSnapshot(String name, WaypointPos position, int rgb) {
    }
}
