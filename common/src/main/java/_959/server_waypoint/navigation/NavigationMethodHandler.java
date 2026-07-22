package _959.server_waypoint.navigation;

import java.util.UUID;

/**
 * Platform implementation for one navigation method.
 */
public interface NavigationMethodHandler<P> {
    NavigationMethod method();

    NavigationResult enable(
            P player,
            NavigationSession session,
            NavigationSnapshot snapshot
    );

    void update(
            P player,
            NavigationSession session,
            NavigationSnapshot snapshot
    );

    void disable(P player, NavigationSession session);

    /**
     * Releases method state that is keyed only by player UUID. Lifecycle
     * cleanup invokes this even when the platform player object is no longer
     * available, and after ordinary player-aware cleanup as a final safeguard.
     */
    default void cleanupPlayer(UUID playerUuid, NavigationSession session) {
    }
}
