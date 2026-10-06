package _959.server_waypoint.common.util;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;

public final class SyncedWaypointHighlight {
    public static final int XAEROS_SYNCED_DEFAULT_BACKGROUND = 0x7F0D47A1;

    private SyncedWaypointHighlight() {
    }

    public static int xaerosWaypointBackground(WaypointSet waypointSet, Waypoint waypoint) {
        // Current synchronized waypoints have plain names; the set owns the marker.
        if (waypoint == null || waypointSet == null || !SyncedWaypointName.isSinglePartSyncedName(waypointSet.getName())) {
            return 0;
        }
        // Xaero appends its own server waypoint rows after the selected set's entries.
        for (Waypoint member : waypointSet.getWaypoints()) {
            if (member == waypoint) {
                return XAEROS_SYNCED_DEFAULT_BACKGROUND;
            }
        }
        return 0;
    }

    public static int xaerosBackground(int color) {
        return switch (color) {
            case -10496 -> 0xDD26C6DA;
            case -922757376 -> 0xCC00BCD4;
            case -13487566 -> 0xC80D47A1;
            case -939524096 -> XAEROS_SYNCED_DEFAULT_BACKGROUND;
            default -> color;
        };
    }
}
