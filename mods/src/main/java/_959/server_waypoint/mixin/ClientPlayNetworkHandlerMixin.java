package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.WaypointClientMod;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleLogin", at = @At(value = "TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void setHandshakeStatus(ClientboundLoginPacket packet, CallbackInfo ci) {
        WaypointClientMod.getInstance().onJoinServer();
    }

}
