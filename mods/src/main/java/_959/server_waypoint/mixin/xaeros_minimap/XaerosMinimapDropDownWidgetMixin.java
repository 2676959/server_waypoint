package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.util.SyncedWaypointName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

//? if >= 1.21.11 || (forge && = 1.20.1) || (neoforge && = 1.21.3) {
@Pseudo
@Mixin(targets = "xaero.lib.client.gui.widget.dropdown.DropDownWidget", remap = false)
//?} else {
/*@Pseudo
@Mixin(targets = "xaero.common.gui.dropdown.DropDownWidget", remap = false)
*///?}
public class XaerosMinimapDropDownWidgetMixin {
    @Redirect(
            method = "drawMenu",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"),
            remap = false
    )
    private String sw$displaySyncedWaypointSetName(String instance, CharSequence target, CharSequence replacement) {
        return SyncedWaypointName.toDisplayWaypointName(instance.replace(target, replacement));
    }
}
