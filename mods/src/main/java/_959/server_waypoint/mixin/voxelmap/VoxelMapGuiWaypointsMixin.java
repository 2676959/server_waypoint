//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mamiyaotaru.voxelmap.gui.GuiWaypoints;

@Mixin(value = GuiWaypoints.class, remap = false)
public class VoxelMapGuiWaypointsMixin {
    @Redirect(
            method = "deleteClicked",
            at = @At(value = "FIELD", target = "Lcom/mamiyaotaru/voxelmap/util/Waypoint;name:Ljava/lang/String;"),
            remap = false
    )
    private String sw$displaySyncedWaypointName(Waypoint waypoint) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypoint.name);
    }
}
//?}
