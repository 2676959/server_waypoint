package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.Objects;

public record WaypointListUpdateMessage(
        WaypointScope scope,
        String dimensionName,
        String previousListIdentifier,
        WaypointList waypointList
) implements WaypointScopedMessage {
    public WaypointListUpdateMessage {
        java.util.Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(previousListIdentifier, "previousListIdentifier");
        waypointList = Objects.requireNonNull(waypointList, "waypointList").deepCopy();
    }

    @Override
    public ChunkedMessageType<WaypointListUpdateMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_LIST_UPDATE;
    }
}
