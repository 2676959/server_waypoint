package _959.server_waypoint.common.network;

import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.common.ServerWaypointClientMod;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import _959.server_waypoint.common.util.LocalEditionFileManager;
import _959.server_waypoint.common.util.XaeroMinimapHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.io.IOException;
import java.util.List;


//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///?}

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.*;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.TextHelper.DimensionColorHelper.getDimensionColor;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
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
        WaypointListBuffer waypointListBuffer = payload.waypointListBuffer();
        String dimString = waypointListBuffer.dimString();
        RegistryKey<World> dimKey = getDimensionKey(dimString);
        if (dimKey == null) {
            warnInvalidDimension(context.player(), dimString);
            return;
        }
        ServerWaypointClientMod.LOGGER.info("received waypoint list in {}", dimString);
        WaypointList waypointList = waypointListBuffer.waypointList();
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
        String dimString = dimensionWaypoint.dimString();
        RegistryKey<World> dimKey = getDimensionKey(dimString);
        if (dimKey == null) {
            warnInvalidDimension(context.player(), dimString);
            return;
        }
        ServerWaypointClientMod.LOGGER.info("received dimensionWaypoint in {}", dimString);
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
        WorldWaypointBuffer worldWaypointBuffer = payload.worldWaypointBuffer();
        List<DimensionWaypoint> dimensionWaypointList = worldWaypointBuffer.dimensionWaypoints();
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        for (DimensionWaypoint dimensionWaypoint : dimensionWaypointList) {
            XaeroMinimapHelper.addDimensionWaypoint(session, dimensionWaypoint);
        }
        context.player().sendMessage(Text.of("All waypoints on this server have been added to Xaero's minimap successfully."), false);
        for (DimensionWaypoint dimensionWaypoint : dimensionWaypointList) {
            String dimString = dimensionWaypoint.dimString();
            RegistryKey<World> dimKey = getDimensionKey(dimString);
            if (dimKey == null) {
                warnInvalidDimension(context.player(), dimString);
                continue;
            }
            try {
                XaeroMinimapHelper.saveMinimapWorld(session, dimKey);
            } catch (IOException e) {
                ServerWaypointClientMod.LOGGER.warn("Failed to save waypoints for dimension {}.", dimString, e);
                MutableText msg = text("Failed to save waypoints for dimension").append(text(" \"" + dimString + "\" ").formatted(getDimensionColor(dimKey)));
                msg.append(text("to file.").formatted(Formatting.WHITE));
                context.player().sendMessage(msg, false);
            }
        }
        LocalEditionFileManager.writeEdition(session, worldWaypointBuffer.edition());
    }

    public static void onWaypointModificationPayload(
            WaypointModificationS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        WaypointModificationBuffer waypointModification = payload.waypointModification();
        String dimString = waypointModification.dimString();
        RegistryKey<World> dimKey = getDimensionKey(dimString);
        if (dimKey == null) {
            warnInvalidDimension(context.player(), dimString);
            return;
        }
        ServerWaypointClientMod.LOGGER.info("Received waypoint modification: {} in dimension {} for list {}.",
                waypointModification.type(), dimKey, waypointModification.listName());
        
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        WaypointSet waypointSet = minimapWorld.getWaypointSet(waypointModification.listName());
        
        if (waypointSet == null) {
                waypointSet = WaypointSet.Builder.begin()
                .setName(waypointModification.listName())
                .build();
            ServerWaypointClientMod.LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.",
                    waypointModification.listName(), dimKey);
            minimapWorld.addWaypointSet(waypointSet);
        }

        SimpleWaypoint simpleWaypoint = waypointModification.waypoint();
        switch (waypointModification.type()) {
            case ADD -> {
                waypointSet.add(simpleWaypointToWaypoint(waypointModification.waypoint()));
                context.player().sendMessage(text("Waypoint ")
                .append(simpleWaypointToFormattedText(simpleWaypoint, tpCmd(dimString, simpleWaypoint.pos(), simpleWaypoint.yaw()), waypointInfoText(dimString, simpleWaypoint))
                        .append(text(" has been added to Xaero's minimap.").setStyle(DEFAULT_STYLE))), false);

            }
            case REMOVE -> {
                removeWaypointsByName(waypointSet, simpleWaypoint.name());
                context.player().sendMessage(text("Waypoint ")
                .append(simpleWaypointToFormattedText(simpleWaypoint, tpCmd(dimString, simpleWaypoint.pos(), simpleWaypoint.yaw()), waypointInfoText(dimString, simpleWaypoint))
                        .append(text(" has been removed from Xaero's minimap.").setStyle(DEFAULT_STYLE))), false);
            }
            case UPDATE -> {
                XaeroMinimapHelper.replaceWaypoint(waypointSet, simpleWaypointToWaypoint(waypointModification.waypoint()));
                context.player().sendMessage(text("Waypoint ")
                .append(simpleWaypointToFormattedText(simpleWaypoint, tpCmd(dimString, simpleWaypoint.pos(), simpleWaypoint.yaw()), waypointInfoText(dimString, simpleWaypoint))
                    .append(text(" has been updated on Xaero's minimap.").setStyle(DEFAULT_STYLE))), false);
            }
        }

        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            ServerWaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.literal("Failed to save waypoints to file.").formatted(Formatting.RED), false);
        }
        LocalEditionFileManager.writeEdition(session, waypointModification.edition());
    }

    private static void warnInvalidDimension(PlayerEntity player, String dimString) {
        ServerWaypointClientMod.LOGGER.warn("Failed to decode dimension {}", dimString);
        player.sendMessage(Text.of("Failed to decode dimension " + dimString), false);
    }

}
