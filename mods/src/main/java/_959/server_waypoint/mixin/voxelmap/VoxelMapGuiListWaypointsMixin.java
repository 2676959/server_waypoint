//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

//? if >= 1.21.11 {
@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiListWaypoints", remap = false)
//?} else {
/*@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiSlotWaypoints", remap = false)
*///?}
public class VoxelMapGuiListWaypointsMixin {
    @Redirect(
            method = {"setSelected", "updateFilter"},
            at = @At(value = "FIELD", target = "Lcom/mamiyaotaru/voxelmap/util/Waypoint;name:Ljava/lang/String;", opcode = Opcodes.GETFIELD),
            remap = false
    )
    private String sw$displaySyncedWaypointName(Waypoint waypoint) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypoint.name);
    }
}
//?}
