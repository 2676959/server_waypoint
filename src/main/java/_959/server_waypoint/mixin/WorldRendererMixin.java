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
    //? if > 1.21 {
    @Inject(
            method = "renderMain",
            at = @At(value = "HEAD")
    )
    //?} else {
    /*@Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;applyModelViewMatrix()V",
                    //? if >= 1.20.6 {
                    ordinal = 0,
                    //?} else {
                    //ordinal = 1,
                    //?}
                    shift = At.Shift.AFTER
            )
    )
    *///?}
    private void renderWaypoint(CallbackInfo ci) {
        ModelViewMatrix.set(RenderSystem.getModelViewMatrix());
        ProjectionMatrix.set(RenderSystem.getProjectionMatrix());
    }
}