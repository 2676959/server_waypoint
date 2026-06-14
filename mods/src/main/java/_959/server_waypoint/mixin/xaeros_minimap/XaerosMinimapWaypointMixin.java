package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.util.SyncedWaypointName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;

@Mixin(value = Waypoint.class, remap = false)
public class XaerosMinimapWaypointMixin {
    @Inject(method = "getLocalizedName", at = @At("RETURN"), cancellable = true, remap = false)
    private void sw$displaySyncedWaypointName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SyncedWaypointName.toDisplayWaypointName(cir.getReturnValue()));
    }
}
