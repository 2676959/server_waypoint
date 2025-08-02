package _959.server_waypoint.core.waypoint;

public record WaypointPos(int x, int y, int z) {
   public String toShortString() { return String.format("%d, %d, %d", x, y, z); }
}
