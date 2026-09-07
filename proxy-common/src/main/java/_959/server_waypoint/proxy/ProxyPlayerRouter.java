package _959.server_waypoint.proxy;

import java.util.Optional;
import java.util.UUID;

/** Resolves proxy-authenticated players and delegates asynchronous backend switches. */
public interface ProxyPlayerRouter<S> extends TransferAdapter<S> {
    Optional<ProxyPlayerSnapshot> findPlayer(UUID playerId);
}
