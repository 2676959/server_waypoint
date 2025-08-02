package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.WaypointServerMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "loadWorld", at = @At("TAIL"))
    private void onLoadWorld(CallbackInfo ci) {
        WaypointServerMod.INSTANCE.setMinecraftServer((MinecraftServer) (Object) this);
    }
}
