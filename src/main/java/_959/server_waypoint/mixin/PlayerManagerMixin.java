package _959.server_waypoint.mixin;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.core.network.buffer.XaerosWorldIdBuffer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.getWorldId;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "sendWorldInfo", at = @At(value = "TAIL"))
    private void onSendWorldInfo(ServerPlayerEntity player, ServerWorld world, CallbackInfo ci) {
        if (CONFIG.Features().sendXaerosWorldId()) {
            ModMessageSender.getInstance().sendPlayerPacket(player, new XaerosWorldIdBuffer(getWorldId()));
        }
    }
}
