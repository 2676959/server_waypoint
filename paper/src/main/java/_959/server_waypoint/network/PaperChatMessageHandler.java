package _959.server_waypoint.network;

import _959.server_waypoint.core.network.ChatMessageHandler;
import _959.server_waypoint.server.command.permission.PaperPermissionManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class PaperChatMessageHandler extends ChatMessageHandler<CommandSourceStack, String, Player> {
    private final Server server;

    public PaperChatMessageHandler(Server server, PaperMessageSender sender, PaperPermissionManager permissionManager) {
        super(sender, permissionManager);
        this.server = server;
    }

    @Override
    protected boolean isDimensionValid(String dimensionName) {
        String[] split = dimensionName.split(":");
        NamespacedKey dimensionKey = new NamespacedKey(split[0], split[1]);
        return server.getWorld(dimensionKey) != null;
    }
}
