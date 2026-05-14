package _959.server_waypoint.mixin;

import net.minecraft.client.renderer.GameRenderer;
//? if >= 26.1 {
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;
//?}

import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    //? if >= 26.1 {
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow private float spinningEffectTime;
    @Shadow private float spinningEffectSpeed;

    @Shadow
    private void bobHurt(CameraRenderState cameraState, PoseStack poseStack) {
    }

    @Shadow
    private void bobView(CameraRenderState cameraState, PoseStack poseStack) {
    }

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
        CameraRenderState cameraState = gameRenderState.levelRenderState.cameraRenderState;
        Matrix4f projectionMatrix = sw$getFinalLevelProjectionMatrix(deltaTracker, cameraState);
        OptimizedWaypointRenderer.updateCameraSnapshot(cameraState.pos, cameraState.viewRotationMatrix, projectionMatrix);
        OptimizedWaypointRenderer.render(graphics);
    }

    private Matrix4f sw$getFinalLevelProjectionMatrix(DeltaTracker deltaTracker, CameraRenderState cameraState) {
        Matrix4f projectionMatrix = new Matrix4f(cameraState.projectionMatrix);
        OptionsRenderState optionsState = gameRenderState.optionsRenderState;
        PoseStack bobStack = new PoseStack();
        this.bobHurt(cameraState, bobStack);
        if (optionsState.bobView) {
            this.bobView(cameraState, bobStack);
        }

        projectionMatrix.mul(bobStack.last().pose());
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return projectionMatrix;
        }

        float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        float screenEffectScale = optionsState.screenEffectScale;
        float portalIntensity = Mth.lerp(worldPartialTicks, player.oPortalEffectIntensity, player.portalEffectIntensity);
        float nauseaIntensity = player.getEffectBlendFactor(MobEffects.NAUSEA, worldPartialTicks);
        float spinningEffectIntensity = Math.max(portalIntensity, nauseaIntensity) * screenEffectScale * screenEffectScale;
        if (spinningEffectIntensity > 0.0F) {
            float skew = 5.0F / (spinningEffectIntensity * spinningEffectIntensity + 5.0F) - spinningEffectIntensity * 0.04F;
            skew *= skew;
            Vector3f axis = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float angle = (this.spinningEffectTime + worldPartialTicks * this.spinningEffectSpeed) * ((float) Math.PI / 180F);
            projectionMatrix.rotate(angle, axis);
            projectionMatrix.scale(1.0F / skew, 1.0F, 1.0F);
            projectionMatrix.rotate(-angle, axis);
        }

        return projectionMatrix;
    }
    //?}
}
