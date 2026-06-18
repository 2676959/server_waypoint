package _959.server_waypoint.common.client.util;

import _959.server_waypoint.common.network.payload.ModPayload;
//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif forge {
/*import _959.server_waypoint.forge.ServerWaypointForge;
import net.minecraftforge.network.PacketDistributor;
*///?} elif neoforge && >= 1.21.9 {
/*import net.neoforged.neoforge.client.network.ClientPacketDistributor;
*///?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}
//? if neoforge && = 1.20.2
/*import _959.server_waypoint.neoforge.ServerWaypointNeoForge;*/

public class NetworkHelper {
    public static void sendPayloadToServer(ModPayload payload) {
        //? if fabric {
        ClientPlayNetworking.send(payload);
        //?} elif forge {
        /*//? if <= 1.20.1 {
        /^ServerWaypointForge.PACKET_CHANNEL.sendToServer(payload);
        ^///?} else {
        ServerWaypointForge.PACKET_CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
        //?}
        *///?} elif neoforge && >= 1.21.9 {
        /*ClientPacketDistributor.sendToServer(payload);
        *///?} elif neoforge && = 1.20.2 {
        /*ServerWaypointNeoForge.PACKET_CHANNEL.sendToServer(payload);
        *///?} elif neoforge && = 1.20.4 {
        /*PacketDistributor.SERVER.noArg().send(payload);
        *///?} elif neoforge {
        /*PacketDistributor.sendToServer(payload);
        *///?}
    }
}
