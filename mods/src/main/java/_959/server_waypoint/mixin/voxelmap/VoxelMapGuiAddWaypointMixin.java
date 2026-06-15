//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.util.SyncedWaypointName;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mamiyaotaru.voxelmap.gui.GuiAddWaypoint;

@Mixin(value = GuiAddWaypoint.class, remap = false)
public class VoxelMapGuiAddWaypointMixin {
    @Shadow
    @Final
    private Waypoint waypoint;

    @Redirect(
            method = "init",
            at = @At(value = "FIELD", target = "Lcom/mamiyaotaru/voxelmap/util/Waypoint;name:Ljava/lang/String;"),
            remap = false
    )
    private String sw$displaySyncedWaypointNameInField(Waypoint waypoint) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypoint.name);
    }

    @Redirect(
            method = "acceptWaypoint",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;", ordinal = 0),
            remap = true
    )
    private String sw$keepSyncedWaypointNameFormatted(EditBox editBox) {
        String newName = editBox.getValue();
        SyncedWaypointName.ParsedName parsedName = SyncedWaypointName.parse(this.waypoint.name);
        if (parsedName == null) {
            return newName;
        }
        String formattedName = SyncedWaypointName.format(parsedName.listName(), newName);
        return formattedName == null ? this.waypoint.name : formattedName;
    }
}
//?}
