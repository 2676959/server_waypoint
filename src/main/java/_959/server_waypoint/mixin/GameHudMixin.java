package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class GameHudMixin {
    @Inject(method = "render", at = @At(value = "HEAD"))
    public void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
//        WaypointRenderer.renderOnHud(context);
        OptimizedWaypointRenderer.render(context);
    }
}
