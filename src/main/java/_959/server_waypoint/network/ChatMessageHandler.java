package _959.server_waypoint.network;

import _959.server_waypoint.server.waypoint.DimensionManager;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.util.Pair;
import net.minecraft.world.World;

import java.util.Set;

import static _959.server_waypoint.ServerWaypoint.LOGGER;
import static _959.server_waypoint.server.WaypointServer.INSTANCE;
import static _959.server_waypoint.server.WaypointServer.CONFIG;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.util.SimpleWaypointHelper.*;
import static _959.server_waypoint.util.TextButton.addButton;
import static _959.server_waypoint.util.TextButton.addListButton;
import static _959.server_waypoint.util.TextHelper.text;
import static _959.server_waypoint.util.TextHelper.waypointInfoText;

public class ChatMessageHandler {
    public static void onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
        if (! player.hasPermissionLevel(CONFIG.CommandPermission().add())) {
            return;
        }
        String messageString = message.getContent().getString();
        if (messageString.startsWith(XAERO_SHARE_PREFIX)) {
            LOGGER.info("found chat shared waypoint");
            Pair<SimpleWaypoint, RegistryKey<World>> waypointWithDim = chatShareToSimpleWaypoint(messageString);
            if (waypointWithDim == null) {
                LOGGER.info("invalid waypoint sharing message");
                return;
            }
            SimpleWaypoint waypoint = waypointWithDim.getLeft();
            RegistryKey<World> dimKey = waypointWithDim.getRight();
            if (waypoint == null) {
                LOGGER.info("unknown waypoint received");
                return;
            }
            if (CONFIG.AddWaypointFromChatSharing().auto()) {
                DimensionManager dimensionManager = INSTANCE.getDimensionManager(dimKey);
                if (dimensionManager != null) {
                    Set<String> listNames = dimensionManager.getWaypointListMap().keySet();
                    if (listNames.isEmpty()) {
                        promptNoWaypointList(player, dimKey);
                    } else {
                        player.sendMessage(text("Found chat shared waypoint: ").append(
                                simpleWaypointToFormattedText(
                                        waypoint,
                                        tpCmd(dimKey, waypoint.pos(), waypoint.yaw()),
                                        waypointInfoText(dimKey, waypoint))
                        ));
                        player.sendMessage(text("Select list to add:"));
                        for (String name : listNames) {
                            MutableText listItem = addButton(dimKey, name, waypoint);
                            listItem.append(text(" " + name).setStyle(DEFAULT_STYLE));
                            player.sendMessage(listItem);
                        }
                    }
                } else {
                    LOGGER.info("dimension not found, add new dimension");
                    INSTANCE.addDimensionManager(dimKey);
                    promptNoWaypointList(player, dimKey);
                }
            }
        }
    }

    private static void promptNoWaypointList(ServerPlayerEntity player, RegistryKey<World> dimKey) {
        MutableText feedback = text("No waypoint list available. Add a waypoint list first. ");
        feedback.append(addListButton(dimKey, ""));
        player.sendMessage(feedback);
    }
}
