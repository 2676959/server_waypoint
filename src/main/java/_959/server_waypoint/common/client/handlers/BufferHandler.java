package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.core.network.buffer.*;

/**
 * handle buffer received on the client side
 * */
public interface BufferHandler {
    void onServerHandshake(ServerHandshakeBuffer buffer);
    void onUpdatesBundle(UpdatesBundleBuffer buffer);
    void onWaypointList(WaypointListBuffer buffer);
    void onDimensionWaypoint(DimensionWaypointBuffer buffer);
    void onWorldWaypoint(WorldWaypointBuffer buffer);
    void onWaypointModification(WaypointModificationBuffer buffer);
}
