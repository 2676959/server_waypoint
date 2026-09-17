package _959.server_waypoint.navigation;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Platform access required by the shared navigation service.
 */
public interface NavigationPlatform<P> {
    UUID playerUuid(P player);

    /**
     * Executes player-owned work on the platform thread which owns that
     * player. The callback may run immediately when the caller already owns
     * the player.
     */
    void executePlayer(UUID playerUuid, Consumer<P> action);

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
     * Implementations may throw here to enforce player thread ownership.
     */
    default void assertPlayerThread(P player) {
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
