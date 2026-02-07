package _959.server_waypoint.common.client.gui;

//? if > 1.20.1
import net.minecraft.client.render.RenderLayer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public final class DrawContextHelper {
    public static void texture(DrawContext context, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if > 1.20.1 {
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?} else {
        /*context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?}
    }

    @SuppressWarnings("deprecation")
    public static void withVertexConsumers(DrawContext context, Consumer<VertexConsumerProvider> consumer) {
        //? if > 1.20.1 {
        context.draw(consumer);
        //?} else {
        /*context.draw(() -> consumer.accept(context.getVertexConsumers()));
        *///?}
    }

    public static void vertex(VertexConsumer vertexConsumer, Matrix4f matrix, float x, float y, float z, int color) {
        //? if > 1.20.1 {
        vertexConsumer.vertex(matrix, x, y, z).color(color);
        //?} else {
        /*vertexConsumer.vertex(matrix, x, y, z).color(color).next();
        *///?}
    }
}
