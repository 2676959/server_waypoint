package _959.server_waypoint.crossserver;

import java.util.Map;
import java.util.Objects;

/** Waypoints keyed by exact waypoint identity, independent of display labels. */
public record RemoteListSnapshot(
        String displayName,
        RemoteRevision listRevision,
        Map<String, RemoteWaypointSnapshot> waypoints
) {
    public RemoteListSnapshot {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(listRevision, "listRevision");
        waypoints = Map.copyOf(waypoints);
    }
}
