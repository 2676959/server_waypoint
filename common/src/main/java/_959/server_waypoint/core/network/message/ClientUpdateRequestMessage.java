package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;

import java.util.List;

/** Requests canonical server snapshots for client revisions that no longer match. */
public record ClientUpdateRequestMessage(WaypointScope scope, List<DimensionSyncIdentifier> dimensionSyncIds) implements WaypointScopedMessage {
    public ClientUpdateRequestMessage {
        java.util.Objects.requireNonNull(scope, "scope");
        dimensionSyncIds = List.copyOf(dimensionSyncIds);
    }

    @Override
    public ChunkedMessageType<ClientUpdateRequestMessage> getType() {
        return ChunkedMessageRegistry.CLIENT_UPDATE_REQUEST;
    }
}
