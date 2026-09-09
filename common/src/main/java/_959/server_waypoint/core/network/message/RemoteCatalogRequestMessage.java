package _959.server_waypoint.core.network.message;

import _959.server_waypoint.core.network.*;
import java.util.Objects;
import java.util.UUID;

public record RemoteCatalogRequestMessage(UUID requestId) implements ChunkedMessage {
    public RemoteCatalogRequestMessage { Objects.requireNonNull(requestId); }
    @Override public ChunkedMessageType<RemoteCatalogRequestMessage> getType() {
        return ChunkedMessageRegistry.REMOTE_CATALOG_REQUEST;
    }
}
