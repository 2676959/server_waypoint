package _959.server_waypoint.mixin;

//? if < 26.1 {
/*//? if > 1.20.6
import net.minecraft.client.DeltaTracker;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.Gui;

@Mixin(Gui.class)
public class GameHudMixin {
    //? if < 26.1 {
    /*@Inject(
            method = /^? if <26 {^//^"render"^//^?} else {^/"extractRenderState"/^?}^/,
            at = @At(value = "HEAD")
    )
    public void sw$renderWaypoints(
            GuiGraphics context,
            //? if > 1.20.6 {
            DeltaTracker tickCounter,
            //?} else {
            /^float tickDelta,
            ^///?}
            CallbackInfo ci
    ) {
        OptimizedWaypointRenderer.render(context);
    }
    *///?}
}
