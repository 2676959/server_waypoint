//~ gui_graphics_26
package _959.server_waypoint.mixin;

//? if > 1.20.6
import net.minecraft.client.DeltaTracker;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GameHudMixin {
    //? if < 26.1 {
    @Inject(
            method = /*? if <26 {*//*"render"*//*?} else {*/"extractRenderState"/*?}*/,
            at = @At(value = "HEAD")
    )
    public void sw$renderWaypoints(
            GuiGraphicsExtractor context,
            //? if > 1.20.6 {
            DeltaTracker tickCounter,
            //?} else {
            /*float tickDelta,
            *///?}
            CallbackInfo ci
    ) {
        OptimizedWaypointRenderer.
        //$ render_method_swap
                renderWaypoints
                (context);
    }
    //?}
}
