package _959.server_waypoint.navigation;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform access required by the shared navigation service.
 */
public interface NavigationPlatform<P> {
    UUID playerUuid(P player);

    Optional<P> findPlayer(UUID playerUuid);

    NavigationSnapshot snapshot(P player, NavigationTarget target);

    /**
     * Validates a complete candidate session before any handler or stored
     * session is changed. Item implementations use the candidate's full method
     * selection for atomic inventory-capacity checks.
     */
    default NavigationResult preflight(
            P player,
            @Nullable NavigationSession currentSession,
            NavigationSession proposedSession
    ) {
        return NavigationResult.success();
    }

    /**
     * Implementations may throw here to enforce the server-thread-only contract.
     */
    default void assertServerThread() {
    }

    /**
     * Receives isolated handler failures from ticking or cleanup.
     */
    default void onHandlerException(
            UUID playerUuid,
            NavigationMethod method,
            RuntimeException exception
    ) {
    }

    /** Reads a serialized session from platform-owned player persistence. */
    default Optional<String> loadPersistedSession(P player) {
        return Optional.empty();
    }

    /** Replaces the serialized session in platform-owned player persistence. */
    default void savePersistedSession(P player, String encodedSession) {
    }

    /** Removes the serialized session after navigation is explicitly ended. */
    default void clearPersistedSession(P player) {
    }

    /** Receives isolated failures from platform persistence operations. */
    default void onPersistenceException(
            UUID playerUuid,
            String operation,
            RuntimeException exception
    ) {
    }
}
