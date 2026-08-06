package _959.server_waypoint.core.edit;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import org.jetbrains.annotations.Nullable;

public record WaypointEditResult(
        EditResultStatus status,
        @Nullable WaypointFileManager fileManager,
        @Nullable WaypointList listSnapshot,
        @Nullable SimpleWaypoint beforeSnapshot,
        @Nullable SimpleWaypoint afterSnapshot,
        int syncNum
) {
    public WaypointEditResult {
        listSnapshot = listSnapshot == null ? null : listSnapshot.deepCopy();
        beforeSnapshot = beforeSnapshot == null ? null : new SimpleWaypoint(beforeSnapshot);
        afterSnapshot = afterSnapshot == null ? null : new SimpleWaypoint(afterSnapshot);
    }
}
