package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.Objects;

public record WaypointListUpdateMessage(
        String dimensionName,
        String previousListIdentifier,
        WaypointList waypointList
) implements ChunkedMessage {
    public WaypointListUpdateMessage {
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(previousListIdentifier, "previousListIdentifier");
        waypointList = Objects.requireNonNull(waypointList, "waypointList").deepCopy();
    }

    @Override
    public ChunkedMessageType<WaypointListUpdateMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_LIST_UPDATE;
    }
}
