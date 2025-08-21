package _959.server_waypoint.common.network;

import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.util.Pair;

import java.util.Set;

//? if neoforge
/*import net.minecraft.text.Text;*/

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.common.server.WaypointServerMod.CONFIG;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.*;
import static _959.server_waypoint.common.util.TextButton.addButton;
import static _959.server_waypoint.common.util.TextButton.addListButton;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.TextHelper.waypointInfoText;

public class ChatMessageHandler {
    public static void onChatMessage(
            //? if fabric {
            SignedMessage message,
            //?} elif neoforge {
            /*Text message,
            *///?}
            ServerPlayerEntity player, MessageType.Parameters parameters) {
        if (! player.hasPermissionLevel(CONFIG.CommandPermission().add())) {
            return;
        }
        String messageString = message
                //? if fabric
                .getContent()
                .getString();
        if (messageString.startsWith(XAERO_SHARE_PREFIX)) {
            LOGGER.info("found chat shared waypoint");
            Pair<SimpleWaypoint, String> waypointWithDim = chatShareToSimpleWaypoint(messageString);
            if (waypointWithDim == null) {
                LOGGER.info("invalid waypoint sharing message");
                return;
            }
            SimpleWaypoint waypoint = waypointWithDim.getLeft();
            String dimString = waypointWithDim.getRight();
//            if (waypoint == null) {
//                LOGGER.info("unknown waypoint received");
//                return;
//            }
            if (CONFIG.AddWaypointFromChatSharing().enable()) {
                WaypointFileManager waypointFileManager = WaypointServerMod.INSTANCE.getWaypointFileManager(dimString);
                if (waypointFileManager != null) {
                    Set<String> listNames = waypointFileManager.getWaypointListMap().keySet();
                    if (listNames.isEmpty()) {
                        promptNoWaypointList(player, dimString);
                    } else {
                        player.sendMessage(text("Found chat shared waypoint: ").append(
                                simpleWaypointToFormattedText(
                                        waypoint,
                                        tpCmd(dimString, waypoint.pos(), waypoint.yaw()),
                                        waypointInfoText(dimString, waypoint))
                        ));
                        player.sendMessage(text("Select list to add:"));
                        for (String name : listNames) {
                            MutableText listItem = addButton(dimString, name, waypoint);
                            listItem.append(text(" " + name).setStyle(DEFAULT_STYLE));
                            player.sendMessage(listItem);
                        }
                    }
                } else {
                    LOGGER.info("dimension not found, add new dimension");
                    WaypointServerMod.INSTANCE.addWaypointFileManager(dimString);
                    promptNoWaypointList(player, dimString);
                }
            }
        }
    }

    private static void promptNoWaypointList(ServerPlayerEntity player, String dimString) {
        MutableText feedback = text("No waypoint list available. Add a waypoint list first. ");
        feedback.append(addListButton(dimString,""));
        player.sendMessage(feedback);
    }
}
