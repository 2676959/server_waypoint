//~ gui_graphics_26
package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.util.SyncedWaypointHighlight;
import _959.server_waypoint.common.util.SyncedWaypointName;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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

    @ModifyVariable(
            method = "drawSlot",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String sw$displaySyncedWaypointSetString(String option) {
        SyncedWaypointName.DisplayName displayName = SyncedWaypointName.toWaypointDisplayName(option);
        this.sw$syncedWaypointSetOption = displayName.synced();
        return displayName.name();
    }

    @ModifyVariable(
            method = "drawSlot",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private Component sw$displaySyncedWaypointSetComponent(Component option) {
        SyncedWaypointName.DisplayName displayName = SyncedWaypointName.toWaypointDisplayName(option.getString());
        this.sw$syncedWaypointSetOption = displayName.synced();
        return displayName.synced() ? Component.literal(displayName.name()).setStyle(option.getStyle()) : option;
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", remap = true),
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
