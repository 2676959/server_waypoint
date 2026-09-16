//~ gui_graphics_26
package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.util.SyncedWaypointName;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if >= 1.21.11 || (forge && = 1.20.1) || (neoforge && = 1.21.3) {
@Pseudo
@Mixin(targets = "xaero.lib.client.gui.widget.dropdown.DropDownWidget", remap = false)
//?} else {
/*@Pseudo
@Mixin(targets = "xaero.common.gui.dropdown.DropDownWidget", remap = false)
*///?}
public class XaerosMinimapDropDownWidgetMixin {
    @ModifyVariable(
            method = "drawSlot",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String sw$displaySyncedWaypointSetString(String option) {
        return SyncedWaypointName.toDisplayWaypointName(option);
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
        return displayName.synced() ? Component.literal(displayName.name()).setStyle(option.getStyle()) : option;
    }
}
