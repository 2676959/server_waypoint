package _959.server_waypoint.common.client.gui;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class DrawContextHelper {
    public static void texture(GuiGraphics context, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if > 1.21 {
        /*context.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        context.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?}
    }

    @SuppressWarnings("deprecation")
    public static void withVertexConsumers(GuiGraphics context, Consumer<MultiBufferSource> consumer) {
        //? if > 1.21 {
        /*context.drawSpecial(consumer);
        *///?} else {
        context.drawManaged(() -> consumer.accept(context.bufferSource()));
        //?}
    }

    public static void vertex(VertexConsumer vertexConsumer, Matrix4f matrix, float x, float y, float z, int color) {
        //? if > 1.20.6 {
        vertexConsumer.addVertex(matrix, x, y, z).setColor(color);
        //?} else {
        /*vertexConsumer.vertex(matrix, x, y, z).color(color).next();
        *///?}
    }
}
