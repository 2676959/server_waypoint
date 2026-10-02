package _959.server_waypoint.crossserver.authorization;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.CommandPermission;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Backend permission adapter. Invoke on the platform thread owning the source/player. */
public final class RemotePermissions<S, K, P> {
    private final PermissionManager<S, K, P> permissions;
    private final Supplier<CommandPermission> configuration;
    private final Function<S, P> sourcePlayer;

    public RemotePermissions(PermissionManager<S, K, P> permissions,
                             Supplier<CommandPermission> configuration, Function<S, P> sourcePlayer) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.sourcePlayer = Objects.requireNonNull(sourcePlayer, "sourcePlayer");
    }

    /** Console browsing follows the platform's ordinary command permission rules. */
    public boolean canList(S source) {
        return permissions.hasPermission(source, permissions.keys.remoteList(), configuration.get().remoteList());
    }

    /** A console cannot initiate a player handoff. Both checks use the actual source player. */
    public boolean canRequestTeleport(S source) {
        P player = sourcePlayer.apply(source);
        if (player == null) return false;
        CommandPermission levels = configuration.get();
        return permissions.checkPlayerPermission(player, permissions.keys.tp(), levels.tp())
                && permissions.checkPlayerPermission(player, permissions.keys.remoteTp(), levels.remoteTp());
    }

    /** Recheck the arrived player's local teleport permission, never a source assertion or UUID lookup. */
    public boolean canTeleportOnArrival(P player) {
        return player != null && permissions.checkPlayerPermission(
                player, permissions.keys.tp(), configuration.get().tp());
    }
}
