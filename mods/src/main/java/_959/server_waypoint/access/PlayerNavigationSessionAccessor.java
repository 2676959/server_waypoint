package _959.server_waypoint.access;

import org.jetbrains.annotations.Nullable;

/** Access to the serialized navigation session carried by a server player. */
public interface PlayerNavigationSessionAccessor {
    @Nullable String sw$getNavigationSession();

    void sw$setNavigationSession(@Nullable String encodedSession);
}
