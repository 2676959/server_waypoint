package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record WaypointEditResultMessage(
        long requestId,
        EditResultStatus status,
        String dimensionName,
        String listIdentifier,
        String previousWaypointIdentifier,
        @Nullable SimpleWaypoint waypoint,
        int listRevision
) implements ChunkedMessage {
    public WaypointEditResultMessage {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        Objects.requireNonNull(previousWaypointIdentifier, "previousWaypointIdentifier");
        waypoint = waypoint == null ? null : new SimpleWaypoint(waypoint);
    }

    @Override
    public ChunkedMessageType<WaypointEditResultMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_EDIT_RESULT;
    }
}
