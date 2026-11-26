package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import java.util.Objects;

/** Authorization is independent of snapshot readiness. Denial must clear cached presentation. */
public record WaypointCollectionStateMessage(WaypointScope scope, boolean readable) implements WaypointScopedMessage {
    public WaypointCollectionStateMessage { Objects.requireNonNull(scope, "scope"); }

    @Override
    public ChunkedMessageType<WaypointCollectionStateMessage> getType() {
        return ChunkedMessageRegistry.WAYPOINT_COLLECTION_STATE;
    }
}
