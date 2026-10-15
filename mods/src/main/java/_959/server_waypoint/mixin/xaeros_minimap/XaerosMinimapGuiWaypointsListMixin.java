//~ gui_graphics_26
package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.client.gui.render.DrawContextHelper;
import _959.server_waypoint.common.util.SyncedWaypointHighlight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.gui.GuiWaypoints;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.world.MinimapWorld;

@Mixin(targets = "xaero.common.gui.GuiWaypoints$List", remap = false)
public abstract class XaerosMinimapGuiWaypointsListMixin {
    @Shadow
    @Final
    private GuiWaypoints this$0;

    @Inject(method = "drawWaypointSlot", at = @At("HEAD"), remap = false)
    private void sw$drawSyncedWaypointBackground(
            GuiGraphicsExtractor context,
            Waypoint waypoint,
            int x,
            int y,
            CallbackInfo ci
    ) {
        if (waypoint == null) {
            return;
        }
        MinimapWorld displayedWorld = ((XaerosMinimapGuiWaypointsAccessor) this.this$0).sw$getDisplayedWorld();
        int color = SyncedWaypointHighlight.xaerosWaypointBackground(
                displayedWorld == null ? null : displayedWorld.getCurrentWaypointSet(), waypoint
        );
        if (color == 0) {
            return;
        }
        // Xaero owns this list's row geometry and clipping; SW widgets cannot render its rows.
        DrawContextHelper.nextLayer(context);
        context.fill(x, y - 2, x + 220, y + 16, color);
        DrawContextHelper.previousLayer(context);
    }
}
