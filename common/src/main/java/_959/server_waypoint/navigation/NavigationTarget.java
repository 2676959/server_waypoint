package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;

import java.util.Objects;

/**
 * An immutable copy of the waypoint properties needed by navigation.
 */
public record NavigationTarget(
        String dimensionName,
        String listName,
        String listDisplayName,
        String waypointName,
        String waypointDisplayName,
        String waypointDescription,
        WaypointPos position,
        int rgb
) {
    public NavigationTarget {
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(listDisplayName, "listDisplayName");
        Objects.requireNonNull(waypointName, "waypointName");
        Objects.requireNonNull(waypointDisplayName, "waypointDisplayName");
        Objects.requireNonNull(waypointDescription, "waypointDescription");
        Objects.requireNonNull(position, "position");
    }

    public NavigationTarget(
            String dimensionName,
            WaypointList waypointList,
            SimpleWaypoint waypoint
    ) {
        this(dimensionName, waypointList.name(), waypointList.displayName(), snapshot(waypoint));
    }

    private NavigationTarget(
            String dimensionName,
            String listName,
            String listDisplayName,
            WaypointSnapshot waypointSnapshot
    ) {
        this(
                dimensionName,
                listName,
                listDisplayName,
                waypointSnapshot.name(),
                waypointSnapshot.displayName(),
                waypointSnapshot.description(),
                waypointSnapshot.position(),
                waypointSnapshot.rgb()
        );
    }

    private static WaypointSnapshot snapshot(SimpleWaypoint waypoint) {
        SimpleWaypoint snapshot = new SimpleWaypoint(Objects.requireNonNull(waypoint, "waypoint"));
        return new WaypointSnapshot(
                snapshot.name(),
                snapshot.displayName(),
                snapshot.description(),
                snapshot.pos(),
                snapshot.rgb()
        );
    }

    private record WaypointSnapshot(
            String name,
            String displayName,
            String description,
            WaypointPos position,
            int rgb
    ) {
    }
}
