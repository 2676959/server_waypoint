package _959.server_waypoint.common.network;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.common.util.XaeroMinimapHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
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

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextNoTp;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.common.util.TextHelper.getDimensionColor;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.XaerosWaypointHelper.*;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.replaceWaypoint;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.removeWaypointsByName;

public class ServerWaypointPayloadHandler {
    public static void onServerHandshake(
            ServerHandshakeS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
             *///?}
    ) {
        LOGGER.info("received server handshake packet: {}", payload.toString());
        ServerHandshakeBuffer serverHandshakeBuffer = payload.serverHandshakeBuffer();
        WaypointClientMod.getInstance().requestUpdates(serverHandshakeBuffer.serverId());
    }

    public static void onUpdatesBundle(
            UpdatesBundleS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
             *///?}
    ) {
        UpdatesBundleBuffer updatesBundle = payload.updatesBundleBuffer();
        LOGGER.info("received updates bundle: {}", updatesBundle);
        WaypointClientMod waypointClient = WaypointClientMod.getInstance();
        for (DimensionWaypointBuffer dimensionBuffer : updatesBundle) {
            String dimensionName = dimensionBuffer.dimensionName();
            WaypointFileManager fileManager = waypointClient.getWaypointFileManager(dimensionName);
            List<WaypointList> listsUpdates = dimensionBuffer.waypointLists();
            if (listsUpdates.isEmpty()) {
                // remove dimension
                waypointClient.removeDimension(dimensionName);
            } else {
                // update dimension
                if (fileManager == null) {
                    fileManager = waypointClient.addWaypointListManager(dimensionName);
                    fileManager.addWaypointLists(listsUpdates);
                } else {
                    for (WaypointList listOnServer : listsUpdates) {
                        String listName = listOnServer.name();
                        WaypointList listOnClient = fileManager.getWaypointListByName(listName);
                        if (listOnServer.getSyncNum() == WaypointList.REMOVE_LIST) {
                            // remove list
                            if (listOnClient != null) {
                                fileManager.removeWaypointListByName(listName);
                            }
                        } else {
                            // replace list
                            fileManager.addWaypointList(listOnServer);
                        }
                    }
                }
                try {
                    fileManager.saveDimension();
                } catch (IOException e) {
                    LOGGER.info("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
                }
            }
        }
        waypointClient.setHandshakeFinished(true);
    }

    public static void onWaypointListPayload(
            WaypointListS2CPayload payload,
            //? if fabric {
            ClientPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        WaypointListBuffer waypointListBuffer = payload.waypointListBuffer();
        String dimensionName = waypointListBuffer.dimensionName();
        RegistryKey<World> dimKey = getDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(context.player(), dimensionName);
            return;
        }
        WaypointClientMod.LOGGER.info("received waypoint list in {}", dimensionName);
        WaypointList waypointList = waypointListBuffer.waypointList();
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        XaeroMinimapHelper.replaceWaypointList(minimapWorld, waypointList);
        context.player().sendMessage(Text.translatable("waypoint.list.added", waypointList.name()), false);
        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            WaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.translatable("waypoint.save.failed").formatted(Formatting.RED), false);
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
        DimensionWaypointBuffer dimensionWaypointBuffer = payload.dimensionWaypointBuffer();
        String dimensionName = dimensionWaypointBuffer.dimensionName();
        RegistryKey<World> dimKey = getDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(context.player(), dimensionName);
            return;
        }
        WaypointClientMod.LOGGER.info("received dimensionWaypoint in {}", dimensionName);
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        XaeroMinimapHelper.replaceWaypointLists(minimapWorld, dimensionWaypointBuffer.waypointLists());
        context.player().sendMessage(Text.translatable("waypoint.dimension.waypoint.added", Text.literal(dimensionName).formatted(getDimensionColor(dimensionName))), false);
        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            WaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.translatable("waypoint.save.failed").formatted(Formatting.RED), false);
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
        WaypointClientMod.LOGGER.info("received worldWaypoint");
        WorldWaypointBuffer worldWaypointBuffer = payload.worldWaypointBuffer();
        if (WaypointServerMod.isDedicated) {
            WaypointClientMod.getInstance().onWorldWaypointPayload(worldWaypointBuffer);
        }
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        for (DimensionWaypointBuffer dimensionWaypointBuffer : worldWaypointBuffer) {
            XaeroMinimapHelper.addDimensionWaypoint(session, dimensionWaypointBuffer);
        }
        context.player().sendMessage(Text.translatable("waypoint.all.added"), false);
        for (DimensionWaypointBuffer dimensionWaypointBuffer : worldWaypointBuffer) {
            String dimensionName = dimensionWaypointBuffer.dimensionName();
            RegistryKey<World> dimKey = getDimensionKey(dimensionName);
            if (dimKey == null) {
                warnInvalidDimension(context.player(), dimensionName);
                continue;
            }
            try {
                XaeroMinimapHelper.saveMinimapWorld(session, dimKey);
            } catch (IOException e) {
                WaypointClientMod.LOGGER.warn("Failed to save waypoints for dimension {}.", dimensionName, e);
                context.player().sendMessage(Text.translatable("waypoint.save.dimension.failed", Text.literal(dimensionName).formatted(getDimensionColor(dimensionName))), false);
            }
        }
//        LocalEditionFileManager.writeEdition(session, worldWaypointBuffer.edition());
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
        if (WaypointServerMod.isDedicated) {
            WaypointClientMod.getInstance().onWaypointModificationPayload(waypointModification);
        }
        String dimensionName = waypointModification.dimensionName();
        RegistryKey<World> dimKey = getDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(context.player(), dimensionName);
            return;
        }
        WaypointClientMod.LOGGER.info("Received waypoint modification: {} in dimension {} for list {}.",
                waypointModification.type(), dimKey, waypointModification.listName());
        
        MinimapSession session = XaeroMinimapHelper.getMinimapSession();
        MinimapWorld minimapWorld = XaeroMinimapHelper.getMinimapWorld(session, dimKey);
        WaypointSet waypointSet = minimapWorld.getWaypointSet(waypointModification.listName());
        
        if (waypointSet == null) {
                waypointSet = WaypointSet.Builder.begin()
                .setName(waypointModification.listName())
                .build();
            WaypointClientMod.LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.",
                    waypointModification.listName(), dimKey);
            minimapWorld.addWaypointSet(waypointSet);
        }

        SimpleWaypoint simpleWaypoint = waypointModification.waypoint();
        String listName = waypointModification.listName();
        switch (waypointModification.type()) {
            case ADD -> {
                waypointSet.add(simpleWaypointToXaerosWaypoint(simpleWaypoint));
                context.player().sendMessage(Text.translatable("waypoint.modification.add", toVanillaText(waypointTextWithTp(simpleWaypoint, dimensionName, listName))), false);
            }
            case REMOVE -> {
                removeWaypointsByName(waypointSet, simpleWaypoint.name());
                context.player().sendMessage(Text.translatable("waypoint.modification.remove", toVanillaText(waypointTextNoTp(simpleWaypoint, dimensionName))), false);
            }
            case UPDATE -> {
                replaceWaypoint(waypointSet, simpleWaypointToXaerosWaypoint(simpleWaypoint));
                context.player().sendMessage(Text.translatable("waypoint.modification.update", toVanillaText(waypointTextWithTp(simpleWaypoint, dimensionName, listName))), false);
            }
        }

        try {
            XaeroMinimapHelper.saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            WaypointClientMod.LOGGER.warn("Failed to save waypoints", e);
            context.player().sendMessage(Text.translatable("waypoint.save.failed").formatted(Formatting.RED), false);
        }
    }

    private static void warnInvalidDimension(PlayerEntity player, String dimensionName) {
        WaypointClientMod.LOGGER.warn("Failed to decode dimension {}", dimensionName);
        player.sendMessage(Text.translatable("waypoint.dimension.decode.fail", Text.literal(dimensionName)), false);
    }

}
