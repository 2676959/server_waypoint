package _959.server_waypoint;

import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.network.ChatMessageHandler;
import _959.server_waypoint.core.network.ClientHandshakeHandler;
import _959.server_waypoint.core.network.PlatformMessageSender;

public class ServerWaypointPlugin<S, K, P, D, B, C> {
    private final PlatformMessageSender<S, P> sender;
    private final PermissionManager<S, K, P> permissionManager;
    private final CoreWaypointCommand<S, K, P, D, B, C> command;
    private final ChatMessageHandler<S, K, P> chatMessageHandler;
    private final ClientHandshakeHandler<S, P> clientHandshakeHandler;

    public ServerWaypointPlugin(PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissionManager, CoreWaypointCommand<S, K, P, D, B, C> command, ChatMessageHandler<S, K, P> chatMessageHandler, ClientHandshakeHandler<S, P> clientHandshakeHandler) {
        this.sender = sender;
        this.permissionManager = permissionManager;
        this.command = command;
        this.chatMessageHandler = chatMessageHandler;
        this.clientHandshakeHandler = clientHandshakeHandler;
    }
}
