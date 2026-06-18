package _959.server_waypoint.common.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.network.ChatMessageHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;

//? if neoforge || forge
/*import net.minecraft.network.chat.Component;*/

public abstract class ModChatMessageHandler<K> extends ChatMessageHandler<CommandSourceStack, K, ServerPlayer> {
    private MinecraftServer server;

    public void onChatMessage(
            //? if fabric {
            PlayerChatMessage message,
            //?} elif neoforge || forge {
            /*Component message,
            *///?}
            ServerPlayer player, ChatType.Bound parameters) {
        String messageString = message
                //? if fabric {
                .decoratedContent()
                //?}
                .getString();
        this.onChatMessage(player, messageString);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public ModChatMessageHandler(ModMessageSender sender, PermissionManager<CommandSourceStack, K, ServerPlayer> permissionManager) {
        super(sender, permissionManager);
    }

    @Override
    protected boolean isDimensionValid(String dimensionName) {
        if (this.server == null) {
            LOGGER.info("MinecraftServer not initialized.");
            return false;
        }
        var key = getDimensionKey(dimensionName);
        if (key == null) return false;
        return this.server.getLevel(key) != null;
    }
}
