package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.WaypointClientMod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}

@Mixin(MinimapWorldStateUpdater.class)
public class MinimapWorldStateUpdaterMixin {

    @Inject(method = "onServerLevelId", at = @At(value = "TAIL"), remap = false)
    private void injectOnServerLevelId(int id, CallbackInfo ci) {

        //? if fabric {
        ClientPlayNetworking.send(WaypointClientMod.getInstance().getClientHandShakePayload());
        //?} elif neoforge {
        /*PacketDistributor.sendToServer(HandshakePayloadGenerator.generate());
        *///?}
    }
}
