package _959.server_waypoint.mixin.xaeros_worldmap;

import _959.server_waypoint.access.XaerosWorldMapWaypointAccess;
import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.common.util.XaeroMinimapHelper;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.mods.gui.Waypoint;

@Mixin(value = Waypoint.class, remap = false)
public class XaerosWorldMapWaypointMixin implements XaerosWorldMapWaypointAccess {
    @Shadow
    private String setName;

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, remap = false)
    private void sw$displaySyncedWaypointName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SyncedWaypointName.toDisplayXaerosWorldMapName(cir.getReturnValue()));
    }

    @ModifyReturnValue(method = "getSymbol()Ljava/lang/String;", at = @At("RETURN"), remap = false)
    private String sw$fitSyncedWaypointSymbol(String symbol) {
        if (XaeroMinimapHelper.getSyncedWaypointListName(this.setName, sw$getRawName()) == null) {
            return symbol;
        }

        Font font = Minecraft.getInstance().font;
        // Xaero draws at 3x into a 64-pixel atlas tile with padding.
        int maxWidth = 20;
        if (font.width(symbol) <= maxWidth) {
            return symbol;
        }
        return font.substrByWidth(FormattedText.of(symbol), maxWidth).getString();
    }

    @Override
    public String sw$getRawName() {
        Object original = ((Waypoint) (Object) this).getOriginal();
        if (original instanceof xaero.common.minimap.waypoints.Waypoint minimapWaypoint) {
            return minimapWaypoint.getName();
        }
        return ((Waypoint) (Object) this).getName();
    }

    @Override
    public String sw$getRawSetName() {
        return this.setName;
    }
}
