package _959.server_waypoint.mixin.xaeros_worldmap;

import _959.server_waypoint.access.XaerosWorldMapWaypointAccess;
import _959.server_waypoint.common.util.SyncedWaypointName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.mods.gui.Waypoint;

@Mixin(value = Waypoint.class, remap = false)
public class XaerosWorldMapWaypointMixin implements XaerosWorldMapWaypointAccess {
    @Shadow
    private String setName;

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, remap = false)
    private void sw$displaySyncedWaypointName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SyncedWaypointName.toDisplayXaerosWorldMapName(cir.getReturnValue()));
    }

    @Inject(method = "getSetName", at = @At("RETURN"), cancellable = true, remap = false)
    private void sw$displaySyncedWaypointSetName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SyncedWaypointName.toDisplayXaerosWorldMapName(cir.getReturnValue()));
    }

    @Override
    public String sw$getRawName() {
        Object original = ((Waypoint) (Object) this).getOriginal();
        if (original instanceof xaero.common.minimap.waypoints.Waypoint minimapWaypoint) {
            return minimapWaypoint.getName();
        }
        return ((Waypoint) (Object) this).getName();
    }

    @Override
    public String sw$getRawSetName() {
        return this.setName;
    }
}
