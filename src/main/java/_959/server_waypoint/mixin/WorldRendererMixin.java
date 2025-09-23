package _959.server_waypoint.mixin;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V")
    )
    private void renderWaypoint(CallbackInfo ci, @Local(argsOnly = true) Camera camera, @Local MatrixStack matrixStack, @Local(argsOnly = true) RenderTickCounter renderTickCounter, @Local(ordinal = 0) VertexConsumerProvider.Immediate immediate) {
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
    }
}