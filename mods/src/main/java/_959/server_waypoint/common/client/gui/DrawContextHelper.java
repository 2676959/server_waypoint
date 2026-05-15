//~ resource_location_import
//~ gui_render_state_26
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >= 1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
//?}
import net.minecraft.client.renderer.MultiBufferSource;
//? if < 1.21.6
/*import net.minecraft.client.renderer.RenderType;*/
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class DrawContextHelper {
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    public static void texture(GuiGraphicsExtractor context,
    //$ resource_location_type_swap
    Identifier
    texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if >= 1.21.6 {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?} elif > 1.21 {
        /*context.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        /*context.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?}
    }

    public static void push(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        context.pose().pushMatrix();
        //?} else {
        /*context.pose().pushPose();
        *///?}
    }

    public static void pop(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        context.pose().popMatrix();
        //?} else {
        /*context.pose().popPose();
        *///?}
    }

    public static void translate(GuiGraphicsExtractor context, float x, float y) {
        //? if >= 1.21.6 {
        context.pose().translate(x, y);
        //?} else {
        /*context.pose().translate(x, y, 0.0F);
        *///?}
    }

    public static void scale(GuiGraphicsExtractor context, float x, float y) {
        //? if >= 1.21.6 {
        context.pose().scale(x, y);
        //?} else {
        /*context.pose().scale(x, y, 1.0F);
        *///?}
    }

    public static void nextLayer(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        context.nextStratum();
        //?} else {
        /*context.pose().translate(0.0F, 0.0F, 1.0F);
        *///?}
    }

    public static void previousLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -1.0F);
        *///?}
    }

    public static void renderOutline(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        //? if = 1.21.9 {
        /*renderOutlineWithFill(context, x, y, width, height, color);
        *///?} else {
        context.
        //$ gui_outline_method_swap
        outline
                (x, y, width, height, color);
        //?}
    }

    private static void renderOutlineWithFill(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static Matrix4f currentMatrix(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        return IDENTITY_MATRIX;
        //?} else {
        /*return context.pose().last().pose();
        *///?}
    }

    @SuppressWarnings("deprecation")
    public static void withVertexConsumers(GuiGraphicsExtractor context, Consumer<MultiBufferSource> consumer) {
        //? if >= 1.21.6 {
        MultiBufferSource.BufferSource immediate = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        consumer.accept(immediate);
        immediate.endBatch();
        //?} elif > 1.21 {
        /*context.drawSpecial(consumer);
        *///?} else {
        /*context.drawManaged(() -> consumer.accept(context.bufferSource()));
        *///?}
    }

    public static void drawHorizontalGradient(GuiGraphicsExtractor context, float left, float top, float right, float bottom, int leftColor, int rightColor) {
        drawColorGradient(context, left, top, right, bottom, leftColor, leftColor, rightColor, rightColor);
    }

    public static void drawColorGradient(
            GuiGraphicsExtractor context,
            float left,
            float top,
            float right,
            float bottom,
            int topLeftColor,
            int bottomLeftColor,
            int bottomRightColor,
            int topRightColor
    ) {
        //? if >= 1.21.6 {
        //? if neoforge {
        /*context.submitGuiElementRenderState(new ColoredQuadRenderState(
                RenderPipelines.GUI,
                TextureSetup.noTexture(),
                new Matrix3x2f(context.pose()),
                left, top,
                left, bottom,
                right, bottom,
                right, top,
                topLeftColor, bottomLeftColor, bottomRightColor, topRightColor,
                context.peekScissorStack()
        ));
        *///?} else {
        ColoredQuadRenderState renderState = new ColoredQuadRenderState(
                RenderPipelines.GUI,
                TextureSetup.noTexture(),
                new Matrix3x2f(context.pose()),
                left, top,
                left, bottom,
                right, bottom,
                right, top,
                topLeftColor, bottomLeftColor, bottomRightColor, topRightColor,
                context.scissorStack.peek()
        );
        //? if >= 26.1 {
        context.guiRenderState.addGuiElement(renderState);
        //?} else {
        /*context.guiRenderState.submitGuiElement(renderState);
        *///?}
        //?}
        //?} else {
        /*withVertexConsumers(context, vertexConsumerProvider -> {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderType.gui());
            Matrix4f matrix = currentMatrix(context);
            addColoredVertex(vertexConsumer, matrix, left, top, 0, topLeftColor);
            addColoredVertex(vertexConsumer, matrix, left, bottom, 0, bottomLeftColor);
            addColoredVertex(vertexConsumer, matrix, right, bottom, 0, bottomRightColor);
            addColoredVertex(vertexConsumer, matrix, right, top, 0, topRightColor);
        });
        *///?}
    }

    private static void addColoredVertex(VertexConsumer vertexConsumer, Matrix4f matrix, float x, float y, float z, int color) {
        //? if > 1.20.6 {
        vertexConsumer.addVertex(matrix, x, y, z).setColor(color);
        //?} else {
        /*vertexConsumer.vertex(matrix, x, y, z).color(color).endVertex();
        *///?}
    }

    //? if >= 1.21.6 {
    private record ColoredQuadRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            int color0,
            int color1,
            int color2,
            int color3,
            @Nullable ScreenRectangle scissorArea
    ) implements GuiElementRenderState {
        @Override
        //? if >= 1.21.9 {
        public void buildVertices(VertexConsumer vertexConsumer) {
            vertexConsumer.addVertexWith2DPose(this.pose, this.x0, this.y0).setColor(this.color0);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setColor(this.color1);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x2, this.y2).setColor(this.color2);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x3, this.y3).setColor(this.color3);
        }
        //?} else {
        /*public void buildVertices(VertexConsumer vertexConsumer, float depth) {
            vertexConsumer.addVertexWith2DPose(this.pose, this.x0, this.y0, depth).setColor(this.color0);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x1, this.y1, depth).setColor(this.color1);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x2, this.y2, depth).setColor(this.color2);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x3, this.y3, depth).setColor(this.color3);
        }
        *///?}

        @Override
        public @Nullable ScreenRectangle bounds() {
            float minX = Math.min(Math.min(this.x0, this.x1), Math.min(this.x2, this.x3));
            float minY = Math.min(Math.min(this.y0, this.y1), Math.min(this.y2, this.y3));
            float maxX = Math.max(Math.max(this.x0, this.x1), Math.max(this.x2, this.x3));
            float maxY = Math.max(Math.max(this.y0, this.y1), Math.max(this.y2, this.y3));
            ScreenRectangle bounds = new ScreenRectangle((int) minX, (int) minY, (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY)).transformMaxBounds(this.pose);
            return this.scissorArea == null ? bounds : this.scissorArea.intersection(bounds);
        }
    }
    //?}
}
