package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.util.NetworkHelper;
import _959.server_waypoint.common.network.payload.c2s.UploadChunkC2SPayload;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.network.upload.UploadedWaypointListChunk;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.common.util.XaerosWaypointHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.common.minimap.waypoints.Waypoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.TextHelper.getDimensionColor;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.*;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;

public final class XaerosMinimapWaypointHelper {
    private static final int UPLOAD_WAYPOINTS_PER_LIST_CHUNK = 64;
    private static final int UPLOAD_LISTS_PER_PACKET = 4;

    private XaerosMinimapWaypointHelper() {
    }

    public static void replaceAll(WaypointClientMod waypointClientMod) {
        MinimapSession session = getMinimapSession();
        waypointClientMod.forEachWaypointFileManager((fileManager) ->
                replaceDimension(session, fileManager));
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
                    String listName = SyncedWaypointName.parseSyncedName(waypointSet.getName());
                    if (listName == null) {
                        listName = waypointSet.getName();
                    }
                    if (request.listName() != null && !request.listName().equals(listName)) {
                        continue;
                    }
                    addWaypointSetChunks(request, dimensionName, listName, waypointSet, uploadedLists);
                }
            }
            sendUploadResult(request, UploadStatus.SUCCESS, uploadedLists);
        } catch (Exception exception) {
            LOGGER.warn("Failed to export Xaero's waypoints for upload", exception);
            sendUploadResult(request, UploadStatus.FAILED, List.of());
        }
    }

    private static void addWaypointSetChunks(
            UploadRequestBuffer request,
            String dimensionName,
            String listName,
            WaypointSet waypointSet,
            List<UploadedWaypointListChunk> destination
    ) {
        List<SimpleWaypoint> batch = new ArrayList<>(UPLOAD_WAYPOINTS_PER_LIST_CHUNK);
        boolean exportedWaypoint = false;
        for (Waypoint waypoint : waypointSet.getWaypoints()) {
            if (waypoint.getPurpose() != WaypointPurpose.NORMAL || waypoint.isTemporary() || waypoint.isDisabled()) {
                continue;
            }
            String waypointName = SyncedWaypointName.parseSyncedName(waypoint.getName());
            if (waypointName == null) {
                waypointName = waypoint.getName();
            }
            if (request.waypointName() != null && !request.waypointName().equals(waypointName)) {
                continue;
            }
            SimpleWaypoint simpleWaypoint = XaerosWaypointHelper.xaerosWaypointToSimpleWaypoint(waypoint);
            if (!waypointName.equals(simpleWaypoint.name())) {
                simpleWaypoint = new SimpleWaypoint(
                        waypointName,
                        simpleWaypoint.initials(),
                        simpleWaypoint.pos(),
                        simpleWaypoint.rgb(),
                        simpleWaypoint.yaw(),
                        simpleWaypoint.global()
                );
            }
            batch.add(simpleWaypoint);
            if (batch.size() == UPLOAD_WAYPOINTS_PER_LIST_CHUNK) {
                destination.add(new UploadedWaypointListChunk(dimensionName, listName, batch));
                exportedWaypoint = true;
                batch = new ArrayList<>(UPLOAD_WAYPOINTS_PER_LIST_CHUNK);
            }
        }
        if (!batch.isEmpty()) {
            destination.add(new UploadedWaypointListChunk(dimensionName, listName, batch));
        } else if (request.deleteMissing() && !exportedWaypoint) {
            destination.add(new UploadedWaypointListChunk(dimensionName, listName, List.of()));
        }
    }

    private static void sendUploadResult(
            UploadRequestBuffer request,
            UploadStatus status,
            List<UploadedWaypointListChunk> uploadedLists
    ) {
        if (status != UploadStatus.SUCCESS || uploadedLists.isEmpty()) {
            NetworkHelper.sendPayloadToServer(new UploadChunkC2SPayload(
                    new UploadChunkBuffer(request.requestId(), 0, true, status, List.of())
            ));
            return;
        }
        int sequence = 0;
        for (int from = 0; from < uploadedLists.size(); from += UPLOAD_LISTS_PER_PACKET) {
            int to = Math.min(from + UPLOAD_LISTS_PER_PACKET, uploadedLists.size());
            NetworkHelper.sendPayloadToServer(new UploadChunkC2SPayload(
                    new UploadChunkBuffer(
                            request.requestId(),
                            sequence++,
                            to == uploadedLists.size(),
                            status,
                            uploadedLists.subList(from, to)
                    )
            ));
        }
    }

    public static void replaceList(String dimensionName, WaypointList waypointList) {
        ResourceKey<Level> dimKey = getValidDimensionKey(dimensionName);
        Player player = Minecraft.getInstance().player;
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        replaceWaypointList(minimapWorld, waypointList);
        displayClientMessage(player, Component.translatable("server_waypoint.list.added.xaeros", waypointList.name()));
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    public static void replaceDimension(String dimensionName, List<WaypointList> waypointLists) {
        ResourceKey<Level> dimKey = getValidDimensionKey(dimensionName);
        Player player = Minecraft.getInstance().player;
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        replaceWaypointLists(minimapWorld, waypointLists);
        displayClientMessage(player, Component.translatable("server_waypoint.dimension.waypoint.added.xaeros", Component.literal(dimensionName).withStyle(getDimensionColor(dimensionName))));
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    public static void applyModification(String dimensionName, String listName, WaypointModificationType type, SimpleWaypoint waypoint, String waypointName) {
        Player player = Minecraft.getInstance().player;
        ResourceKey<Level> dimKey = getValidDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(player, dimensionName);
            return;
        }

        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        String syncedListName = SyncedWaypointName.formatSyncedName(listName);
        if (syncedListName == null) {
            LOGGER.warn("Skipping Xaero's Minimap sync for list {} because its generated name would be ambiguous.", listName);
            return;
        }
        WaypointSet waypointSet = minimapWorld.getWaypointSet(syncedListName);

        if (waypointSet == null && (type == WaypointModificationType.ADD || type == WaypointModificationType.UPDATE || type == WaypointModificationType.ADD_LIST)) {
            waypointSet = WaypointSet.Builder.begin()
                    .setName(syncedListName)
                    .build();
            LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.", listName, dimKey);
            minimapWorld.addWaypointSet(waypointSet);
        }

        switch (type) {
            case ADD -> {
                if (waypoint == null) {
                    return;
                }
                replaceSyncedWaypoint(waypointSet, listName, waypoint);
                displayClientMessage(player, Component.translatable("server_waypoint.modification.add.xaeros", toVanillaText(waypointTextWithTp(waypoint, dimensionName, listName))));
            }
            case REMOVE -> {
                if (waypointSet == null) {
                    return;
                }
                removeSyncedWaypoint(waypointSet, waypointName);
            }
            case UPDATE -> {
                if (waypoint == null) {
                    return;
                }
                if (waypointName != null && !waypointName.equals(waypoint.name())) {
                    removeSyncedWaypoint(waypointSet, waypointName);
                }
                replaceSyncedWaypoint(waypointSet, listName, waypoint);
                displayClientMessage(player, Component.translatable("server_waypoint.modification.update.xaeros", toVanillaText(waypointTextWithTp(waypoint, dimensionName, listName))));
            }
            case ADD_LIST -> {
            }
            case REMOVE_LIST -> {
                removeSyncedWaypointSet(minimapWorld, syncedListName);
            }
        }
        saveMinimapWorldWithFeedback(session, minimapWorld, player);
    }

    private static void replaceDimension(MinimapSession session, WaypointFileManager fileManager) {
        ResourceKey<Level> dimKey = getValidDimensionKey(fileManager.getDimensionName());
        if (dimKey == null) {
            warnInvalidDimension(Minecraft.getInstance().player, fileManager.getDimensionName());
            return;
        }
        addOrReplaceWaypointLists(session, dimKey, fileManager.getWaypointLists());
    }

    private static ResourceKey<Level> getValidDimensionKey(String dimensionName) {
        return getDimensionKey(dimensionName);
    }

    private static void saveMinimapWorldWithFeedback(MinimapSession session, MinimapWorld minimapWorld, Player player) {
        try {
            saveMinimapWorld(session, minimapWorld);
        } catch (IOException e) {
            LOGGER.warn("Failed to save waypoints", e);
            displayClientMessage(player, Component.translatable("server_waypoint.save.failed.xaeros").withStyle(ChatFormatting.RED));
        }
    }

    private static void warnInvalidDimension(Player player, String dimensionName) {
        LOGGER.warn("Failed to decode dimension {}", dimensionName);
        displayClientMessage(player, Component.translatable("server_waypoint.dimension.decode.fail", Component.literal(dimensionName)));
    }

    private static void displayClientMessage(Player player, Component message) {
        if (player == null) {
            return;
        }
        //? if >=26
        player.sendSystemMessage(message);
        //? if <26
        /*player.displayClientMessage(message, false);*/
    }
}
