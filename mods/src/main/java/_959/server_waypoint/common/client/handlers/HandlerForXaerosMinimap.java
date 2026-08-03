package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.util.NetworkHelper;
import _959.server_waypoint.common.network.payload.c2s.UploadChunkC2SPayload;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.network.upload.UploadedWaypointListChunk;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.TextHelper.getDimensionColor;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.*;
import static _959.server_waypoint.common.util.XaerosWaypointHelper.simpleWaypointToXaerosWaypoint;
import static _959.server_waypoint.common.util.XaerosWaypointHelper.xaerosWaypointToSimpleWaypoint;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;

/**
 * only runs XaerosMinimap related logic when receiving buffers
 * */
public class HandlerForXaerosMinimap implements BufferHandler {
    private static final int UPLOAD_WAYPOINTS_PER_LIST_CHUNK = 64;
    private static final int UPLOAD_LISTS_PER_PACKET = 4;

    public static void syncFromServerWaypointMod() {
        WaypointClientMod waypointClientMod = WaypointClientMod.getInstance();
        MinimapSession session = getMinimapSession();
        waypointClientMod.forEachWaypointFileManager((fileManager) ->
            addOrReplaceWaypointLists(session, getDimensionKey(fileManager.getDimensionName()), fileManager.getWaypointLists()));
        saveAllWorlds(session);
    }

    public static void uploadToServer(UploadRequestBuffer request) {
        if (!ClientConfig.isXaerosMinimapLoaded) {
            sendUploadResult(request, UploadStatus.XAERO_NOT_INSTALLED, List.of());
            return;
        }
        if (!WaypointClientMod.isXaerosMinimapReady) {
            sendUploadResult(request, UploadStatus.XAERO_NOT_READY, List.of());
            return;
        }

        try {
            MinimapSession session = getMinimapSession();
            if (session == null) {
                sendUploadResult(request, UploadStatus.XAERO_NOT_READY, List.of());
                return;
            }
            List<UploadedWaypointListChunk> uploadedLists = new ArrayList<>();
            for (String dimensionName : request.dimensionNames()) {
                ResourceKey<Level> dimensionKey = getDimensionKey(dimensionName);
                if (dimensionKey == null) {
                    continue;
                }
                MinimapWorld minimapWorld = getMinimapWorld(session, dimensionKey);
                if (minimapWorld == null) {
                    continue;
                }
                for (WaypointSet waypointSet : minimapWorld.getIterableWaypointSets()) {
                    if (request.listName() != null && !request.listName().equals(waypointSet.getName())) {
                        continue;
                    }
                    addWaypointSetChunks(request, dimensionName, waypointSet, uploadedLists);
                }
            }
            sendUploadResult(request, UploadStatus.SUCCESS, uploadedLists);
        } catch (Exception e) {
            LOGGER.warn("Failed to export Xaero's waypoints for upload", e);
            sendUploadResult(request, UploadStatus.FAILED, List.of());
        }
    }

    private static void addWaypointSetChunks(UploadRequestBuffer request, String dimensionName, WaypointSet waypointSet,
                                              List<UploadedWaypointListChunk> destination) {
        List<SimpleWaypoint> batch = new ArrayList<>(UPLOAD_WAYPOINTS_PER_LIST_CHUNK);
        boolean exportedWaypoint = false;
        for (Waypoint waypoint : waypointSet.getWaypoints()) {
            if (waypoint.getPurpose() != WaypointPurpose.NORMAL || waypoint.isTemporary() || waypoint.isDisabled()) {
                continue;
            }
            if (request.waypointName() != null && !request.waypointName().equals(waypoint.getName())) {
                continue;
            }
            batch.add(xaerosWaypointToSimpleWaypoint(waypoint));
            if (batch.size() == UPLOAD_WAYPOINTS_PER_LIST_CHUNK) {
                destination.add(new UploadedWaypointListChunk(dimensionName, waypointSet.getName(), batch));
                exportedWaypoint = true;
                batch = new ArrayList<>(UPLOAD_WAYPOINTS_PER_LIST_CHUNK);
            }
        }
        if (!batch.isEmpty()) {
            destination.add(new UploadedWaypointListChunk(dimensionName, waypointSet.getName(), batch));
        } else if (request.deleteMissing() && !exportedWaypoint) {
            // An empty chunk is a manifest entry: force-local-delete must be able to
            // distinguish an empty local set from a set that Xaero does not have.
            destination.add(new UploadedWaypointListChunk(dimensionName, waypointSet.getName(), List.of()));
        }
    }

    private static void sendUploadResult(UploadRequestBuffer request, UploadStatus status,
                                         List<UploadedWaypointListChunk> uploadedLists) {
        if (status != UploadStatus.SUCCESS) {
            NetworkHelper.sendPayloadToServer(new UploadChunkC2SPayload(
                    new UploadChunkBuffer(request.requestId(), 0, true, status, List.of())
            ));
            return;
        }
        if (uploadedLists.isEmpty()) {
            NetworkHelper.sendPayloadToServer(new UploadChunkC2SPayload(
                    new UploadChunkBuffer(request.requestId(), 0, true, status, List.of())
            ));
            return;
        }
        int sequence = 0;
        for (int from = 0; from < uploadedLists.size(); from += UPLOAD_LISTS_PER_PACKET) {
            int to = Math.min(from + UPLOAD_LISTS_PER_PACKET, uploadedLists.size());
            boolean finalChunk = to == uploadedLists.size();
            NetworkHelper.sendPayloadToServer(new UploadChunkC2SPayload(
                    new UploadChunkBuffer(request.requestId(), sequence++, finalChunk, status, uploadedLists.subList(from, to))
            ));
        }
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
        ResourceKey<Level> dimKey = getDimensionKey(dimensionName);
        Player player = Minecraft.getInstance().player;
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }
        WaypointList waypointList = buffer.waypointList();
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        replaceWaypointList(minimapWorld, waypointList);
        displayClientMessage(player, Component.translatable("server_waypoint.list.added.xaeros", waypointList.name()));
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    @Override
    public void onDimensionWaypoint(DimensionWaypointBuffer buffer) {
        String dimensionName = buffer.dimensionName();
        ResourceKey<Level> dimKey = getDimensionKey(dimensionName);
        Player player = Minecraft.getInstance().player;
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        for (WaypointList waypointList : buffer.waypointLists()) {
            if (waypointList.getSyncNum() == WaypointList.REMOVE_LIST) {
                minimapWorld.removeWaypointSet(waypointList.name());
            } else {
                replaceWaypointList(minimapWorld, waypointList);
            }
        }
        displayClientMessage(player, Component.translatable("server_waypoint.dimension.waypoint.added.xaeros", Component.literal(dimensionName).withStyle(getDimensionColor(dimensionName))));
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    @Override
    public void onWorldWaypoint(WorldWaypointBuffer buffer) {
        Player player = Minecraft.getInstance().player;
        MinimapSession session = getMinimapSession();
        for (DimensionWaypointBuffer dimensionWaypointBuffer : buffer) {
            addDimensionWaypoint(session, dimensionWaypointBuffer);
        }
        displayClientMessage(player, Component.translatable("server_waypoint.all.added.xaeros"));
        for (DimensionWaypointBuffer dimensionWaypointBuffer : buffer) {
            String dimensionName = dimensionWaypointBuffer.dimensionName();
            ResourceKey<Level> dimKey = getDimensionKey(dimensionName);
            if (dimKey == null) {
                warnInvalidDimension(player, dimensionName);
                continue;
            }
            try {
                saveMinimapWorld(session, dimKey);
            } catch (IOException e) {
                LOGGER.warn("Failed to save waypoints for dimension {}.", dimensionName, e);
                displayClientMessage(player, Component.translatable("server_waypoint.save.dimension.failed.xaeros", Component.literal(dimensionName).withStyle(getDimensionColor(dimensionName))));
            }
        }
    }

    @Override
    public void onWaypointModification(WaypointModificationBuffer buffer) {
        Player player = Minecraft.getInstance().player;
        String dimensionName = buffer.dimensionName();
        ResourceKey<Level> dimKey = getDimensionKey(dimensionName);
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
                displayClientMessage(player, Component.translatable("server_waypoint.modification.add.xaeros", toVanillaText(waypointTextWithTp(simpleWaypoint, dimensionName, listName))));
            }
            case REMOVE -> {
                String waypointName = buffer.waypointName();
                removeWaypointsByName(waypointSet, waypointName);
//                player.sendMessage(Text.translatable("waypoint.modification.remove", toVanillaText(waypointTextNoTp(simpleWaypoint, dimensionName))), false);
            }
            case UPDATE -> {
                SimpleWaypoint simpleWaypoint = buffer.waypoint();
                replaceWaypoint(waypointSet, simpleWaypointToXaerosWaypoint(simpleWaypoint));
                displayClientMessage(player, Component.translatable("server_waypoint.modification.update.xaeros", toVanillaText(waypointTextWithTp(simpleWaypoint, dimensionName, listName))));
            }
        }
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    protected void saveMinimapWorldWithFeedback(MinimapSession session, MinimapWorld minimapWorld, Player player) {
        try {
            saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            LOGGER.warn("Failed to save waypoints", e);
            displayClientMessage(player, Component.translatable("server_waypoint.save.failed.xaeros").withStyle(ChatFormatting.RED));
        }
    }

    protected void warnInvalidDimension(Player player, String dimensionName) {
        LOGGER.warn("Failed to decode dimension {}", dimensionName);
        displayClientMessage(player, Component.translatable("server_waypoint.dimension.decode.fail", Component.literal(dimensionName)));
    }

    private static void displayClientMessage(Player player, Component message) {
        //? if >=26
        player.sendSystemMessage(message);
        //? if <26
        /*player.displayClientMessage(message, false);*/
    }
}
