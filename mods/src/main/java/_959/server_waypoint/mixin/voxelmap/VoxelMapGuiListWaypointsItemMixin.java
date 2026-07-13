//~ gui_graphics_26
//? if fabric {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.client.gui.render.DrawContextHelper;
import _959.server_waypoint.common.client.gui.render.WaypointTextures;
import _959.server_waypoint.common.util.SyncedWaypointName;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static _959.server_waypoint.common.util.SyncedWaypointName.isVoxelMapSyncedWaypointName;

//? if >= 1.21.11 {
@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiListWaypoints$WaypointItem", remap = false)
//?} else {
/*@Mixin(targets = "com.mamiyaotaru.voxelmap.gui.GuiSlotWaypoints$WaypointItem", remap = false)
*///?}
public abstract class VoxelMapGuiListWaypointsItemMixin extends AbstractSelectionList.Entry<VoxelMapGuiListWaypointsItemMixin> {
    @Unique
    private static final int iconSize = 12;

    //? if >= 26 {
    @Redirect(
            method = "extractContent(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            remap = false
    )
    private void sw$drawSyncedWaypointIcon(
            GuiGraphicsExtractor context,
            Font font,
            String waypointName,
            int x,
            int y,
            int color
    ) {
        sw$drawSyncedWaypointIcon(context, waypointName, this.getX() - iconSize, this.getY() + 3);
        context.centeredText(font, SyncedWaypointName.toDisplayVoxelMapWaypointName(waypointName), x, y, color);
    }
    //?} elif >= 1.21.9 {
    /*@Redirect(
            method = "renderContent(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            remap = true
    )
    private void sw$drawSyncedWaypointIcon(
            GuiGraphicsExtractor context,
            Font font,
            String waypointName,
            int x,
            int y,
            int color
    ) {
        sw$drawSyncedWaypointIcon(context, waypointName, x, y);
        context.drawCenteredString(font, SyncedWaypointName.toDisplayVoxelMapWaypointName(waypointName), x, y, color);
    }
    *///?} else {
    /*@Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIIIZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            remap = true
    )
    private void sw$drawSyncedWaypointIcon(
            GuiGraphicsExtractor context,
            Font font,
            String waypointName,
            int x,
            int y,
            int color
    ) {
        sw$drawSyncedWaypointIcon(context, waypointName, x, y);
        context.drawCenteredString(font, SyncedWaypointName.toDisplayVoxelMapWaypointName(waypointName), x, y, color);
    }
    *///?}

    @Unique
    private static void sw$drawSyncedWaypointIcon(GuiGraphicsExtractor context, String waypointName, int x, int y) {
        if (isVoxelMapSyncedWaypointName(waypointName)) {
            DrawContextHelper.texture(context, WaypointTextures.SYNCED_ICON, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }
}
//?}
