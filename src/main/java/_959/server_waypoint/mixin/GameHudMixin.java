package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GameHudMixin {
    @Inject(
            method = "render",
            at = @At(value = "HEAD")
    )
    public void sw$renderWaypoints(
            GuiGraphics context,
            //? if > 1.20.6 {
            DeltaTracker tickCounter,
            //?} else {
            /*float tickDelta,
            *///?}
            CallbackInfo ci
    ) {
        OptimizedWaypointRenderer.render(context);
    }
}
