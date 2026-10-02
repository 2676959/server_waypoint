package _959.server_waypoint.crossserver.authorization;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.RemoteWaypointKey;
import java.util.Objects;
import java.util.function.Predicate;

/** Fresh destination checks; these results are not reservations or authority to transfer a player. */
public final class DestinationAuthorization<P> {
    public enum Result {
        ALLOWED, WRONG_DESTINATION, NOT_EXPORTED, PLAYER_REQUIRED, PERMISSION_DENIED, UNAVAILABLE
    }

    /**
     * Resolve the exact identity from current local state and current PUBLIC export policy.
     * Return false for a missing/unexported waypoint; throw when authoritative state is unavailable.
     * No player UUID is accepted: v1 does not support player-private catalogs or offline permissions.
     */
    @FunctionalInterface
    public interface ExportPolicy {
        boolean isCurrentlyExported(RemoteWaypointKey key) throws Exception;
    }

    private final RemoteServerId serverId;
    private final ExportPolicy exports;
    private final Predicate<P> finalPermission;

    public DestinationAuthorization(RemoteServerId serverId, ExportPolicy exports, Predicate<P> finalPermission) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.exports = Objects.requireNonNull(exports, "exports");
        this.finalPermission = Objects.requireNonNull(finalPermission, "finalPermission");
    }

    /** Run with safe access to authoritative destination state. Does not check an offline player. */
    public Result prepare(RemoteWaypointKey key) {
        Objects.requireNonNull(key, "key");
        if (!serverId.equals(key.serverId())) return Result.WRONG_DESTINATION;
        try {
            return exports.isCurrentlyExported(key) ? Result.ALLOWED : Result.NOT_EXPORTED;
        } catch (Exception unavailable) {
            return Result.UNAVAILABLE;
        }
    }

    /**
     * Invoke on the arrived player's owning thread after the caller validates the claim/UUID.
     * Re-evaluates export and local permission every time. The caller must still resolve current
     * coordinates and perform the teleport in the same owning-thread operation (step 14).
     */
    public Result arrive(RemoteWaypointKey key, P player) {
        if (player == null) return Result.PLAYER_REQUIRED;
        Result export = prepare(key);
        if (export != Result.ALLOWED) return export;
        try {
            return finalPermission.test(player) ? Result.ALLOWED : Result.PERMISSION_DENIED;
        } catch (Exception unavailable) {
            return Result.UNAVAILABLE;
        }
    }
}
