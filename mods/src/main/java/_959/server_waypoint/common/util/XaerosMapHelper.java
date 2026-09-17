package _959.server_waypoint.common.util;

public final class XaerosMapHelper {
    public static final int XAEROS_WORLD_MAP_UNKNOWN_Y = 32767;

    private XaerosMapHelper() {
    }

    public static int resolveWorldMapRightClickY(int rightClickY, int fallbackY) {
        if (rightClickY == XAEROS_WORLD_MAP_UNKNOWN_Y) {
            return fallbackY;
        }
        return rightClickY + 1;
    }

    public static int resolveWorldMapWaypointY(boolean yIncluded, int waypointY, int fallbackY) {
        if (yIncluded) {
            return waypointY;
        }
        return fallbackY;
    }
}
