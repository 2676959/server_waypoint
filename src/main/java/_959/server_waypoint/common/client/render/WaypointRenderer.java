package _959.server_waypoint.common.client.render;

import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WaypointRenderer {
    public static final List<WaypointRenderData> WaypointsOnHud = new CopyOnWriteArrayList<>();
    public static final Matrix4f ModelViewMatrix = new Matrix4f();
    public static final Matrix4f ProjectionMatrix = new Matrix4f();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final TextRenderer textRenderer = mc.textRenderer;
    private static final Window window = mc.getWindow();
    private static final Camera camera = mc.gameRenderer.getCamera();
    private static final Matrix4f identity = new Matrix4f();
    private static final Vector4f posVec = new Vector4f();

    public static void addWaypointLists(List<WaypointList> waypointLists) {

    }

    public static void drawWaypointOnHud(Matrix4f matrix, float x, float y, String text, int color, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType textLayerType) {
        textRenderer.draw(text, x, y,0xFFFFFFFF, false, matrix, vertexConsumers, textLayerType, color, 0xF000F0);
    }

    public static void renderOnHud(DrawContext context) {
        int scaledWidth = window.getScaledWidth();
        float windowCenterX = scaledWidth / 2F;
        int scaledHeight = window.getScaledHeight();
        float windowCenterY = scaledHeight / 2F;
        float guiScaleFactor = (float) window.getScaleFactor();
        int framebufferHeight = window.getFramebufferHeight();
        Vec3d cameraPos = camera.getPos().negate();
        float camX = (float) cameraPos.x;
        float camY = (float) cameraPos.y;
        float camZ = (float) cameraPos.z;
        float projectionConstant = ProjectionMatrix.m11();
        float baseScale = 0.01F * framebufferHeight / guiScaleFactor;
        float projectionScale = baseScale * projectionConstant;
        float minBaseScale = baseScale / 5F;
        context.draw(immediate -> {
            for (WaypointRenderData waypointData : WaypointsOnHud) {
                Vector4f pos = posVec.set(waypointData.pos(), 1F);
                pos.y += 0.5F;
                pos.add(camX, camY, camZ, 0F);
                pos.mul(ModelViewMatrix);
                pos.mul(ProjectionMatrix);
                float depth = pos.w();
                if (depth > 0) {
                    pos.div(depth);
                } else continue;
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

                // show initials
                String displayText = waypointData.initials();
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
                int rgb = waypointData.rgb();
                int alpha = 0x80000000;

                // change to name when hovering on initials
                if (isIn2DBox(windowCenterX, windowCenterY, upperCornerX, upperCornerY, lowerCornerX, lowerCornerY)) {
                    Vector4f relativePos = posVec.set(waypointData.pos(), 1F);
                    relativePos.y += 0.5F;
                    relativePos.add(camX, camY, camZ, 0F);
                    double distance = Math.sqrt(relativePos.x * relativePos.x + relativePos.y * relativePos.y + relativePos.z * relativePos.z);
                    // set on top
                    z = 0F;
                    alpha = 0xBB000000;
                    displayText = waypointData.name();
                    // only update width, assuming height does not change
                    textWidth = textRenderer.getWidth(displayText);
                    tx = wx - ((textWidth - 1) * scale / 2F);
                    // distance text
                    String distanceText;
                    if (distance >= 1000) {
                        distanceText = (Math.round(distance / 100.0) / 10.0) + "km";
                    } else {
                        distanceText = (Math.round(distance * 10.0) / 10.0) + "m";
                    }
                    float distanceTextScale = scale / 1.25F;
                    Matrix4f matrix = identity.translation(tx - 0.2F * scale, wy + distanceTextScale, z).scale(distanceTextScale);
                    // draw distance
                    drawWaypointOnHud(matrix, 0, 0, distanceText, 0x80000000, immediate, TextRenderer.TextLayerType.NORMAL);
                    // text y position for waypoint name text
                    identity.identity();
                }
                Matrix4f matrix = identity.translation(tx, ty, z).scale(scale);
                // draw waypoint
                drawWaypointOnHud(matrix, 0, 0, displayText, alpha + rgb, immediate, TextRenderer.TextLayerType.NORMAL);
                identity.identity();
            }
        });
    }

    private static boolean isIn2DBox(float x, float y, float min_x, float min_y, float max_x, float max_y) {
        return (min_x <= x) && (x <= max_x) && (min_y <= y) && (y <= max_y);
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        renderOnHud(context);
    }
}
