package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.waypoint.DimensionWaypoint;

import java.util.List;

public record WorldWaypointBuffer(List<DimensionWaypoint> dimensionWaypoints, int edition) {
}
