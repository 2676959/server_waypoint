package _959.server_waypoint.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ModelViewMatrix;
import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ProjectionMatrix;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(
            //? if > 1.20.1 {
            method = "renderMain",
            //?} else {
            /*method = "render",
            *///?}
            at = @At(value = "TAIL")
    )
    private void renderWaypoint(CallbackInfo ci) {
        ModelViewMatrix.set(RenderSystem.getModelViewMatrix());
        ProjectionMatrix.set(RenderSystem.getProjectionMatrix());
    }
}