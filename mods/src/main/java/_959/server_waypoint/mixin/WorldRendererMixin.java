package _959.server_waypoint.mixin;

//? if >= 1.21.2 {
    //? if >= 1.21.6 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Vector4f;
    //?}
    //? if >= 1.21.3 && <= 1.21.5
/*import net.minecraft.client.renderer.GameRenderer;*/
    //? if = 1.21.3
/*import net.minecraft.client.renderer.LightTexture;*/
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;
//?} elif >= 1.20 {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ModelViewMatrix;
import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ProjectionMatrix;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    //? if > 1.21 {
    @Inject(
            method = {"renderLevel"},
            at = {@At("HEAD")}
    )
    public void
    sw$copyMatrices(
    //? if >= 1.21.9 {
    GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f cullingProjectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogVector, boolean skyPass, CallbackInfo info
    //?} elif >= 1.21.6 {
    /*GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogVector, boolean skyPass, CallbackInfo info
    *///?} elif >= 1.21.5 {
    /*GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, CallbackInfo info
    *///?} else {
    /*GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, CallbackInfo info
    *///?}
    )
    {
        ProjectionMatrix.set(projectionMatrix);
        ModelViewMatrix.set(modelViewMatrix);
    }
    //?} elif >= 1.20 {
    /*@Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;applyModelViewMatrix()V",
                    //? if >= 1.20.6 {
                    ordinal = 0,
                    //?} else {
                    /^ordinal = 1,
                    ^///?}
                    shift = At.Shift.AFTER
            )
    )
    private void sw$copyMatrices(CallbackInfo ci) {
        ModelViewMatrix.set(RenderSystem.getModelViewMatrix());
        ProjectionMatrix.set(RenderSystem.getProjectionMatrix());
    }
    *///?}
}
