//~ gui_graphics_26
package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.client.gui.render.DrawContextHelper;
import _959.server_waypoint.common.util.SyncedWaypointName;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.waypoints.Waypoint;

import static _959.server_waypoint.common.util.SyncedWaypointHighlight.XAEROS_SYNCED_DEFAULT_BACKGROUND;

@Mixin(targets = "xaero.common.gui.GuiWaypoints$List", remap = false)
public class XaerosMinimapGuiWaypointsListMixin {
    @Inject(method = "drawWaypointSlot", at = @At("HEAD"), remap = false)
    private void sw$drawSyncedWaypointBackground(
            GuiGraphicsExtractor context,
            Waypoint waypoint,
            int x,
            int y,
            CallbackInfo ci
    ) {
        if (waypoint == null || !SyncedWaypointName.isSinglePartSyncedName(waypoint.getName())) {
            return;
        }
        DrawContextHelper.nextLayer(context);
        context.fill(x, y - 2, x + 220, y + 16, XAEROS_SYNCED_DEFAULT_BACKGROUND);
        DrawContextHelper.previousLayer(context);
    }
}
