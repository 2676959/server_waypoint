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
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V")
    )
    private void renderWaypoint(CallbackInfo ci) {
        ModelViewMatrix.set(RenderSystem.getModelViewMatrix());
        ProjectionMatrix.set(RenderSystem.getProjectionMatrix());
    }
}