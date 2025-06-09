package _959.server_waypoint.network;

import _959.server_waypoint.ServerWaypointClient;
import _959.server_waypoint.network.payload.DimensionWaypointS2CPayload;
import _959.server_waypoint.network.payload.WaypointListS2CPayload;
import _959.server_waypoint.network.payload.WorldWaypointS2CPayload;
import _959.server_waypoint.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.network.waypoint.WorldWaypoint;
import _959.server_waypoint.server.waypoint.WaypointList;
import _959.server_waypoint.util.XaeroMinimapHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapWorld;

import java.io.IOException;

import static _959.server_waypoint.util.TextHelper.text;
import static _959.server_waypoint.util.DimensionColorHelper.getDimensionColor;

public class ServerWaypointPayloadHandler {

    public static void onWaypointListPayload(WaypointListS2CPayload payload, ClientPlayNetworking.Context context) {
        RegistryKey<World> dimKey = payload.dimKey();
        ServerWaypointClient.LOGGER.info("received waypoint list in {}", dimKey.getValue().toString());
        WaypointList waypointList = payload.waypointList();
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        XaeroMinimapHelper.addWaypointList(minimapWorld, waypointList);
        context.player().sendMessage(Text.of("Waypoint set \"%s\" has been added to Xaero's minimap successfully.".formatted(waypointList.name())), false);
        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            ServerWaypointClient.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.literal("Failed to save waypoints to file.").formatted(Formatting.RED), false);
        }
    }

    public static void onDimensionWaypointPayload(DimensionWaypointS2CPayload payload, ClientPlayNetworking.Context context) {
        DimensionWaypoint dimensionWaypoint = payload.dimensionWaypoint();
        RegistryKey<World> dimKey = dimensionWaypoint.dimKey();
        ServerWaypointClient.LOGGER.info("received dimensionWaypoint in {}", dimKey.getValue().toString());
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        XaeroMinimapHelper.addWaypointLists(minimapWorld, dimensionWaypoint.waypointLists());
        MutableText msg = text("Waypoints in dimension").append(text(" \"" + dimKey.getValue().toString() + "\" ").formatted(getDimensionColor(dimKey)));
        msg.append(text("has been added to Xaero's minimap successfully.").formatted(Formatting.WHITE));
        context.player().sendMessage(msg, false);
        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            ServerWaypointClient.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.literal("Failed to save waypoints to file.").formatted(Formatting.RED), false);
        }
    }

    public static void onWorldWaypointPayload(WorldWaypointS2CPayload payload, ClientPlayNetworking.Context context) {
        ServerWaypointClient.LOGGER.info("received worldWaypoint");
        WorldWaypoint worldWaypoint = payload.worldWaypoint();
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        for (DimensionWaypoint dimensionWaypoint : worldWaypoint.dimensionWaypoints()) {
            XaeroMinimapHelper.addDimensionWaypoint(session, dimensionWaypoint);
        }
        context.player().sendMessage(Text.of("All waypoints on this server have been added to Xaero's minimap successfully."), false);
        for (DimensionWaypoint dimensionWaypoint : worldWaypoint.dimensionWaypoints()) {
            try {
                XaeroMinimapHelper.saveMinimapWorld(session, dimensionWaypoint.dimKey());
            } catch (IOException e) {
                RegistryKey<World> dimKey = dimensionWaypoint.dimKey();
                ServerWaypointClient.LOGGER.warn("Failed to save waypoints for dimension {}", dimKey.getValue().toString(), e);
                MutableText msg = text("Failed to save waypoints for dimension").append(text(" \"" + dimKey.getValue().toString() + "\" ").formatted(getDimensionColor(dimKey)));
                msg.append(text("to file.").formatted(Formatting.WHITE));
                context.player().sendMessage(msg, false);
            }
        }
    }

}
