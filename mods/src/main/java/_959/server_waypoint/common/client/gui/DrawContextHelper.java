package _959.server_waypoint.common.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
//? if >= 1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
//?}
import net.minecraft.client.renderer.MultiBufferSource;
//? if < 1.21.6
/*import net.minecraft.client.renderer.RenderType;*/
//? if >= 1.21.11
/*import net.minecraft.resources.Identifier;*/
//? if < 1.21.11
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class DrawContextHelper {
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    public static void texture(GuiGraphics context, /*? if < 1.21.11 {*/ResourceLocation/*?} else {*/ /*Identifier *//*?}*/ texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if >= 1.21.6 {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?} elif > 1.21 {
        /*context.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        /*context.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?}
    }

    public static void push(GuiGraphics context) {
        //? if >= 1.21.6 {
        context.pose().pushMatrix();
        //?} else {
        /*context.pose().pushPose();
        *///?}
    }

    public static void pop(GuiGraphics context) {
        //? if >= 1.21.6 {
        context.pose().popMatrix();
        //?} else {
        /*context.pose().popPose();
        *///?}
    }

    public static void translate(GuiGraphics context, float x, float y) {
        //? if >= 1.21.6 {
        context.pose().translate(x, y);
        //?} else {
        /*context.pose().translate(x, y, 0.0F);
        *///?}
    }

    public static void scale(GuiGraphics context, float x, float y) {
        //? if >= 1.21.6 {
        context.pose().scale(x, y);
        //?} else {
        /*context.pose().scale(x, y, 1.0F);
        *///?}
    }

    public static void nextLayer(GuiGraphics context) {
        //? if >= 1.21.6 {
        context.nextStratum();
        //?} else {
        /*context.pose().translate(0.0F, 0.0F, 1.0F);
        *///?}
    }

    public static void previousLayer(GuiGraphics context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -1.0F);
        *///?}
    }

    public static void renderOutline(GuiGraphics context, int x, int y, int width, int height, int color) {
        //? if = 1.21.9 {
        /*renderOutlineWithFill(context, x, y, width, height, color);
        *///?} else {
        context.renderOutline(x, y, width, height, color);
        //?}
    }

    private static void renderOutlineWithFill(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static Matrix4f currentMatrix(GuiGraphics context) {
        //? if >= 1.21.6 {
        return IDENTITY_MATRIX;
        //?} else {
        /*return context.pose().last().pose();
        *///?}
    }

    public static void withGuiVertices(GuiGraphics context, Consumer<VertexConsumer> consumer) {
        //? if >= 1.21.6 {
        consumer.accept(new GuiQuadVertexConsumer(context));
        //?} else {
        /*withVertexConsumers(context, vertexConsumerProvider -> consumer.accept(vertexConsumerProvider.getBuffer(RenderType.gui())));
        *///?}
    }

    @SuppressWarnings("deprecation")
    public static void withVertexConsumers(GuiGraphics context, Consumer<MultiBufferSource> consumer) {
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

    public static void vertex(VertexConsumer vertexConsumer, Matrix4f matrix, float x, float y, float z, int color) {
        //? if > 1.20.6 {
        vertexConsumer.addVertex(matrix, x, y, z).setColor(color);
        //?} else {
        /*vertexConsumer.vertex(matrix, x, y, z).color(color).endVertex();
        *///?}
    }

    //? if >= 1.21.6 {
    private static final class GuiQuadVertexConsumer implements VertexConsumer {
        private final GuiGraphics context;
        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final int[] color = new int[4];
        private int index;

        private GuiQuadVertexConsumer(GuiGraphics context) {
            this.context = context;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x[this.index] = x;
            this.y[this.index] = y;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.color[this.index] = alpha << 24 | red << 16 | green << 8 | blue;
            this.index++;
            if (this.index == 4) {
                this.flush();
                this.index = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.color[this.index] = color;
            this.index++;
            if (this.index == 4) {
                this.flush();
                this.index = 0;
            }
            return this;
        }

        private void flush() {
            //? if neoforge {
            /*this.context.submitGuiElementRenderState(new ColoredQuadRenderState(
                    RenderPipelines.GUI,
                    TextureSetup.noTexture(),
                    new Matrix3x2f(this.context.pose()),
                    this.x[0], this.y[0],
                    this.x[1], this.y[1],
                    this.x[2], this.y[2],
                    this.x[3], this.y[3],
                    this.color[0], this.color[1], this.color[2], this.color[3],
                    this.context.peekScissorStack()
            ));
            *///?} else {
            this.context.guiRenderState.submitGuiElement(new ColoredQuadRenderState(
                    RenderPipelines.GUI,
                    TextureSetup.noTexture(),
                    new Matrix3x2f(this.context.pose()),
                    this.x[0], this.y[0],
                    this.x[1], this.y[1],
                    this.x[2], this.y[2],
                    this.x[3], this.y[3],
                    this.color[0], this.color[1], this.color[2], this.color[3],
                    this.context.scissorStack.peek()
            ));
            //?}
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        //? if >= 1.21.11
        /*@Override*/
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

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
        /*public void buildVertices(VertexConsumer vertexConsumer) {
            vertexConsumer.addVertexWith2DPose(this.pose, this.x0, this.y0).setColor(this.color0);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setColor(this.color1);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x2, this.y2).setColor(this.color2);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x3, this.y3).setColor(this.color3);
        }
        *///?} else {
        public void buildVertices(VertexConsumer vertexConsumer, float depth) {
            vertexConsumer.addVertexWith2DPose(this.pose, this.x0, this.y0, depth).setColor(this.color0);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x1, this.y1, depth).setColor(this.color1);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x2, this.y2, depth).setColor(this.color2);
            vertexConsumer.addVertexWith2DPose(this.pose, this.x3, this.y3, depth).setColor(this.color3);
        }
        //?}

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
