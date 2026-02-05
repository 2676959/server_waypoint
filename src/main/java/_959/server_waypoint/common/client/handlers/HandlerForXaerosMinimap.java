package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.io.IOException;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.TextHelper.getDimensionColor;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.*;
import static _959.server_waypoint.common.util.XaerosWaypointHelper.simpleWaypointToXaerosWaypoint;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;

/**
 * only runs XaerosMinimap related logic when receiving buffers
 * */
public class HandlerForXaerosMinimap implements BufferHandler {
    public static void syncFromServerWaypointMod() {
        WaypointClientMod waypointClientMod = WaypointClientMod.getInstance();
        MinimapSession session = getMinimapSession();
        waypointClientMod.forEachWaypointFileManager((fileManager) ->
            addOrReplaceWaypointLists(session, getDimensionKey(fileManager.getDimensionName()), fileManager.getWaypointLists()));
        saveAllWorlds(session);
    }

    @Override
    public void onServerHandshake(ServerHandshakeBuffer buffer) {

    }

    @Override
    public void onUpdatesBundle(UpdatesBundleBuffer buffer) {

    }

    @Override
    public void onWaypointList(WaypointListBuffer buffer) {
        String dimensionName = buffer.dimensionName();
        RegistryKey<World> dimKey = getDimensionKey(dimensionName);
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }
        WaypointList waypointList = buffer.waypointList();
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        replaceWaypointList(minimapWorld, waypointList);
        player.sendMessage(Text.translatable("server_waypoint.list.added.xaeros", waypointList.name()), false);
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    @Override
    public void onDimensionWaypoint(DimensionWaypointBuffer buffer) {
        String dimensionName = buffer.dimensionName();
        RegistryKey<World> dimKey = getDimensionKey(dimensionName);
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        replaceWaypointLists(minimapWorld, buffer.waypointLists());
        player.sendMessage(Text.translatable("server_waypoint.dimension.waypoint.added.xaeros", Text.literal(dimensionName).formatted(getDimensionColor(dimensionName))), false);
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    @Override
    public void onWorldWaypoint(WorldWaypointBuffer buffer) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        MinimapSession session = getMinimapSession();
        for (DimensionWaypointBuffer dimensionWaypointBuffer : buffer) {
            addDimensionWaypoint(session, dimensionWaypointBuffer);
        }
        player.sendMessage(Text.translatable("server_waypoint.all.added.xaeros"), false);
        for (DimensionWaypointBuffer dimensionWaypointBuffer : buffer) {
            String dimensionName = dimensionWaypointBuffer.dimensionName();
            RegistryKey<World> dimKey = getDimensionKey(dimensionName);
            if (dimKey == null) {
                warnInvalidDimension(player, dimensionName);
                continue;
            }
            try {
                saveMinimapWorld(session, dimKey);
            } catch (IOException e) {
                LOGGER.warn("Failed to save waypoints for dimension {}.", dimensionName, e);
                player.sendMessage(Text.translatable("server_waypoint.save.dimension.failed.xaeros", Text.literal(dimensionName).formatted(getDimensionColor(dimensionName))), false);
            }
        }
    }

    @Override
    public void onWaypointModification(WaypointModificationBuffer buffer) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        String dimensionName = buffer.dimensionName();
        RegistryKey<World> dimKey = getDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }

        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        WaypointSet waypointSet = minimapWorld.getWaypointSet(buffer.listName());

        if (waypointSet == null) {
            waypointSet = WaypointSet.Builder.begin()
                    .setName(buffer.listName())
                    .build();
            LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.",
                    buffer.listName(), dimKey);
            minimapWorld.addWaypointSet(waypointSet);
        }

        String listName = buffer.listName();
        switch (buffer.type()) {
            case ADD -> {
                SimpleWaypoint simpleWaypoint = buffer.waypoint();
                waypointSet.add(simpleWaypointToXaerosWaypoint(simpleWaypoint));
                player.sendMessage(Text.translatable("server_waypoint.modification.add.xaeros", toVanillaText(waypointTextWithTp(simpleWaypoint, dimensionName, listName))), false);
            }
            case REMOVE -> {
                String waypointName = buffer.waypointName();
                removeWaypointsByName(waypointSet, waypointName);
//                player.sendMessage(Text.translatable("waypoint.modification.remove", toVanillaText(waypointTextNoTp(simpleWaypoint, dimensionName))), false);
            }
            case UPDATE -> {
                SimpleWaypoint simpleWaypoint = buffer.waypoint();
                replaceWaypoint(waypointSet, simpleWaypointToXaerosWaypoint(simpleWaypoint));
                player.sendMessage(Text.translatable("server_waypoint.modification.update.xaeros", toVanillaText(waypointTextWithTp(simpleWaypoint, dimensionName, listName))), false);
            }
        }
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    protected void saveMinimapWorldWithFeedback(MinimapSession session, MinimapWorld minimapWorld, PlayerEntity player) {
        try {
            saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            LOGGER.warn("Failed to save waypoints", e);
            player.sendMessage(Text.translatable("server_waypoint.save.failed.xaeros").formatted(Formatting.RED), false);
        }
    }

    protected void warnInvalidDimension(PlayerEntity player, String dimensionName) {
        LOGGER.warn("Failed to decode dimension {}", dimensionName);
        player.sendMessage(Text.translatable("server_waypoint.dimension.decode.fail", Text.literal(dimensionName)), false);
    }
}
