package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.util.SyncedWaypointName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.hud.minimap.waypoint.render.WaypointMapRenderer;
import xaero.hud.minimap.waypoint.set.WaypointSet;

@Mixin(WaypointMapRenderer.class)
public class XaerosMinimapWaypointMapRendererMixin {
    @Redirect(
            method = "drawSetChange",
            at = @At(value = "INVOKE", target = "Lxaero/hud/minimap/waypoint/set/WaypointSet;getName()Ljava/lang/String;"),
            remap = false
    )
    private String sw$displaySyncedWaypointSetName(WaypointSet waypointSet) {
        return SyncedWaypointName.toDisplayWaypointName(waypointSet.getName());
    }
}
