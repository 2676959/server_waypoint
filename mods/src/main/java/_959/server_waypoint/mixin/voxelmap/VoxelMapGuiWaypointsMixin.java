//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
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
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mamiyaotaru/voxelmap/VoxelConstants;getMinecraft()Lnet/minecraft/client/Minecraft;"
            ),
            cancellable = true, remap = false)
    private void sw$redirectEditGui(Waypoint waypoint, CallbackInfo ci) {
        if (isVoxelMapSyncedWaypointName(waypoint.name)) {
            MinecraftClientHelper.setScreen(new WaypointManagerScreen(
                    WaypointClientMod.getInstance() , (Screen) (Object) this
            ));
            ci.cancel();
        }
    }
}
//?}
