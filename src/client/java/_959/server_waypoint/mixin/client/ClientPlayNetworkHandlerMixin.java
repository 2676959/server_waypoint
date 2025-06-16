package _959.server_waypoint.mixin.client;

import _959.server_waypoint.ServerWaypointClient;
import _959.server_waypoint.util.HandshakePayloadGenerator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameJoin",
            at = @At(value = "TAIL")
    )
    private void injectOnGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        ServerWaypointClient.LOGGER.info("Send handshake payload to server");
        ClientPlayNetworking.send(HandshakePayloadGenerator.generate());
    }
}
