package _959.server_waypoint.mixin;

import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ProjectionMatrix;

@Mixin(PerspectiveProjectionMatrixBuffer.class)
public class PerspectiveProjectionMatrixBufferMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void server_waypoint$captureProjectionMatrix(Matrix4f projectionMatrix, CallbackInfoReturnable<?> cir) {
        ProjectionMatrix.set(projectionMatrix);
    }
}
