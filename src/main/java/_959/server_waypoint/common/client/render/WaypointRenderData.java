package _959.server_waypoint.common.client.render;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

public record WaypointRenderData(Vector3f pos, int rgb, String listName, String name, String initials) {
    public static WaypointRenderData from(String listName, SimpleWaypoint waypoint) {
        WaypointPos waypointPos = waypoint.pos();
        Vector3f pos = new Vector3f(waypointPos.X(), waypointPos.y(), waypointPos.Z());
        int color = Formatting.byColorIndex(waypoint.colorIdx()).getColorValue();
        return new WaypointRenderData(pos, color, listName, waypoint.name(), waypoint.initials());
    }
}