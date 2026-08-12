package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;

public record WaypointModificationMessage(
        String dimensionName,
        String listName,
        String listDisplayName,
        String waypointName,
        SimpleWaypoint waypoint,
        WaypointModificationType type,
        int syncId) implements ChunkedMessage {
    public WaypointModificationMessage {
        waypoint = waypoint == null ? null : new SimpleWaypoint(waypoint);
    }

    @Override
    public ChunkedMessageType<WaypointModificationMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_MODIFICATION;
    }
}
