package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;

public record WaypointModificationBuffer(
        String dimString,
        String listName,
        SimpleWaypoint waypoint,
        WaypointModificationType type,
        int edition) {
}