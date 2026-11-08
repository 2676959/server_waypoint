package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;

/** A nearby projection of the waypoint block centre, in absolute world coordinates. */
public record WorldMarkerPosition(double x, double y, double z) {
    public static WorldMarkerPosition project(WorldMarkerPosition eye, WaypointPos target, double radius) {
        double dx = target.x() + 0.5D - eye.x;
        double dy = target.y() + 0.5D - eye.y;
        double dz = target.z() + 0.5D - eye.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double factor = distance > radius ? radius / distance : 1.0D;
        return new WorldMarkerPosition(eye.x + dx * factor, eye.y + dy * factor, eye.z + dz * factor);
    }

    public double distanceTo(WorldMarkerPosition other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
