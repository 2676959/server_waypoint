package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.util.SyncedWaypointName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.common.gui.dropdown.DropDownWidget;

@Mixin(DropDownWidget.class)
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
