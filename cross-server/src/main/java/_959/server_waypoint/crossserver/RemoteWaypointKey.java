package _959.server_waypoint.crossserver;

import java.util.Objects;

/** Exact identity tuple. Coordinates and presentation fields are not part of identity. */
public record RemoteWaypointKey(
        RemoteServerId serverId,
        String dimensionName,
        String listName,
        String waypointName
) {
    public RemoteWaypointKey {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(waypointName, "waypointName");
    }
}
