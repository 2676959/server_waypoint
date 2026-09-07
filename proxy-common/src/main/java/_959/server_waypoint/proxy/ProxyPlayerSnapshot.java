package _959.server_waypoint.proxy;

import _959.server_waypoint.crossserver.RemoteServerId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Point-in-time proxy observation, never a forwarded claim or permission to transfer. */
public record ProxyPlayerSnapshot(UUID playerId, Optional<RemoteServerId> currentServer) {
    public ProxyPlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(currentServer, "currentServer");
    }
}
