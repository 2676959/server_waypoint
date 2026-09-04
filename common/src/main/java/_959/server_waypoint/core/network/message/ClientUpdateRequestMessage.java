package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;

import java.util.List;

/** Requests canonical server snapshots for client revisions that no longer match. */
public record ClientUpdateRequestMessage(List<DimensionSyncIdentifier> dimensionSyncIds) implements ChunkedMessage {
    public ClientUpdateRequestMessage {
        dimensionSyncIds = List.copyOf(dimensionSyncIds);
    }

    @Override
    public ChunkedMessageType<ClientUpdateRequestMessage> getType() {
        return ChunkedMessageRegistry.CLIENT_UPDATE_REQUEST;
    }
}
