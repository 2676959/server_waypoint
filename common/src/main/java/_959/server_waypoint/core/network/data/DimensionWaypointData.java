package _959.server_waypoint.core.network.data;

import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.List;

/** An immutable snapshot of all waypoint lists carried for one dimension. */
public record DimensionWaypointData(String dimensionName, List<WaypointList> waypointLists) {
    public DimensionWaypointData {
        waypointLists = waypointLists.stream()
                .map(WaypointList::deepCopy)
                .toList();
    }
}
