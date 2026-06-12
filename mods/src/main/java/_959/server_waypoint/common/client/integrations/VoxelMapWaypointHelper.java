//? if fabric {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiPredicate;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public final class VoxelMapWaypointHelper {
    private VoxelMapWaypointHelper() {
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
