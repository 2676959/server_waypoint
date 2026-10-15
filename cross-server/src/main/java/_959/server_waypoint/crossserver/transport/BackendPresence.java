package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import java.time.Instant;
import java.util.Set;

/** Immutable administrative observation; never an authorization token. */
public record BackendPresence(RemoteServerId serverId, TransportMode mode, Set<Integer> capabilities,
                              long generation, Instant registeredAt) {
    public BackendPresence { capabilities = Set.copyOf(capabilities); }
    public boolean authenticated() { return mode == TransportMode.NOISE_KK; }
    public String securityStatus() { return authenticated() ? "KK_AUTHENTICATED" : "TRUSTED_LOOPBACK"; }
}
