package _959.server_waypoint.fabric.network;

import _959.server_waypoint.fabric.ServerWaypointFabric;
import _959.server_waypoint.fabric.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.fabric.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.fabric.network.waypoint.WorldWaypoint;
import _959.server_waypoint.fabric.server.WaypointServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ClientHandshakeHandler {
    public static void onClientHandshake(HandshakeC2SPayload payload, ServerPlayNetworking.Context context) {
        int edition = payload.waypointsEdition();
        ServerWaypointFabric.LOGGER.info("new connection with client edition: {}", edition);
        if (edition != WaypointServer.EDITION) {
            WorldWaypoint worldWaypoint = WaypointServer.INSTANCE.toWorldWaypoint();
            if (worldWaypoint != null) {
                ServerPlayNetworking.send(context.player(), new WorldWaypointS2CPayload(worldWaypoint, WaypointServer.EDITION));
            }
        }
    }
}
