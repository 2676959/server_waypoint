package _959.server_waypoint.common.network;

import _959.server_waypoint.common.ServerWaypointClientMod;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.common.network.waypoint.WorldWaypoint;
import _959.server_waypoint.common.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.common.server.waypoint.WaypointList;
import _959.server_waypoint.common.util.LocalEditionFileManager;
import _959.server_waypoint.common.util.XaeroMinimapHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.io.IOException;


//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///?}

import static _959.server_waypoint.common.util.SimpleWaypointHelper.*;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.TextHelper.DimensionColorHelper.getDimensionColor;
import static _959.server_waypoint.common.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.common.util.TextHelper.waypointInfoText;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.removeWaypointsByName;

public class ServerWaypointPayloadHandler {
    public static void onWaypointListPayload(
            WaypointListS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        RegistryKey<World> dimKey = payload.dimKey();
        ServerWaypointClientMod.LOGGER.info("received waypoint list in {}", dimKey.getValue().toString());
        WaypointList waypointList = payload.waypointList();
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        XaeroMinimapHelper.replaceWaypointList(minimapWorld, waypointList);
        context.player().sendMessage(Text.of("Waypoint list \"%s\" has been added to Xaero's minimap successfully.".formatted(waypointList.name())), false);
        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            ServerWaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.literal("Failed to save waypoints to file.").formatted(Formatting.RED), false);
        }
    }

    public static void onDimensionWaypointPayload(
            DimensionWaypointS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        DimensionWaypoint dimensionWaypoint = payload.dimensionWaypoint();
        RegistryKey<World> dimKey = dimensionWaypoint.dimKey();
        ServerWaypointClientMod.LOGGER.info("received dimensionWaypoint in {}", dimKey.getValue().toString());
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        XaeroMinimapHelper.replaceWaypointLists(minimapWorld, dimensionWaypoint.waypointLists());
        MutableText msg = text("Waypoints in dimension").append(text(" \"" + dimKey.getValue().toString() + "\" ").formatted(getDimensionColor(dimKey)));
        msg.append(text("has been added to Xaero's minimap successfully.").formatted(Formatting.WHITE));
        context.player().sendMessage(msg, false);
        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            ServerWaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.literal("Failed to save waypoints to file.").formatted(Formatting.RED), false);
        }
    }

    public static void onWorldWaypointPayload(
            WorldWaypointS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        ServerWaypointClientMod.LOGGER.info("received worldWaypoint");
        WorldWaypoint worldWaypoint = payload.worldWaypoint();
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        for (DimensionWaypoint dimensionWaypoint : worldWaypoint.dimensionWaypoints()) {
            XaeroMinimapHelper.addDimensionWaypoint(session, dimensionWaypoint);
        }
        context.player().sendMessage(Text.of("All waypoints on this server have been added to Xaero's minimap successfully."), false);
        for (DimensionWaypoint dimensionWaypoint : worldWaypoint.dimensionWaypoints()) {
            try {
                XaeroMinimapHelper.saveMinimapWorld(session, dimensionWaypoint.dimKey());
            } catch (IOException e) {
                RegistryKey<World> dimKey = dimensionWaypoint.dimKey();
                ServerWaypointClientMod.LOGGER.warn("Failed to save waypoints for dimension {}.", dimKey.getValue().toString(), e);
                MutableText msg = text("Failed to save waypoints for dimension").append(text(" \"" + dimKey.getValue().toString() + "\" ").formatted(getDimensionColor(dimKey)));
                msg.append(text("to file.").formatted(Formatting.WHITE));
                context.player().sendMessage(msg, false);
            }
        }
        LocalEditionFileManager.writeEdition(session, payload.edition());
    }

    public static void onWaypointModificationPayload(
            WaypointModificationS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        RegistryKey<World> dimKey = payload.dimKey();
        ServerWaypointClientMod.LOGGER.info("Received waypoint modification: {} in dimension {} for list {}.",
            payload.type(), dimKey.getValue().toString(), payload.listName());
        
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        WaypointSet waypointSet = minimapWorld.getWaypointSet(payload.listName());
        
        if (waypointSet == null) {
                waypointSet = WaypointSet.Builder.begin()
                .setName(payload.listName())
                .build();
            ServerWaypointClientMod.LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.",
                payload.listName(), dimKey.getValue().toString());
            minimapWorld.addWaypointSet(waypointSet);
        }

        SimpleWaypoint simpleWaypoint = payload.waypoint();
        switch (payload.type()) {
            case ADD -> {
                waypointSet.add(simpleWaypointToWaypoint(payload.waypoint()));
                context.player().sendMessage(text("Waypoint ")
                .append(simpleWaypointToFormattedText(simpleWaypoint, tpCmd(dimKey, simpleWaypoint.pos(), simpleWaypoint.yaw()), waypointInfoText(dimKey, simpleWaypoint))
                        .append(text(" has been added to Xaero's minimap.").setStyle(DEFAULT_STYLE))), false);

            }
            case REMOVE -> {
                removeWaypointsByName(waypointSet, simpleWaypoint.name());
                context.player().sendMessage(text("Waypoint ")
                .append(simpleWaypointToFormattedText(simpleWaypoint, tpCmd(dimKey, simpleWaypoint.pos(), simpleWaypoint.yaw()), waypointInfoText(dimKey, simpleWaypoint))
                        .append(text(" has been removed from Xaero's minimap.").setStyle(DEFAULT_STYLE))), false);
            }
            case UPDATE -> {
                XaeroMinimapHelper.replaceWaypoint(waypointSet, simpleWaypointToWaypoint(payload.waypoint()));
                context.player().sendMessage(text("Waypoint ")
                .append(simpleWaypointToFormattedText(simpleWaypoint, tpCmd(dimKey, simpleWaypoint.pos(), simpleWaypoint.yaw()), waypointInfoText(dimKey, simpleWaypoint))
                    .append(text(" has been updated on Xaero's minimap.").setStyle(DEFAULT_STYLE))), false);
            }
        }

        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            ServerWaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.literal("Failed to save waypoints to file.").formatted(Formatting.RED), false);
        }
        LocalEditionFileManager.writeEdition(session, payload.edition());
    }



}
