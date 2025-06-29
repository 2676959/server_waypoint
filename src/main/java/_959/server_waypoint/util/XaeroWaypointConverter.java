package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.WaypointVisibilityType;

public class XaeroWaypointConverter {
    public static Waypoint simpleWaypointToWaypoint(SimpleWaypoint simpleWaypoint) {
        Waypoint waypoint = new Waypoint(
                simpleWaypoint.pos().getX(),
                simpleWaypoint.pos().getY(),
                simpleWaypoint.pos().getZ(),
                simpleWaypoint.name(),
                simpleWaypoint.initials(),
                WaypointColor.fromIndex(simpleWaypoint.colorIdx()),
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
