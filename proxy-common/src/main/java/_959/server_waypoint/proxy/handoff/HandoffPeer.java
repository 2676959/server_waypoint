package _959.server_waypoint.proxy.handoff;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.transport.TransportMode;
import java.util.Objects;
import java.util.UUID;

/** Transport-owner supplied context, never decoded from a backend message or built from presence alone. */
public record HandoffPeer(RemoteServerId serverId, UUID sessionId, TransportMode mode) {
    public HandoffPeer {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(mode, "mode");
        if (sessionId.equals(new UUID(0, 0))) throw new IllegalArgumentException("Nil session ID");
    }
}
