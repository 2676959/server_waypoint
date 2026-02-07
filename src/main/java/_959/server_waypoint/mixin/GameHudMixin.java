package _959.server_waypoint.mixin;

//? if > 1.20.6
import net.minecraft.client.render.RenderTickCounter;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class GameHudMixin {
    @Inject(
            method = "render",
            at = @At(value = "HEAD")
    )
    public void sw$renderWaypoints(
            DrawContext context,
            //? if > 1.20.6 {
            RenderTickCounter tickCounter,
            //?} else {
            /*float tickDelta,
            *///?}
            CallbackInfo ci
    ) {
        OptimizedWaypointRenderer.render(context);
    }
}
