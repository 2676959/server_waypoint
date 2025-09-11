//? if neoforge {
//package _959.server_waypoint.neoforge;
//
//import _959.server_waypoint.common.ServerWaypointClientMod;
//import _959.server_waypoint.common.network.ServerWaypointPayloadHandler;
//import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
//import _959.server_waypoint.common.network.payload.s2c.*;
//import net.neoforged.api.distmarker.Dist;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.fml.common.Mod;
//import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//
//@Mod(value = ServerWaypointNeoForge.MOD_ID, dist = Dist.CLIENT)
//@EventBusSubscriber(modid = ServerWaypointNeoForge.MOD_ID, value = Dist.CLIENT)
//public class ServerWaypointNeoForgeClient extends ServerWaypointClientMod {
//    @SubscribeEvent
//    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
//        final PayloadRegistrar registrar = event.registrar("1");
//        // S2C
//        registrar.playToClient(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC, ServerWaypointPayloadHandler::onWaypointListPayload);
//        registrar.playToClient(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC, ServerWaypointPayloadHandler::onDimensionWaypointPayload);
//        registrar.playToClient(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC, ServerWaypointPayloadHandler::onWorldWaypointPayload);
//        registrar.playToClient(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC, ServerWaypointPayloadHandler::onWaypointModificationPayload);
//        // C2S
//        registrar.playToServer(HandshakeC2SPayload.ID, HandshakeC2SPayload.PACKET_CODEC, (payload, context) -> {});
//    }
//}
//?}