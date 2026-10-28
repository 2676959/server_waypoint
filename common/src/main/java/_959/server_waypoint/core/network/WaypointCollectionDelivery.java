package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.message.WaypointCollectionStateMessage;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

/** The shared read gate for private command and packet delivery. Recheck immediately before sending. */
public final class WaypointCollectionDelivery<S, K, P> {
    private final PlatformMessageSender<S, P> sender;
    private final PermissionManager<S, K, P> permissions;

    public WaypointCollectionDelivery(PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissions) {
        this.sender = sender;
        this.permissions = permissions;
    }

    public boolean canReadPrivate(P player) {
        if (!sender.canSendChunkedMessage(player)) return false;
        boolean allowed = permissions.checkPlayerPermission(player, permissions.keys.privateList(),
                CONFIG.CommandPermission().privateList());
        if (!allowed) sender.sendPlayerChunkedMessage(player,
                new WaypointCollectionStateMessage(WaypointScope.PRIVATE, false));
        return allowed;
    }

    public ChunkedMessageDelivery sendPrivate(P player, WaypointScopedMessage message) {
        if (message.scope() != WaypointScope.PRIVATE) throw new IllegalArgumentException("Expected private message");
        if (!canReadPrivate(player)) return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
        return sender.sendPlayerChunkedMessageTracked(player, message, () -> canReadPrivate(player));
    }
}
