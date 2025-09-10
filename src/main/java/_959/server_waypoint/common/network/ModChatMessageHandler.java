package _959.server_waypoint.common.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.network.ChatMessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;

//? if neoforge
/*import net.minecraft.text.Text;*/

public abstract class ModChatMessageHandler<K> extends ChatMessageHandler<ServerCommandSource, K, ServerPlayerEntity> {
    private MinecraftServer server;

    public void onChatMessage(
            //? if fabric {
            SignedMessage message,
            //?} elif neoforge {
            /*Text message,
            *///?}
            ServerPlayerEntity player, MessageType.Parameters parameters) {
        String messageString = message
                //? if fabric
                .getContent()
                .getString();
        this.onChatMessage(player, messageString);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public ModChatMessageHandler(ModMessageSender sender, PermissionManager<ServerCommandSource, K, ServerPlayerEntity> permissionManager) {
        super(sender, permissionManager);
    }

    @Override
    protected boolean isDimensionValid(String dimensionName) {
        if (this.server == null) {
            LOGGER.info("MinecraftServer not initialized.");
            return false;
        }
        return this.server.getWorld(getDimensionKey(dimensionName)) != null;
    }
}
