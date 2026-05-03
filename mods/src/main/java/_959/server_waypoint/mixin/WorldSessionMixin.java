package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.WaypointClientMod;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSessionTelemetryManager.class)
public abstract class WorldSessionMixin {
    @Inject(
            method = "onDisconnect",
            at = @At(value = "TAIL")
    )
    private void sw$onLeaveServer(CallbackInfo ci) {
        WaypointClientMod.getInstance().onLeaveServer();
    }
}
