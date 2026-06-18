package _959.server_waypoint.mixin;

//? if >= 26.2 {
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.GameRendererProjectionHelper;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
//? if < 26.1 {
/*//? if > 1.20.6
import net.minecraft.client.DeltaTracker;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.Gui;

@Mixin(Gui.class)
public class GameHudMixin {
    //? if < 26.1 {
    /*@Inject(
            method = /^? if <26 {^//^"render"^//^?} else {^/"extractRenderState"/^?}^/,
            at = @At(value = "HEAD")
    )
    public void sw$renderWaypoints(
            GuiGraphics context,
            //? if > 1.20.6 {
            DeltaTracker tickCounter,
            //?} else {
            /^float tickDelta,
            ^///?}
            CallbackInfo ci
    ) {
        //? if !(neoforge && <= 1.20.4) {
        OptimizedWaypointRenderer.render(context);
        //?}
    }
    *///?}
    //? if >= 26.2 {
    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
            ),
            remap = false
    )
    private void sw$renderWaypoints(DeltaTracker deltaTracker,
                                    boolean shouldRenderLevel,
                                    boolean resourcesLoaded,
                                    CallbackInfo ci,
                                    @Local(name = "graphics") GuiGraphicsExtractor graphics) {
        if (!shouldRenderLevel) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GameRenderState gameRenderState = ((GameRendererAccessor) minecraft.gameRenderer).serverWaypoint$getGameRenderState();
        CameraRenderState cameraState = gameRenderState.levelRenderState.cameraRenderState;
        Matrix4f projectionMatrix = GameRendererProjectionHelper.getFinalLevelProjectionMatrix(
                minecraft.gameRenderer,
                deltaTracker,
                cameraState
        );
        OptimizedWaypointRenderer.updateCameraSnapshot(
                cameraState.pos,
                cameraState.viewRotationMatrix,
                projectionMatrix
        );
        OptimizedWaypointRenderer.render(graphics);
    }
    //?}
}
