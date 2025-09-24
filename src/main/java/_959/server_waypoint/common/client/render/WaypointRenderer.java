package _959.server_waypoint.common.client.render;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import static _959.server_waypoint.fabric.ServerWaypointFabricClient.*;

public class WaypointRenderer {
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MatrixStack matrixStack = context.getMatrices();
        matrixStack.push();
        Window window = MinecraftClient.getInstance().getWindow();
        float width = window.getScaledWidth() / 2f;
        float height = window.getScaledHeight() / 2f;
        matrixStack.translate((x.get() + 1) * width, (1 - y.get()) * height, 0);
//            matrixStack.multiplyPositionMatrix(new Matrix4f(
//                    1, 0, 0, 0,
//                    0, 0, 0, 0,
//                    0, 1, 0, 0,
//                    0, 0, 0, 0));
//            matrixStack.multiply(camera.getRotation());
//            matrixStack.translate(width / 2F, 0, -height / 2F);
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        context.draw((immediate -> {
            VertexConsumer buffer = immediate.getBuffer(RenderLayer.getDebugQuads());
            buffer.vertex(matrix, 0F, 0F, 0F).color(0xFFAABBCC);
            buffer.vertex(matrix, 10F, 0F, 0F).color(0xFFAABBCC);
            buffer.vertex(matrix, 10F, 10F, 0F).color(0xFFAABBCC);
            buffer.vertex(matrix, 0F, 10F, 0F).color(0xFFAABBCC);
        }));
        matrixStack.pop();
        context.drawItem(new ItemStack(Items.ACACIA_BOAT, 65), 5, 5);
    }
}
