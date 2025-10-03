package _959.server_waypoint.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.ServerWaypointClientMod.LOGGER;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "setWorld", at = @At(value = "HEAD"))
    public void setWorld(ClientWorld world, CallbackInfo ci) {
        if (world == null) {
            return;
        }
        String worldName = world.getRegistryKey().getValue().toString();
        LOGGER.info("setWorld to: {}", worldName);
    }
}
