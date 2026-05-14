package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ProjectionMatrix;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    //? if >= 26.1 {
    @Inject(
            method = "extractGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"),
            remap = false
    )
    private void sw$renderWaypoints(DeltaTracker deltaTracker,
                                    boolean shouldRenderLevel,
                                    boolean resourcesLoaded,
                                    CallbackInfo ci,
                                    @Local(name = "graphics") GuiGraphicsExtractor graphics) {
        OptimizedWaypointRenderer.renderWaypoints(graphics);
    }

    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;")
    )
    private Matrix4f sw$copyProjectionMatrix(Matrix4f matrix4f) {
        ProjectionMatrix.set(matrix4f);
        return matrix4f;
    }
    //?}
}
