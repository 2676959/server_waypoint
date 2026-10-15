//? if voxelmap {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.integrations.VoxelMapWaypointHelper;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mamiyaotaru.voxelmap.gui.GuiWaypoints;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.util.SyncedWaypointName.isVoxelMapSyncedWaypointName;

@Mixin(value = GuiWaypoints.class, remap = false)
public class VoxelMapGuiWaypointsMixin {
    @Redirect(
            method = "deleteClicked",
            at = @At(value = "FIELD", target = "Lcom/mamiyaotaru/voxelmap/util/Waypoint;name:Ljava/lang/String;", opcode = Opcodes.GETFIELD),
            remap = false
    )
    private String sw$displaySyncedWaypointName(Waypoint waypoint) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypoint.name);
    }

    @Inject(
            method = "editWaypoint",
            at = @At("HEAD"),
            cancellable = true, remap = false)
    private void sw$redirectEditGui(Waypoint waypoint, CallbackInfo ci) {
        if (isVoxelMapSyncedWaypointName(waypoint.name)) {
            var target = VoxelMapWaypointHelper.resolveSyncedEditTarget(waypoint, WaypointClientMod.getInstance());
            if (target != null) {
                MinecraftClientHelper.setScreen(new WaypointEditScreen(
                        (Screen) (Object) this,
                        target.dimensionName(),
                        target.listName(),
                        target.listDisplayName(),
                        target.waypoint()
                ));
                ci.cancel();
            }
        }
    }
}
//?}
