package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;

import java.util.Objects;

public record WaypointEditRequestMessage(
        WaypointScope scope,
        long requestId,
        String dimensionName,
        String listIdentifier,
        String waypointIdentifier,
        int expectedListRevision,
        WaypointPatch patch
) implements WaypointScopedMessage {
    public WaypointEditRequestMessage {
        java.util.Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        Objects.requireNonNull(waypointIdentifier, "waypointIdentifier");
        Objects.requireNonNull(patch, "patch");
    }

    @Override
    public ChunkedMessageType<WaypointEditRequestMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_EDIT_REQUEST;
    }
}
