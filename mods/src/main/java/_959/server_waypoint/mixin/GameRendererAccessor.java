package _959.server_waypoint.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

//? if >= 26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
//?}

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    //? if >= 26.1 {
    @Accessor(value = "gameRenderState", remap = false)
    GameRenderState serverWaypoint$getGameRenderState();

    @Invoker(value = "bobHurt", remap = false)
    void serverWaypoint$bobHurt(CameraRenderState cameraState, PoseStack poseStack);

    @Invoker(value = "bobView", remap = false)
    void serverWaypoint$bobView(CameraRenderState cameraState, PoseStack poseStack);

    @Accessor(value = "spinningEffectTime", remap = false)
    float serverWaypoint$getSpinningEffectTime();

    @Accessor(value = "spinningEffectSpeed", remap = false)
    float serverWaypoint$getSpinningEffectSpeed();
    //?}
}
