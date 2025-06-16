package _959.server_waypoint.mixin.client;

import _959.server_waypoint.ServerWaypointClient;
import _959.server_waypoint.util.HandshakePayloadGenerator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;

@Mixin(MinimapWorldStateUpdater.class)
public class MinimapWorldStateUpdaterMixin {

    @Inject(method = "onServerLevelId", at = @At(value = "TAIL"), remap = false)
    private void injectOnServerLevelId(int id, CallbackInfo ci) {
        if (ServerWaypointClient.isHandshakeFinished()) {
            return;
        }
        ServerWaypointClient.LOGGER.info("Send handshake payload to server");
        ClientPlayNetworking.send(HandshakePayloadGenerator.generate());
        ServerWaypointClient.setHandshakeFinished(true);
    }
}
