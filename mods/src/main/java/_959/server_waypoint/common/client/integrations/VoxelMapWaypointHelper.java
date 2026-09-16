//? if fabric {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.common.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.Waypoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public final class VoxelMapWaypointHelper {
    private VoxelMapWaypointHelper() {
    }

    public static void uploadToServer(UploadRequestBuffer request) {
        if (!ClientConfig.isVoxelMapLoaded) {
            sendUploadResult(request, UploadStatus.VOXELMAP_NOT_INSTALLED, List.of());
            return;
        }
        VoxelMap voxelMap = VoxelConstants.getVoxelMapInstance();
        if (voxelMap == null) {
            sendUploadResult(request, UploadStatus.VOXELMAP_NOT_READY, List.of());
            return;
        }

        try {
            WaypointManager manager = voxelMap.getWaypointManager();
            if (manager == null) {
                sendUploadResult(request, UploadStatus.VOXELMAP_NOT_READY, List.of());
                return;
            }
            // Older VoxelMap versions keep coordinate highlights outside getWaypoints().
            Predicate<Waypoint> coordinateHighlight = waypoint -> false;
            //? if >=26
            coordinateHighlight = manager::isCoordinateHighlight;
            List<DimensionWaypointData> uploadedDimensions = new ArrayList<>();
            for (String dimensionName : request.dimensionNames()) {
                String storageName = toVoxelMapStorageName(dimensionName);
                List<Waypoint> dimensionWaypoints = manager.getWaypoints().stream()
                        .filter(waypoint -> waypoint.dimensions.stream().anyMatch(
                                dimension -> dimension.getStorageName().equals(storageName)
                        ))
                        .toList();
                DimensionContainer dimension = voxelMap.getDimensionManager()
                        .getDimensionContainerByIdentifier(dimensionName);
                double coordinateScale = dimension == null || dimension.type == null
                        ? 1.0
                        : dimension.type.coordinateScale();
                uploadedDimensions.add(collectUploadDimension(
                        request,
                        dimensionName,
                        dimensionWaypoints,
                        coordinateHighlight,
                        coordinateScale
                ));
            }
            sendUploadResult(request, UploadStatus.SUCCESS, uploadedDimensions);
        } catch (Exception exception) {
            LOGGER.warn("Failed to export VoxelMap waypoints for upload", exception);
            sendUploadResult(request, UploadStatus.FAILED, List.of());
        }
    }

    static DimensionWaypointData collectUploadDimension(
            UploadRequestBuffer request,
            String dimensionName,
            Iterable<Waypoint> waypoints,
            Predicate<Waypoint> coordinateHighlight,
            double coordinateScale
    ) {
        Map<String, List<SimpleWaypoint>> uploadedByList = new LinkedHashMap<>();
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.enabled || coordinateHighlight.test(waypoint)) {
                continue;
            }
            SyncedWaypointName.ParsedName parsedName = SyncedWaypointName.parse(waypoint.name);
            String listName = parsedName == null ? "VoxelMap" : parsedName.listName();
            String waypointName = parsedName == null ? waypoint.name : parsedName.waypointName();
            if (request.listName() != null && !request.listName().equals(listName)) {
                continue;
            }
            if (request.waypointName() != null && !request.waypointName().equals(waypointName)) {
                continue;
            }

            int rgb = (Math.round(waypoint.red * 255.0F) << 16)
                    | (Math.round(waypoint.green * 255.0F) << 8)
                    | Math.round(waypoint.blue * 255.0F);
            SimpleWaypoint uploaded = new SimpleWaypoint(
                    waypointName,
                    "",
                    new WaypointPos(
                            (int) Math.round(waypoint.x / coordinateScale),
                            waypoint.y,
                            (int) Math.round(waypoint.z / coordinateScale)
                    ),
                    rgb,
                    0,
                    false
            );
            uploadedByList.computeIfAbsent(listName, ignored -> new ArrayList<>()).add(uploaded);
        }

        List<WaypointList> uploadedLists = uploadedByList.entrySet().stream()
                .map(entry -> new WaypointList(
                        entry.getKey(), WaypointList.SERVER_N, entry.getValue()
                ))
                .toList();
        return new DimensionWaypointData(dimensionName, uploadedLists);
    }

    private static void sendUploadResult(
            UploadRequestBuffer request,
            UploadStatus status,
            List<DimensionWaypointData> uploadedDimensions
    ) {
        WaypointClientMod.getInstance().sendChunkedMessageToServer(WaypointData.upload(
                request.requestId(), status, uploadedDimensions
        ));
    }

    public static void replaceAll(WaypointClientMod waypointClientMod) {
        WaypointManager manager = getWaypointManager();
        removeSyncedWaypoints(manager, (waypoint, parsedName) -> true);
        waypointClientMod.forEachWaypointFileManager(fileManager ->
                addLists(manager, fileManager.getDimensionName(), fileManager.getWaypointLists()));
    }

    public static void replaceDimension(String dimensionName, List<WaypointList> waypointLists) {
        WaypointManager manager = getWaypointManager();
        removeSyncedWaypoints(manager, (waypoint, parsedName) -> waypointInDimension(waypoint, dimensionName));
        addLists(manager, dimensionName, waypointLists);
    }

    public static void replaceList(String dimensionName, WaypointList waypointList) {
        WaypointManager manager = getWaypointManager();
        removeSyncedWaypoints(manager, (waypoint, parsedName) ->
                waypointList.name().equals(parsedName.listName()) && waypointInDimension(waypoint, dimensionName));
        addList(manager, dimensionName, waypointList);
    }

    public static void applyModification(String dimensionName, String listName, WaypointModificationType type, SimpleWaypoint waypoint, String waypointName) {
        WaypointManager manager = getWaypointManager();
        switch (type) {
            case ADD, UPDATE -> {
                removeSyncedWaypoint(manager, dimensionName, listName, waypointName);
                addWaypoint(manager, dimensionName, listName, waypoint);
            }
            case REMOVE -> removeSyncedWaypoint(manager, dimensionName, listName, waypointName);
            case REMOVE_LIST -> removeList(dimensionName, listName);
            case ADD_LIST -> {
            }
        }
    }

    private static void removeList(String dimensionName, String listName) {
        WaypointManager manager = getWaypointManager();
        removeSyncedWaypoints(manager, (waypoint, parsedName) ->
                listName.equals(parsedName.listName()) && waypointInDimension(waypoint, dimensionName));
    }

    private static void addLists(WaypointManager manager, String dimensionName, List<WaypointList> waypointLists) {
        for (WaypointList waypointList : waypointLists) {
            addList(manager, dimensionName, waypointList);
        }
    }

    private static void addList(WaypointManager manager, String dimensionName, WaypointList waypointList) {
        for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {
            Waypoint waypoint = toVoxelMapWaypoint(manager, dimensionName, waypointList.name(), simpleWaypoint);
            if (waypoint != null) {
                manager.addWaypoint(waypoint);
            }
        }
    }

    private static void addWaypoint(WaypointManager manager, String dimensionName, String listName, SimpleWaypoint simpleWaypoint) {
        if (simpleWaypoint == null) {
            return;
        }
        Waypoint waypoint = toVoxelMapWaypoint(manager, dimensionName, listName, simpleWaypoint);
        if (waypoint == null) {
            return;
        }
        manager.addWaypoint(waypoint);
    }

    private static Waypoint toVoxelMapWaypoint(WaypointManager manager, String dimensionName, String listName, SimpleWaypoint simpleWaypoint) {
        DimensionContainer dimension = VoxelConstants.getVoxelMapInstance()
                .getDimensionManager()
                .getDimensionContainerByIdentifier(dimensionName);
        if (dimension == null) {
            LOGGER.warn("Failed to decode VoxelMap dimension {}", dimensionName);
            return null;
        }
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(dimension);

        int x = simpleWaypoint.x();
        int z = simpleWaypoint.z();
        if (dimension.type != null && dimension.type.coordinateScale() != 1.0) {
            double dimensionScale = dimension.type.coordinateScale();
            x = (int) (x * dimensionScale);
            z = (int) (z * dimensionScale);
        }

        String voxelMapName = toVoxelMapName(listName, simpleWaypoint.name());
        if (voxelMapName == null) {
            LOGGER.warn("Skipping VoxelMap sync for waypoint {} in list {} because its generated name would be ambiguous.", simpleWaypoint.name(), listName);
            return null;
        }

        int rgb = simpleWaypoint.rgb();
        return new Waypoint(
                voxelMapName,
                x,
                z,
                simpleWaypoint.y(),
                true,
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                "",
                manager.getCurrentSubworldDescriptor(false),
                dimensions
        );
    }

    private static void removeSyncedWaypoint(WaypointManager manager, String dimensionName, String listName, String waypointName) {
        removeSyncedWaypoints(manager, (waypoint, parsedName) ->
                listName.equals(parsedName.listName())
                        && waypointName.equals(parsedName.waypointName())
                        && waypointInDimension(waypoint, dimensionName));
    }

    private static void removeSyncedWaypoints(WaypointManager manager, BiPredicate<Waypoint, SyncedWaypointName.ParsedName> shouldRemove) {
        List<Waypoint> matches = new ArrayList<>();
        for (Waypoint waypoint : manager.getWaypoints()) {
            SyncedWaypointName.ParsedName parsedName = SyncedWaypointName.parse(waypoint.name);
            if (parsedName != null && shouldRemove.test(waypoint, parsedName)) {
                matches.add(waypoint);
            }
        }
        for (Waypoint waypoint : matches) {
            manager.deleteWaypoint(waypoint);
        }
    }

    private static boolean waypointInDimension(Waypoint waypoint, String dimensionName) {
        String voxelMapDimensionName = toVoxelMapStorageName(dimensionName);
        return waypoint.dimensions.stream().anyMatch(dimension -> voxelMapDimensionName.equals(dimension.getStorageName()));
    }

    private static String toVoxelMapName(String listName, String waypointName) {
        return SyncedWaypointName.format(listName, waypointName);
    }

    private static String toVoxelMapStorageName(String dimensionName) {
        String minecraftPrefix = "minecraft:";
        if (dimensionName.startsWith(minecraftPrefix)) {
            return dimensionName.substring(minecraftPrefix.length());
        }
        return dimensionName;
    }

    private static WaypointManager getWaypointManager() {
        return VoxelConstants.getVoxelMapInstance().getWaypointManager();
    }
}
//?}
