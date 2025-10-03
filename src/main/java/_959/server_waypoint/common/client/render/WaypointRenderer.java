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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class WaypointRenderer {
    public static final List<WaypointData> WaypointOnHud = new CopyOnWriteArrayList<>();
    public static final AtomicReference<Matrix4f> ModelViewMatrix = new AtomicReference<>();
    public static final AtomicReference<Matrix4f> ProjectionMatrix = new AtomicReference<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final TextRenderer textRenderer = mc.textRenderer;
    private static final Window window = mc.getWindow();
    private static final Camera camera = mc.gameRenderer.getCamera();
    private static final Matrix4f Identity =  new Matrix4f();

    static {
        WaypointOnHud.add(new WaypointData(new Vector3f(1.5F, 3F, 1.5F), 0xFFAACCFF, "Test1", "T1"));
        WaypointOnHud.add(new WaypointData(new Vector3f(1.5F, 0F, 1.5F), 0xFF00FF00, "Test2", "T2"));
    }

    public static void addWaypointLists(List<WaypointList> waypointLists) {

    }

    public static void drawWaypointOnHud(Matrix4f matrix, float x, float y, String text, int color, VertexConsumerProvider vertexConsumers) {
//        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
//        buffer.vertex(matrix, -5F, -5F, 0F).color(color);
//        buffer.vertex(matrix, 5F, -5F, 0F).color(color);
//        buffer.vertex(matrix, 5F, 5F, 0F).color(color);
//        buffer.vertex(matrix, -5F, 5F, 0F).color(color);
//        int width = textRenderer.getWidth(text);
//        float y = textRenderer.getWrappedLinesHeight(text, width);
        textRenderer.draw(text, x, y,0xFFFFFFFF, false, matrix, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, color, 0xF000F0);
    }

    public static void renderOnHud(DrawContext context) {
        float winHalfWidth = window.getScaledWidth() / 2f;
        float winHalfHeight = window.getScaledHeight() / 2f;
        Matrix4f modelMatrix = ModelViewMatrix.get();
        Matrix4f projectMatrix = ProjectionMatrix.get();
        Vec3d cameraPos = camera.getPos().negate();
        float camX = (float) cameraPos.x;
        float camY = (float) cameraPos.y;
        float camZ = (float) cameraPos.z;
        context.draw((immediate -> {
            for (WaypointData waypointPos : WaypointOnHud) {
                Vector4f pos = new Vector4f(waypointPos.pos(), 1F);
                pos.add(camX, camY, camZ, 0F);
                pos.mul(modelMatrix);
                pos.mul(projectMatrix);
                float depth = pos.w();
                if (depth != 0) {
                    pos.div(depth);
                }
                // normalized camera projection space
                float x = pos.x();
                float y = pos.y();
                // window space
                float dx = (x + 1) * winHalfWidth;
                float dy = (1 - y) * winHalfHeight;
                String displayText = waypointPos.initials();
                int textWidth = textRenderer.getWidth(displayText);
                int textHeight = textRenderer.getWrappedLinesHeight(displayText, textWidth);
                float xRange = textWidth / winHalfWidth;
                float yRange = textHeight / winHalfHeight / 2;
                if (isInAbsBox(x, y + yRange, xRange, yRange)) {
                    displayText = waypointPos.name();
                    textWidth = textRenderer.getWidth(displayText);
                    textHeight = textRenderer.getWrappedLinesHeight(displayText, textWidth);
                }
                drawWaypointOnHud(Identity, dx -(textWidth / 2F), dy - textHeight, displayText, waypointPos.color(), immediate);
            }
        }));
        context.drawItem(new ItemStack(Items.ACACIA_BOAT, 65), 5, 5);
    }

    private static boolean isInAbsBox(float x, float y, float xCeil, float yCeil) {
        float absX = Math.abs(x);
        float absY = Math.abs(y);
        return absX < xCeil && absY < yCeil;
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        renderOnHud(context);
    }
}
