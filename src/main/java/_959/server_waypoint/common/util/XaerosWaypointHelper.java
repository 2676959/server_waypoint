package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import static _959.server_waypoint.util.ColorUtils.rgbToClosestColorIndex;

//? if >= 1.21.5 {
import xaero.hud.minimap.waypoint.WaypointVisibilityType;
//?} else {
/*import xaero.common.minimap.waypoints.WaypointVisibilityType;
*///?}

public class XaerosWaypointHelper {
    public static Waypoint simpleWaypointToXaerosWaypoint(SimpleWaypoint simpleWaypoint) {
        Waypoint waypoint = new Waypoint(
                simpleWaypoint.pos().x(),
                simpleWaypoint.pos().y(),
                simpleWaypoint.pos().z(),
                simpleWaypoint.name(),
                simpleWaypoint.initials(),
                WaypointColor.fromIndex(rgbToClosestColorIndex(simpleWaypoint.rgb())),
                WaypointPurpose.NORMAL,
                false,
                true
        );
        waypoint.setYaw(simpleWaypoint.yaw());
        waypoint.setRotation(true);
        waypoint.setVisibility(simpleWaypoint.global() ? WaypointVisibilityType.GLOBAL : WaypointVisibilityType.LOCAL);
        return waypoint;
    }
}
