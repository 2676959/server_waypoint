package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;

/** A waypoint together with the dimension and list it belongs to. */
public record PlacedWaypoint(String dimension, WaypointList list, SimpleWaypoint waypoint) {
    public Component reference(DimensionStyle dims) {
        return WaypointRefs.reference(dims, this.dimension, this.list, this.waypoint);
    }
}
