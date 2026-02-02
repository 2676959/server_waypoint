package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.WaypointClientMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameJoin", at = @At(value = "TAIL"))
    private void setHandshakeStatus(GameJoinS2CPacket packet, CallbackInfo ci) {
        WaypointClientMod.getInstance().onJoinServer();
    }

    @Inject(method = "unloadWorld", at = @At(value = "TAIL"))
    private void saveAndCleanStates(CallbackInfo ci) {
        WaypointClientMod.getInstance().onLeaveServer();
    }

}
