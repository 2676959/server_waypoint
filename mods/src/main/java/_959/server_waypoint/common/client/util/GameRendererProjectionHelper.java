package _959.server_waypoint.common.client.util;

//? if >= 26.1 {
import _959.server_waypoint.mixin.GameRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
//?}

public final class GameRendererProjectionHelper {
    private GameRendererProjectionHelper() {
    }

    //? if >= 26.1 {
    public static Matrix4f getFinalLevelProjectionMatrix(GameRenderer gameRenderer,
                                                         DeltaTracker deltaTracker,
                                                         CameraRenderState cameraState) {
        GameRendererAccessor accessor = (GameRendererAccessor) gameRenderer;
        GameRenderState gameRenderState = accessor.serverWaypoint$getGameRenderState();
        OptionsRenderState optionsState = gameRenderState.optionsRenderState;
        Matrix4f projectionMatrix = new Matrix4f(cameraState.projectionMatrix);
        PoseStack bobStack = new PoseStack();
        accessor.serverWaypoint$bobHurt(cameraState, bobStack);
        if (optionsState.bobView) {
            accessor.serverWaypoint$bobView(cameraState, bobStack);
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
            float angle = (accessor.serverWaypoint$getSpinningEffectTime() + worldPartialTicks * accessor.serverWaypoint$getSpinningEffectSpeed())
                    * ((float) Math.PI / 180F);
            projectionMatrix.rotate(angle, axis);
            projectionMatrix.scale(1.0F / skew, 1.0F, 1.0F);
            projectionMatrix.rotate(-angle, axis);
        }

        return projectionMatrix;
    }
    //?}
}
