package _959.server_waypoint.mixin;

//? if > 1.20.1 {
import net.minecraft.client.session.telemetry.WorldSession;
//?} else {
/*import net.minecraft.client.util.telemetry.WorldSession;
*///?}

import _959.server_waypoint.common.client.WaypointClientMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSession.class)
public abstract class WorldSessionMixin {
    @Inject(
            method = "onUnload",
            at = @At(value = "TAIL")
    )
    private void sw$onLeaveServer(CallbackInfo ci) {
        WaypointClientMod.getInstance().onLeaveServer();
    }
}
