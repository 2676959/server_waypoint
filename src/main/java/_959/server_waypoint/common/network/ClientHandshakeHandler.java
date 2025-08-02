package _959.server_waypoint.common.network;

import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.server.WaypointServerMod;

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif neoforge {
/*import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
*///?}

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;

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
        if (edition != WaypointServerMod.EDITION) {
            WorldWaypointS2CPayload worldWaypointPayload = WaypointServerMod.INSTANCE.toWorldWaypointPayload();
            if (worldWaypointPayload != null) {
                //? if fabric {
                ServerPlayNetworking.send(context.player(), worldWaypointPayload);
                //?} elif neoforge {
                /*PacketDistributor.sendToPlayer((ServerPlayerEntity) context.player(), worldWaypointPayload);
                *///?}
            }
        }
    }
}
