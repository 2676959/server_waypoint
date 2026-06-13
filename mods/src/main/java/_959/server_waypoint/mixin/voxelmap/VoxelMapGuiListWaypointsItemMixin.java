//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.util.SyncedWaypointName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

//? if >= 1.21.11 {
@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiListWaypoints$WaypointItem", remap = false)
//?} else {
/*@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiSlotWaypoints$WaypointItem", remap = false)
*///?}
public class VoxelMapGuiListWaypointsItemMixin {
    //? if >= 26 {
    /*@ModifyArg(
            method = "extractContent(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            index = 1,
            remap = false
    )
    private String sw$displaySyncedWaypointName(String waypointName) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypointName);
    }
    *///?} elif >= 1.21.9 {
    @ModifyArg(
            method = "renderContent(Lnet/minecraft/client/gui/GuiGraphics;IIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            index = 1,
            remap = true
    )
    private String sw$displaySyncedWaypointName(String waypointName) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypointName);
    }
    //?} else {
    /*@ModifyArg(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            index = 1,
            remap = true
    )
    private String sw$displaySyncedWaypointName(String waypointName) {
        return SyncedWaypointName.toDisplayVoxelMapWaypointName(waypointName);
    }
    *///?}
}
//?}
