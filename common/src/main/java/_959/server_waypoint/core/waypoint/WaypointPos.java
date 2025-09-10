package _959.server_waypoint.core.waypoint;

public record WaypointPos(int x, int y, int z) {
   public String toShortString() { return String.format("%d, %d, %d", x, y, z); }
   public float X() {
      return x + 0.5F;
   }

   public float Z() {
      return z + 0.5F;
   }
}
