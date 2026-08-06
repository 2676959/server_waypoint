package _959.server_waypoint.core.edit;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.WaypointList;
import org.jetbrains.annotations.Nullable;

public record WaypointListEditResult(
        EditResultStatus status,
        @Nullable WaypointFileManager fileManager,
        @Nullable WaypointList beforeSnapshot,
        @Nullable WaypointList afterSnapshot
) {
    public WaypointListEditResult {
        beforeSnapshot = beforeSnapshot == null ? null : beforeSnapshot.deepCopy();
        afterSnapshot = afterSnapshot == null ? null : afterSnapshot.deepCopy();
    }

    public int syncNum() {
        return this.afterSnapshot == null ? 0 : this.afterSnapshot.getSyncNum();
    }
}
