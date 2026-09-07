package _959.server_waypoint.crossserver;

import _959.server_waypoint.core.waypoint.WaypointPos;

import java.util.List;
import java.util.Objects;

/** Read-only presentation data. Coordinates are advisory and never authorize destination teleporting. */
public record RemoteWaypointSnapshot(
        String displayName,
        String initials,
        WaypointPos position,
        int rgb,
        int yaw,
        boolean global,
        List<String> keywords,
        String description
) {
    public RemoteWaypointSnapshot {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(initials, "initials");
        Objects.requireNonNull(position, "position");
        keywords = List.copyOf(keywords);
        Objects.requireNonNull(description, "description");
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException("RGB must be a 24-bit color");
        }
        if (yaw < -180 || yaw > 180) {
            throw new IllegalArgumentException("Yaw must be between -180 and 180");
        }
    }
}
