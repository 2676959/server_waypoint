package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.common.util.XaeroMinimapHelper;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import org.jetbrains.annotations.Nullable;
import xaero.hud.minimap.world.MinimapWorld;

/** Resolves World Map actions using Xaero's owning waypoint world and the server's cached data. */
public final class XaerosWorldMapWaypointHelper {
    private XaerosWorldMapWaypointHelper() {
    }

    public static @Nullable String getWaypointDimensionName(@Nullable MinimapWorld world) {
        if (world == null || world.getDimId() == null) {
            return null;
        }
        //? if >= 1.21.11 {
        return world.getDimId().identifier().toString();
        //?} else {
        /*return world.getDimId().location().toString();
        *///?}
    }

    public static @Nullable SyncedEditTarget resolveSyncedEditTarget(
            @Nullable String dimensionName,
            String rawSetName,
            String waypointName,
            WaypointFilesManagerCore files
    ) {
        String listName = XaeroMinimapHelper.getSyncedWaypointListName(
                rawSetName, waypointName);
        String decoded = SyncedWaypointName.parseSyncedName(waypointName);
        if (decoded != null) {
            waypointName = decoded;
        }
        if (dimensionName == null || listName == null) {
            return null;
        }
        WaypointFileManager file = files.getWaypointFileManager(dimensionName);
        WaypointList list = file == null ? null : file.getWaypointListByName(listName);
        SimpleWaypoint saved = list == null ? null : list.getWaypointByName(waypointName);
        return saved == null ? null : new SyncedEditTarget(
                dimensionName, list.name(), list.displayName(), new SimpleWaypoint(saved));
    }

    public record SyncedEditTarget(
            String dimensionName, String listName, String listDisplayName, SimpleWaypoint waypoint
    ) {
    }
}
