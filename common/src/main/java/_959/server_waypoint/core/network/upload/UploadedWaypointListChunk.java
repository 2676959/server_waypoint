package _959.server_waypoint.core.network.upload;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import java.util.List;

/** A bounded portion of one Xaero waypoint set. */
public record UploadedWaypointListChunk(String dimensionName, String listName, List<SimpleWaypoint> waypoints) {
    public UploadedWaypointListChunk {
        waypoints = List.copyOf(waypoints);
    }
}
