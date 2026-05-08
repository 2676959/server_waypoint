package _959.server_waypoint.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ModelViewMatrix;
import static _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.ProjectionMatrix;

//? if >= 1.21.6 {
import org.joml.Vector4f;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
//?} else {
/*import net.minecraft.client.renderer.GameRenderer;
*///?}

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    @Inject(
            at = {@At("HEAD")},
            method = {"renderLevel"}
    )
    public void
    sw$copyMatrices(
    //? if >= 1.21.11 {
    GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f cullingProjectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogVector, boolean skyPass, CallbackInfo info
    //?} elif >= 1.21.6 {
    /*GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogVector, boolean skyPass, CallbackInfo info
    *///?} else {
    /*GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, CallbackInfo info
    *///?}
    )
    {
        ProjectionMatrix.set(projectionMatrix);
        ModelViewMatrix.set(modelViewMatrix);
    }
}
