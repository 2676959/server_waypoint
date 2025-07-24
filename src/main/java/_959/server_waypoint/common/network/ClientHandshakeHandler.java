package _959.server_waypoint.common.network;

import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.waypoint.WorldWaypoint;
import _959.server_waypoint.common.server.WaypointServer;

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif neoforge {
/*import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
*///?}

import static _959.server_waypoint.common.ServerWaypointMod.LOGGER;

public class ClientHandshakeHandler {
    public static void onClientHandshake(
            HandshakeC2SPayload payload,
            //? if fabric {
            ServerPlayNetworking.Context context
            //?} elif neoforge {
            /*IPayloadContext context
            *///?}
    ) {
        int edition = payload.waypointsEdition();
        LOGGER.info("new connection with client edition: {}", edition);
        if (edition != WaypointServer.EDITION) {
            WorldWaypoint worldWaypoint = WaypointServer.INSTANCE.toWorldWaypoint();
            if (worldWaypoint != null) {
                WorldWaypointS2CPayload waypointPayload = new WorldWaypointS2CPayload(worldWaypoint, WaypointServer.EDITION);
                //? if fabric {
                ServerPlayNetworking.send(context.player(), waypointPayload);
                //?} elif neoforge {
                /*PacketDistributor.sendToPlayer((ServerPlayerEntity) context.player(), waypointPayload);
                *///?}
            }
        }
    }
}
