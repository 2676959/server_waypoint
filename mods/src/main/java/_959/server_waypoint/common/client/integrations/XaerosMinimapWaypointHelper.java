package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.integrations.MapModIntegration.SyncSuccess;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import static _959.server_waypoint.common.client.integrations.MapModChatHelper.waypointText;
import static _959.server_waypoint.common.client.integrations.MapModSyncNotifications.showSyncFailure;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.common.util.DimensionKeyParser.getDimensionKey;
import static _959.server_waypoint.common.util.TextHelper.getDimensionColor;
import static _959.server_waypoint.common.util.XaeroMinimapHelper.*;

public final class XaerosMinimapWaypointHelper {
    private XaerosMinimapWaypointHelper() {
    }

    static SyncSuccess replaceAll(WaypointClientMod waypointClientMod) {
        MinimapSession session = getMinimapSession();
        boolean[] synced = {true};
        waypointClientMod.forEachWaypointFileManager(fileManager ->
                synced[0] &= replaceDimension(session, fileManager));
        try {
            session.getWorldManagerIO().saveAllWorlds(session);
        } catch (IOException exception) {
            LOGGER.warn("Failed to save Xaero's Minimap worlds", exception);
            showSyncFailure(Component.translatable("server_waypoint.save.failed.xaeros").withStyle(ChatFormatting.RED));
            return null;
        }
        return reportSyncResult(synced[0], SyncSuccess.FULL_SYNC);
    }

    public static WaypointData collectUpload(UploadRequestBuffer request) {
        if (!ClientConfig.isXaerosMinimapLoaded) {
            return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.XAERO_NOT_INSTALLED, List.of());
        }
        if (!WaypointClientMod.isXaerosMinimapReady) {
            return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
        }

        try {
            MinimapSession session = getMinimapSession();
            if (session == null) {
                return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
            }
            List<DimensionWaypointData> uploadedDimensions = new ArrayList<>();
            for (String dimensionName : request.dimensionNames()) {
                ResourceKey<Level> dimensionKey = getDimensionKey(dimensionName);
                if (dimensionKey == null) {
                    LOGGER.warn("Cannot export Xaero's waypoints: unknown requested dimension {}", dimensionName);
                    return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
                }
                MinimapWorld minimapWorld = getMinimapWorld(session, dimensionKey);
                if (minimapWorld == null) {
                    LOGGER.warn("Cannot export Xaero's waypoints: world for requested dimension {} is not loaded", dimensionName);
                    return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.XAERO_NOT_READY, List.of());
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
            return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.SUCCESS, uploadedDimensions);
        } catch (Exception exception) {
            LOGGER.warn("Failed to export Xaero's waypoints for upload", exception);
            return WaypointData.upload(WaypointScope.SHARED, request.requestId(), UploadStatus.FAILED, List.of());
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

    static SyncSuccess replaceList(String dimensionName, WaypointList waypointList) {
        ResourceKey<Level> dimKey = getValidDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(dimensionName);
            return null;
        }
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        boolean synced = replaceWaypointList(minimapWorld, waypointList);
        return saveMinimapWorldWithFeedback(session, minimapWorld)
                ? reportSyncResult(synced, new SyncSuccess("server_waypoint.list.added", waypointList.name()))
                : null;
    }

    static SyncSuccess replaceDimension(String dimensionName, List<WaypointList> waypointLists) {
        ResourceKey<Level> dimKey = getValidDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(dimensionName);
            return null;
        }
        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        boolean synced = replaceWaypointLists(minimapWorld, waypointLists);
        return saveMinimapWorldWithFeedback(session, minimapWorld)
                ? reportSyncResult(synced, new SyncSuccess("server_waypoint.dimension.waypoint.added",
                        Component.literal(dimensionName).withStyle(getDimensionColor(dimensionName))))
                : null;
    }

    static SyncSuccess applyModification(String dimensionName, String listName, WaypointModificationType type,
                                         SimpleWaypoint waypoint, String waypointName) {
        ResourceKey<Level> dimKey = getValidDimensionKey(dimensionName);
        if (dimKey == null) {
            warnInvalidDimension(dimensionName);
            return null;
        }

        MinimapSession session = getMinimapSession();
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        String syncedListName = getSyncedWaypointSetName(listName);
        if (syncedListName == null) {
            LOGGER.warn("Skipping Xaero's Minimap sync for list {} because its generated name would be ambiguous.", listName);
            return null;
        }
        if (DEFAULT_WAYPOINT_SET.equals(syncedListName) && waypoint != null
                && SyncedWaypointName.formatSyncedName(waypoint.name()) == null) {
            LOGGER.warn("Skipping Xaero's Minimap sync for waypoint {} because its generated name would be ambiguous.", waypoint.name());
            return null;
        }
        WaypointSet waypointSet = minimapWorld.getWaypointSet(syncedListName);

        if (waypointSet == null && (type == WaypointModificationType.ADD || type == WaypointModificationType.UPDATE || type == WaypointModificationType.ADD_LIST)) {
            waypointSet = getOrCreateSyncedWaypointSet(minimapWorld, listName);
            LOGGER.info("Waypoint set {} not found in dimension {}, creating new one.", listName, dimKey);
        }

        SyncSuccess feedback = null;
        switch (type) {
            case ADD -> {
                if (waypoint == null) {
                    return null;
                }
                replaceSyncedWaypoint(waypointSet, waypoint);
                feedback = new SyncSuccess("server_waypoint.modification.add",
                        toVanillaText(waypointText(waypoint, dimensionName, listName)));
            }
            case REMOVE -> {
                if (waypointSet == null) {
                    return null;
                }
                if (removeSyncedWaypoint(waypointSet, waypointName)) {
                    feedback = new SyncSuccess("server_waypoint.modification.remove",
                            Component.literal(waypointName));
                }
            }
            case UPDATE -> {
                if (waypoint == null) {
                    return null;
                }
                if (waypointName != null && !waypointName.equals(waypoint.name())) {
                    removeSyncedWaypoint(waypointSet, waypointName);
                }
                replaceSyncedWaypoint(waypointSet, waypoint);
                feedback = new SyncSuccess("server_waypoint.modification.update",
                        toVanillaText(waypointText(waypoint, dimensionName, listName)));
            }
            case ADD_LIST -> {
            }
            case REMOVE_LIST -> {
                if (waypointSet != null || DEFAULT_WAYPOINT_SET.equals(listName)) {
                    removeSyncedWaypointSet(minimapWorld, syncedListName);
                    feedback = new SyncSuccess("server_waypoint.list.removed", listName);
                }
            }
        }
        return saveMinimapWorldWithFeedback(session, minimapWorld) ? feedback : null;
    }

    private static boolean replaceDimension(MinimapSession session, WaypointFileManager fileManager) {
        ResourceKey<Level> dimKey = getValidDimensionKey(fileManager.getDimensionName());
        if (dimKey == null) {
            warnInvalidDimension(fileManager.getDimensionName());
            return false;
        }
        return replaceWaypointLists(getMinimapWorld(session, dimKey), fileManager.getWaypointLists());
    }

    private static ResourceKey<Level> getValidDimensionKey(String dimensionName) {
        return getDimensionKey(dimensionName);
    }

    private static boolean saveMinimapWorldWithFeedback(MinimapSession session, MinimapWorld minimapWorld) {
        try {
            saveMinimapWorld(session, minimapWorld);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to save waypoints", e);
            showSyncFailure(Component.translatable("server_waypoint.save.failed.xaeros").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    private static SyncSuccess reportSyncResult(boolean synced, SyncSuccess success) {
        if (synced) {
            return success;
        }
        showSyncFailure(Component.translatable("server_waypoint.sync.incomplete", "Xaero's minimap")
                .withStyle(ChatFormatting.RED));
        return null;
    }

    private static void warnInvalidDimension(String dimensionName) {
        LOGGER.warn("Failed to decode dimension {}", dimensionName);
        showSyncFailure(Component.translatable("server_waypoint.dimension.decode.fail",
                Component.literal(String.valueOf(dimensionName))).withStyle(ChatFormatting.RED));
    }
}
