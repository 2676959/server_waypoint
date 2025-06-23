package _959.server_waypoint.network;

import _959.server_waypoint.ServerWaypoint;
import _959.server_waypoint.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.network.waypoint.WorldWaypoint;
import _959.server_waypoint.server.WaypointServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ClientHandshakeHandler {
    public static void onClientHandshake(HandshakeC2SPayload payload, ServerPlayNetworking.Context context) {
        int edition = payload.waypointsEdition();
        ServerWaypoint.LOGGER.info("new connection with client edition: {}", edition);
        if (edition != WaypointServer.EDITION) {
            WorldWaypoint worldWaypoint = WaypointServer.INSTANCE.toWorldWaypoint();
            if (worldWaypoint != null) {
                ServerPlayNetworking.send(context.player(), new WorldWaypointS2CPayload(worldWaypoint, WaypointServer.EDITION));
            }
        }
    }
}
