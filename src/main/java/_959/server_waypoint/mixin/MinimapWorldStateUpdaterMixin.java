package _959.server_waypoint.mixin;

import _959.server_waypoint.common.ServerWaypointClientMod;
import _959.server_waypoint.common.util.HandshakePayloadGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;

import static _959.server_waypoint.common.ServerWaypointClientMod.LOGGER;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}

@Mixin(MinimapWorldStateUpdater.class)
public class MinimapWorldStateUpdaterMixin {

    @Inject(method = "onServerLevelId", at = @At(value = "TAIL"), remap = false)
    private void injectOnServerLevelId(int id, CallbackInfo ci) {
        if (ServerWaypointClientMod.isHandshakeFinished()) {
            return;
        }
        LOGGER.info("Send handshake payload to server");
        //? if fabric {
        ClientPlayNetworking.send(HandshakePayloadGenerator.generate());
        //?} elif neoforge {
        /*PacketDistributor.sendToServer(HandshakePayloadGenerator.generate());
        *///?}
        ServerWaypointClientMod.setHandshakeFinished(true);
    }
}
