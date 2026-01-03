package _959.server_waypoint.mixin;

import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.render.WaypointRenderer.ModelViewMatrix;
import static _959.server_waypoint.common.client.render.WaypointRenderer.ProjectionMatrix;
import static _959.server_waypoint.fabric.ServerWaypointFabricClient.*;
import static net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer.BEAM_TEXTURE;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V")
    )
    private void renderWaypoint(CallbackInfo ci, @Local(argsOnly = true) Fog fog,  @Local(argsOnly = true) Camera camera, @Local MatrixStack matrixStack, @Local(argsOnly = true) RenderTickCounter renderTickCounter, @Local(ordinal = 0) VertexConsumerProvider.Immediate immediate) {
//        Tessellator tessellator = Tessellator.getInstance();
//        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        VertexConsumer bufferBuilder = immediate.getBuffer(RenderLayer.getDebugQuads());
        Vec3d cameraPos = camera.getPos();
        WaypointPos waypointPos = new WaypointPos(1, 1, 1);
        double X = waypointPos.X() - cameraPos.x;
        double Y = waypointPos.y() - cameraPos.y;
        double Z = waypointPos.Z() - cameraPos.z;
        int green_color = 0xFF00FF00;
        matrixStack.push();
        matrixStack.translate(X, Y, Z);

        matrixStack.multiply(camera.getRotation());
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        bufferBuilder.vertex(matrix, 0F, 0F, 0F).color(green_color);
        bufferBuilder.vertex(matrix, 1F, 0F, 0F).color(green_color);
        bufferBuilder.vertex(matrix, 1F, 1F, 0F).color(green_color);
        bufferBuilder.vertex(matrix, 0F, 1F, 0F).color(green_color);
        MinecraftClient client = MinecraftClient.getInstance();
        String text = "test";
        matrixStack.scale(0.1F, -0.1F, 1);
        client.textRenderer.draw(text, 0, 0, green_color, false, matrixStack.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0x55000000, 0xF000F0);
        immediate.draw();

        matrixStack.pop();
        matrixStack.push();
        matrixStack.translate(X, Y, Z);
        RenderSystem.setShaderFog(Fog.DUMMY);
        BeaconBlockEntityRenderer.renderBeam(matrixStack, immediate, BEAM_TEXTURE, 0, 1.0F, 18000, 0, 2048, 0xFFFFFFFF, 0.2F, 0.25F);
        matrixStack.pop();
        RenderSystem.setShaderFog(fog);
        ModelViewMatrix.set(RenderSystem.getModelViewMatrix());
        ProjectionMatrix.set(RenderSystem.getProjectionMatrix());
    }

//    @Inject(method = "render", at = @At(value = "HEAD"))
//    public void render(CallbackInfo ci) {
//
//    }
}