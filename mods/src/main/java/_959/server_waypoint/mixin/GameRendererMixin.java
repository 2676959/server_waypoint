package _959.server_waypoint.mixin;

import net.minecraft.client.renderer.GameRenderer;
//? if >= 26.1 {
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.GameRendererProjectionHelper;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    //? if >= 26.1 {
    //? if < 26.2 {
    /*@Inject(
            method = "extractGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"),
            remap = false
    )
    private void sw$renderWaypoints(DeltaTracker deltaTracker,
                                    boolean shouldRenderLevel,
                                    boolean resourcesLoaded,
                                    CallbackInfo ci,
                                    @Local(name = "graphics") GuiGraphicsExtractor graphics) {
        GameRenderState gameRenderState = ((GameRendererAccessor) this).serverWaypoint$getGameRenderState();
        CameraRenderState cameraState = gameRenderState.levelRenderState.cameraRenderState;
        Matrix4f projectionMatrix = GameRendererProjectionHelper.getFinalLevelProjectionMatrix(
                (GameRenderer) (Object) this,
                deltaTracker,
                cameraState
        );
        OptimizedWaypointRenderer.updateCameraSnapshot(cameraState.pos, cameraState.viewRotationMatrix, projectionMatrix);
        OptimizedWaypointRenderer.render(graphics);
    }
    *///?}
    //?}
}
