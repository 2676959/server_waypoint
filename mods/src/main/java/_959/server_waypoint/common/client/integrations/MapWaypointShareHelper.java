package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.util.WaypointInitials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Prompts only the local sender after the map mod or chat screen finishes its own close action. */
public final class MapWaypointShareHelper {
    private MapWaypointShareHelper() {
    }

    public static void onOutgoingMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || WaypointClientMod.getNetworkState() != WaypointClientMod.ClientNetworkState.SYNC_FINISHED
                || (!ClientConfig.isXaerosMinimapLoaded && !ClientConfig.isVoxelMapLoaded)) {
            return;
        }
        SharedMapWaypoint share = SharedMapWaypoint.parse(message, WaypointClientMod.getCurrentDimensionName(),
                player.blockPosition().getY());
        if (share == null || share.isOnServer(WaypointClientMod.getInstance())) {
            return;
        }
        minecraft.
                //? if >1.21
                schedule
                //? if <=1.21
                /*tell*/
                (() -> {
            if (minecraft.player != player
                    || WaypointClientMod.getNetworkState() != WaypointClientMod.ClientNetworkState.SYNC_FINISHED
                    || share.isOnServer(WaypointClientMod.getInstance())) {
                return;
            }
            //? if >=26.2 {
            /*Screen parent = minecraft.gui.screen();
            *///?} else {
            Screen parent = minecraft.screen;
            //?}
            var encoded = SyncedWaypointName.parse(share.name());
            String decoded = SyncedWaypointName.parseSyncedName(share.name());
            String name = encoded != null ? encoded.waypointName() : decoded != null ? decoded : share.name();
            String list = encoded == null ? "" : encoded.listName();
            MinecraftClientHelper.setScreen(minecraft, new ConfirmScreen(confirmed -> {
                if (minecraft.player != player) {
                    return;
                }
                if (!confirmed || share.isOnServer(WaypointClientMod.getInstance())) {
                    MinecraftClientHelper.setScreen(minecraft, parent);
                    return;
                }
                var icon = share.icon() != null && WaypointIconPolicy.voxelMapSuffixes()
                        .contains(share.icon().substring("voxelmap:".length())) ? NamespacedId.parse(share.icon()) : null;
                SimpleWaypoint defaults = new SimpleWaypoint(name, name,
                        share.initials().isEmpty() ? WaypointInitials.getDefaultInitials(name) : share.initials(),
                        new WaypointPos(share.x(), share.y(), share.z()), share.rgb(), share.yaw(), false,
                        List.of(), "", icon);
                MinecraftClientHelper.setScreen(minecraft,
                        WaypointAddScreen.fromWaypoint(parent, share.dimensionName(), list, defaults));
            }, Component.translatable("server_waypoint.map.add_to_server"),
                    Component.translatable("server_waypoint.map.share.add_prompt", name)));
        });
    }
}
