package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;
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
import static _959.server_waypoint.common.client.integrations.MapModChatHelper.displayClientMessage;
import static _959.server_waypoint.common.client.integrations.MapModChatHelper.waypointText;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.common.util.DimensionKeyParser.getDimensionKey;
import static _959.server_waypoint.common.util.TextHelper.getDimensionColor;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.*;

public final class XaerosMinimapWaypointHelper {
    private XaerosMinimapWaypointHelper() {
    }

    public static void replaceAll(WaypointClientMod waypointClientMod) {
        MinimapSession session = getMinimapSession();
        Player player = Minecraft.getInstance().player;
        boolean[] synced = {true};
        waypointClientMod.forEachWaypointFileManager(fileManager ->
                synced[0] &= replaceDimension(session, fileManager));
        try {
            session.getWorldManagerIO().saveAllWorlds(session);
        } catch (IOException exception) {
            LOGGER.warn("Failed to save Xaero's Minimap worlds", exception);
            displayClientMessage(player, Component.translatable("server_waypoint.save.failed.xaeros").withStyle(ChatFormatting.RED));
            return;
        }
        displaySyncResult(player, synced[0], Component.translatable("server_waypoint.all.added.xaeros"));
    }

    public static WaypointData collectUpload(UploadRequestBuffer request) {
        if (!ClientConfig.isXaerosMinimapLoaded) {
            return WaypointData.upload(request.requestId(), UploadStatus.XAERO_NOT_INSTALLED, List.of());
        }
        if (!WaypointClientMod.isXaerosMinimapReady) {
            return WaypointData.upload(request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
        }

        try {
            MinimapSession session = getMinimapSession();
            if (session == null) {
                return WaypointData.upload(request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
            }
            List<DimensionWaypointData> uploadedDimensions = new ArrayList<>();
            for (String dimensionName : request.dimensionNames()) {
                ResourceKey<Level> dimensionKey = getDimensionKey(dimensionName);
                if (dimensionKey == null) {
                    LOGGER.warn("Cannot export Xaero's waypoints: unknown requested dimension {}", dimensionName);
                    return WaypointData.upload(request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
                }
                MinimapWorld minimapWorld = getMinimapWorld(session, dimensionKey);
                if (minimapWorld == null) {
                    LOGGER.warn("Cannot export Xaero's waypoints: world for requested dimension {} is not loaded", dimensionName);
                    return WaypointData.upload(request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
                }
                List<WaypointList> uploadedLists = new ArrayList<>();
                for (WaypointSet waypointSet : minimapWorld.getIterableWaypointSets()) {
                    String listName = SyncedWaypointName.parseSyncedName(waypointSet.getName());
                    if (listName == null) {
                        listName = waypointSet.getName();
                    }
                    if (request.listName() != null && !request.listName().equals(listName)) {
                        continue;
                    }
                    uploadedLists.add(createUploadedWaypointList(request, listName, waypointSet));
                }
                uploadedDimensions.add(new DimensionWaypointData(dimensionName, uploadedLists));
            }
            return WaypointData.upload(request.requestId(), UploadStatus.SUCCESS, uploadedDimensions);
        } catch (Exception exception) {
            LOGGER.warn("Failed to export Xaero's waypoints for upload", exception);
            return WaypointData.upload(request.requestId(), UploadStatus.FAILED, List.of());
        }
    }

    private static WaypointList createUploadedWaypointList(
            UploadRequestBuffer request,
            String listName,
            WaypointSet waypointSet
    ) {
        List<SimpleWaypoint> uploadedWaypoints = new ArrayList<>();
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
            uploadedWaypoints.add(simpleWaypoint);
        }
        return new WaypointList(listName, WaypointList.SERVER_N, uploadedWaypoints);
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
        boolean synced = replaceWaypointList(minimapWorld, waypointList);
        if (saveMinimapWorldWithFeedback(session, minimapWorld, player)) {
            displaySyncResult(player, synced, Component.translatable("server_waypoint.list.added.xaeros", waypointList.name()));
        }
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
        boolean synced = replaceWaypointLists(minimapWorld, waypointLists);
        if (saveMinimapWorldWithFeedback(session, minimapWorld, player)) {
            displaySyncResult(player, synced, Component.translatable("server_waypoint.dimension.waypoint.added.xaeros", Component.literal(dimensionName).withStyle(getDimensionColor(dimensionName))));
        }
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
        String syncedListName = getSyncedWaypointSetName(listName);
        if (syncedListName == null) {
            LOGGER.warn("Skipping Xaero's Minimap sync for list {} because its generated name would be ambiguous.", listName);
            return;
        }
        if (DEFAULT_WAYPOINT_SET.equals(syncedListName) && waypoint != null
                && SyncedWaypointName.formatSyncedName(waypoint.name()) == null) {
            LOGGER.warn("Skipping Xaero's Minimap sync for waypoint {} because its generated name would be ambiguous.", waypoint.name());
            return;
        }
        WaypointSet waypointSet = minimapWorld.getWaypointSet(syncedListName);

        if (waypointSet == null && (type == WaypointModificationType.ADD || type == WaypointModificationType.UPDATE || type == WaypointModificationType.ADD_LIST)) {
            waypointSet = getOrCreateSyncedWaypointSet(minimapWorld, listName);
            LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.", listName, dimKey);
        }

        Component feedback = null;
        switch (type) {
            case ADD -> {
                if (waypoint == null) {
                    return;
                }
                replaceSyncedWaypoint(waypointSet, waypoint);
                feedback = Component.translatable("server_waypoint.modification.add.xaeros", toVanillaText(waypointText(waypoint, dimensionName, listName)));
            }
            case REMOVE -> {
                if (waypointSet == null) {
                    return;
                }
                if (removeSyncedWaypoint(waypointSet, waypointName)) {
                    feedback = Component.translatable("server_waypoint.modification.remove.xaeros",
                            Component.literal(waypointName));
                }
            }
            case UPDATE -> {
                if (waypoint == null) {
                    return;
                }
                if (waypointName != null && !waypointName.equals(waypoint.name())) {
                    removeSyncedWaypoint(waypointSet, waypointName);
                }
                replaceSyncedWaypoint(waypointSet, waypoint);
                feedback = Component.translatable("server_waypoint.modification.update.xaeros", toVanillaText(waypointText(waypoint, dimensionName, listName)));
            }
            case ADD_LIST -> {
            }
            case REMOVE_LIST -> {
                if (waypointSet != null || DEFAULT_WAYPOINT_SET.equals(listName)) {
                    removeSyncedWaypointSet(minimapWorld, syncedListName);
                    feedback = Component.translatable("server_waypoint.list.removed.xaeros", listName);
                }
            }
        }
        if (saveMinimapWorldWithFeedback(session, minimapWorld, player) && feedback != null) {
            displayClientMessage(player, feedback);
        }
    }

    private static boolean replaceDimension(MinimapSession session, WaypointFileManager fileManager) {
        ResourceKey<Level> dimKey = getValidDimensionKey(fileManager.getDimensionName());
        if (dimKey == null) {
            warnInvalidDimension(Minecraft.getInstance().player, fileManager.getDimensionName());
            return false;
        }
        return replaceWaypointLists(getMinimapWorld(session, dimKey), fileManager.getWaypointLists());
    }

    private static ResourceKey<Level> getValidDimensionKey(String dimensionName) {
        return getDimensionKey(dimensionName);
    }

    private static boolean saveMinimapWorldWithFeedback(MinimapSession session, MinimapWorld minimapWorld, Player player) {
        try {
            saveMinimapWorld(session, minimapWorld);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to save waypoints", e);
            displayClientMessage(player, Component.translatable("server_waypoint.save.failed.xaeros").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    private static void displaySyncResult(Player player, boolean synced, Component successMessage) {
        displayClientMessage(player, synced ? successMessage : Component.translatable(
                "server_waypoint.sync.incomplete", "Xaero's minimap").withStyle(ChatFormatting.RED));
    }

    private static void warnInvalidDimension(Player player, String dimensionName) {
        LOGGER.warn("Failed to decode dimension {}", dimensionName);
        displayClientMessage(player, Component.translatable("server_waypoint.dimension.decode.fail",
                Component.literal(String.valueOf(dimensionName))).withStyle(ChatFormatting.RED));
    }
}
