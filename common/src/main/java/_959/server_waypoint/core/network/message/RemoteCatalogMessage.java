package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.network.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import java.util.*;

/** Atomic replacement of the remote-only client cache. Never local waypoint data. */
public record RemoteCatalogMessage(UUID requestId, RemoteCatalogState state,
                                   Map<RemoteServerId, CatalogReceiver.View> servers) implements ChunkedMessage {
    public RemoteCatalogMessage {
        Objects.requireNonNull(requestId); Objects.requireNonNull(state);
        servers = Map.copyOf(servers);
        if (servers.size() > 256 || state != RemoteCatalogState.AVAILABLE && !servers.isEmpty()) {
            throw new IllegalArgumentException("Invalid remote cache replacement");
        }
        servers.forEach((id, view) -> {
            Objects.requireNonNull(view.state()); Objects.requireNonNull(view.displayName());
            if (view.state() == RemoteCatalogState.UNAUTHORIZED
                    || view.snapshot() != null && !id.equals(view.snapshot().serverId())
                    || view.state() == RemoteCatalogState.AVAILABLE && view.snapshot() == null
                    || view.state() == RemoteCatalogState.UNAVAILABLE && view.snapshot() != null) {
                throw new IllegalArgumentException("Invalid remote server view");
            }
        });
    }
    @Override public ChunkedMessageType<RemoteCatalogMessage> getType() {
        return ChunkedMessageRegistry.REMOTE_CATALOG;
    }
}
