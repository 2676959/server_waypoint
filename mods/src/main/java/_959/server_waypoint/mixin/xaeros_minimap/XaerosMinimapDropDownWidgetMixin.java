//~ gui_graphics_26
package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.util.SyncedWaypointHighlight;
import _959.server_waypoint.common.util.SyncedWaypointName;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.11 || (forge && = 1.20.1) || (neoforge && = 1.21.3) {
@Pseudo
@Mixin(targets = "xaero.lib.client.gui.widget.dropdown.DropDownWidget", remap = false)
//?} else {
/*@Pseudo
@Mixin(targets = "xaero.common.gui.dropdown.DropDownWidget", remap = false)
*///?}
public class XaerosMinimapDropDownWidgetMixin {
    @Unique
    private boolean sw$syncedWaypointSetOption;
    @Unique
    private boolean sw$pendingSyncedWaypointSetOption;

    @Redirect(
            method = "drawMenu",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"),
            remap = false
    )
    private String sw$displaySyncedWaypointSetName(String instance, CharSequence target, CharSequence replacement) {
        SyncedWaypointName.DisplayName displayName = SyncedWaypointName.toWaypointDisplayName(instance.replace(target, replacement));
        this.sw$pendingSyncedWaypointSetOption = displayName.synced();
        return displayName.name();
    }

    @Inject(method = "drawSlot", at = @At("HEAD"), remap = false)
    private void sw$captureSyncedWaypointSetOption(
            GuiGraphicsExtractor context,
            String option,
            int slotId,
            int visualSlot,
            int screenHeight,
            int mouseX,
            boolean scrolling,
            int optionLimit,
            int x,
            int y,
            CallbackInfo ci
    ) {
        this.sw$syncedWaypointSetOption = this.sw$pendingSyncedWaypointSetOption;
        this.sw$pendingSyncedWaypointSetOption = false;
    }

    //? if >= 26 {
    @ModifyArg(
            method = "drawSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", remap = false),
            index = 4,
            remap = false
    )
    private int sw$useSyncedWaypointSetBackground(int color) {
        return this.sw$syncedWaypointSetOption ? sw$syncedWaypointSetBackground(color) : color;
    }
    //?} else {
    /*@ModifyArg(
            method = "drawSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", remap = true),
            index = 4
    )
    private int sw$useSyncedWaypointSetBackground(int color) {
        return this.sw$syncedWaypointSetOption ? sw$syncedWaypointSetBackground(color) : color;
    }
    *///?}

    @Unique
    private static int sw$syncedWaypointSetBackground(int color) {
        return SyncedWaypointHighlight.xaerosBackground(color);
    }
}
