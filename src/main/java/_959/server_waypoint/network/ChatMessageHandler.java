package _959.server_waypoint.network;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import net.minecraft.world.World;

import static _959.server_waypoint.ServerWaypoint.LOGGER;
import static _959.server_waypoint.util.SimpleWaypointHelper.XAERO_SHARE_PREFIX;
import static _959.server_waypoint.util.SimpleWaypointHelper.chatShareToSimpleWaypoint;

public class ChatMessageHandler {
    public static void onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
        String messageString = message.getContent().getString();
        LOGGER.info("{}, {}, {}", messageString, player.toString(), parameters.toString());
        if (messageString.startsWith(XAERO_SHARE_PREFIX)) {
            LOGGER.info("received");
            Pair<SimpleWaypoint, RegistryKey<World>> simpleWaypoint = chatShareToSimpleWaypoint(messageString);
            LOGGER.info("{}, {}", simpleWaypoint.getLeft().toString(), simpleWaypoint.getRight().getValue().toString());
        }
    }
}
