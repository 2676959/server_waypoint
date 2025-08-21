package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.WaypointPos;

public class BlockPosConverter {
    public static WaypointPos netherToOverWorld(WaypointPos pos) {
        return new WaypointPos(pos.x() * 8, pos.y(), pos.z() * 8);
    }

    public static WaypointPos overWorldToNether(WaypointPos pos) {
        return new WaypointPos(Math.floorDiv(pos.x(), 8), pos.y(), Math.floorDiv(pos.z(), 8));
    }
}
