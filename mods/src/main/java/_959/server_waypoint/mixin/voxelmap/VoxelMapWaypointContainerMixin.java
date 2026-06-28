//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mamiyaotaru.voxelmap.util.WaypointContainer;

@Mixin(value = WaypointContainer.class, remap = false)
public class VoxelMapWaypointContainerMixin {
//    @Redirect(
//            method = "renderWaypoints",
//            at = @At(value = "FIELD", target = "Lcom/mamiyaotaru/voxelmap/util/Waypoint;name:Ljava/lang/String;"),
//            remap = false,
//            require = 0
//    )
//    private String sw$toUnformattedWaypointName1(Waypoint waypoint) {
//        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypoint.name);
//    }

    @Redirect(
            method = "renderSign",
            at = @At(value = "FIELD", target = "Lcom/mamiyaotaru/voxelmap/util/Waypoint;name:Ljava/lang/String;", opcode = Opcodes.GETFIELD),
            remap = false,
            require = 0
    )
    private String sw$toUnformattedWaypointName2(Waypoint waypoint) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypoint.name);
    }
}
//?}
