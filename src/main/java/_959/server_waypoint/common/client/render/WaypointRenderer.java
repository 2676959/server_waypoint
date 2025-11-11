package _959.server_waypoint.common.client.render;

import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class WaypointRenderer {
    public static final List<WaypointRenderData> WaypointsOnHud = new CopyOnWriteArrayList<>();
    public static final AtomicReference<Matrix4f> ModelViewMatrix = new AtomicReference<>();
    public static final AtomicReference<Matrix4f> ProjectionMatrix = new AtomicReference<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final TextRenderer textRenderer = mc.textRenderer;
    private static final Window window = mc.getWindow();
    private static final Camera camera = mc.gameRenderer.getCamera();
    private static final Matrix4f Identity = new Matrix4f();

    static {
        WaypointsOnHud.add(new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全"));
        WaypointsOnHud.add(new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP"));
    }

    public static void addWaypointLists(List<WaypointList> waypointLists) {

    }

    public static void drawWaypointOnHud(Matrix4f matrix, float x, float y, String text, int color, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType textLayerType) {
//        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
//        buffer.vertex(matrix, -5F, -5F, 0F).color(color);
//        buffer.vertex(matrix, 5F, -5F, 0F).color(color);
//        buffer.vertex(matrix, 5F, 5F, 0F).color(color);
//        buffer.vertex(matrix, -5F, 5F, 0F).color(color);
//        int width = textRenderer.getWidth(text);
//        float y = textRenderer.getWrappedLinesHeight(text, width);
        textRenderer.draw(text, x, y,0xFFFFFFFF, false, matrix, vertexConsumers, textLayerType, color, 0xF000F0);
    }

    public static void renderOnHud(DrawContext context) {
        int scaledWidth = window.getScaledWidth();
        float windowCenterX = scaledWidth / 2F;
        int scaledHeight = window.getScaledHeight();
        float windowCenterY = scaledHeight / 2F;
        float guiScaleFactor = (float) window.getScaleFactor();
        int framebufferWidth = window.getFramebufferWidth();
        int framebufferHeight = window.getFramebufferHeight();
        Matrix4f modelMatrix = ModelViewMatrix.get();
        Matrix4f projectMatrix = ProjectionMatrix.get();
        Vec3d cameraPos = camera.getPos().negate();
        float camX = (float) cameraPos.x;
        float camY = (float) cameraPos.y;
        float camZ = (float) cameraPos.z;
        float projectionConstant = projectMatrix.m11();
        context.drawText(textRenderer, "ScaledW: %d, ScaledH: %d".formatted(scaledWidth, scaledHeight), 10, 20, 0xFFFFFFFF, true);
        context.drawText(textRenderer, "BufferW: %d, BufferH: %d".formatted(framebufferWidth, framebufferHeight), 10, 30, 0xFFFFFFFF, true);
        context.drawText(textRenderer, "ScaleFactor: %.2f".formatted(guiScaleFactor), 10, 40, 0xFFFFFFFF, true);
        float baseScale = 0.01F * framebufferHeight / guiScaleFactor;
        float projectionScale = baseScale * projectionConstant;
        float minBaseScale = baseScale / 5F;
        context.drawText(textRenderer, "ProjectionScale: %.2f".formatted(projectionScale), 10, 50, 0xFFFFFFFF, true);
        context.draw((immediate -> {
            for (WaypointRenderData waypointPos : WaypointsOnHud) {
                Vector4f pos = new Vector4f(waypointPos.pos(), 1F);
                pos.y += 0.5F;
                pos.add(camX, camY, camZ, 0F);
                double distance = Math.sqrt(pos.x * pos.x + pos.y * pos.y + pos.z * pos.z);
                pos.mul(modelMatrix);
                pos.mul(projectMatrix);
                float depth = pos.w();
                if (depth > 0) {
                    pos.div(depth);
                } else {
                    continue;
                }
                // ndc space
                float x = pos.x();
                float y = pos.y();
                float z = -pos.z();

                // window space
                float wx = (x + 1) * windowCenterX;
                float wy = (1 - y) * windowCenterY;

                // scale with perspective
                float scale = projectionScale / depth;
                if (scale < minBaseScale) {
                    scale = minBaseScale;
                }
                textRenderer.draw("RenderScale: %.2f".formatted(scale), 10f, 60f, 0xFFFFFFFF, true, Identity, immediate, TextRenderer.TextLayerType.NORMAL, 0, 0xFF);

                // show initials
                String displayText = waypointPos.initials();
                int textWidth = textRenderer.getWidth(displayText);
                int textHeight = textRenderer.getWrappedLinesHeight(displayText, Integer.MAX_VALUE);

                // text background
                float scaledRealTextWidth = (textWidth + 1) * scale;
                float scaledRealTextHeight = (textHeight + 1) * scale;
                float upperCornerX = wx - (scaledRealTextWidth / 2F);
                float upperCornerY = wy - scaledRealTextHeight;
                float lowerCornerX = upperCornerX + scaledRealTextWidth;
                float lowerCornerY = upperCornerY + scaledRealTextHeight;

                // centering text
                float tx = wx - ((textWidth - 1) * scale / 2F);
                float ty = wy - textHeight * scale;

                // color and transparency
                int rgb = waypointPos.rgb();
                int alpha = 0x80000000;

                // change to name when hovering on initials
                if (isIn2DBox(windowCenterX, windowCenterY, upperCornerX, upperCornerY, lowerCornerX, lowerCornerY)) {
                    // set on top
                    z = 0F;
                    alpha = 0xBB000000;
                    displayText = waypointPos.name();
                    // only update width, assuming height does not change
                    textWidth = textRenderer.getWidth(displayText);
                    tx = wx - ((textWidth - 1) * scale / 2F);
                    // distance text
                    String distanceText;
                    if (distance >= 1000) {
                        distanceText = String.format("%.1fkm", distance / 1000);
                    } else {
                        distanceText = String.format("%.1fm", distance);
                    }
                    float distanceTextScale = scale / 1.25F;
                    Matrix4f matrix = new Matrix4f().translation(tx - 0.2F * scale, wy + distanceTextScale, z).scale(distanceTextScale);
                    // draw distance
                    drawWaypointOnHud(matrix, 0, 0, distanceText, 0x80000000, immediate, TextRenderer.TextLayerType.NORMAL);
                    // text y position for waypoint name text
                }
                Matrix4f matrix = new Matrix4f().translation(tx, ty, z).scale(scale);
                // draw waypoint
                drawWaypointOnHud(matrix, 0, 0, displayText, alpha + rgb, immediate, TextRenderer.TextLayerType.NORMAL);
            }
        }));
        context.drawItem(new ItemStack(Items.ACACIA_BOAT, 65), 5, 5);
    }

    private static boolean isIn2DBox(float x, float y, float min_x, float min_y, float max_x, float max_y) {
        return (min_x <= x) && (x <= max_x) && (min_y <= y) && (y <= max_y);
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        renderOnHud(context);
    }
}
