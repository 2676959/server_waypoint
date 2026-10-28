package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;

public record WaypointModificationMessage(
        WaypointScope scope,
        String dimensionName,
        String listName,
        String listDisplayName,
        String waypointName,
        SimpleWaypoint waypoint,
        WaypointModificationType type,
        int syncId) implements WaypointScopedMessage {
    public WaypointModificationMessage {
        java.util.Objects.requireNonNull(scope, "scope");
        waypoint = waypoint == null ? null : new SimpleWaypoint(waypoint);
    }

    @Override
    public ChunkedMessageType<WaypointModificationMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_MODIFICATION;
    }
}
