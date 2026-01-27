package _959.server_waypoint.common.client.render;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static _959.server_waypoint.util.ColorUtils.getSafeTextColor;
import static net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;

public class OptimizedWaypointRenderer {

    // =========================================================
    // CONFIGURATION
    // =========================================================
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_renderer");
    private static final int MAX_WAYPOINTS = 10000;
    private static final int MAX_RENDER_ID = 20000;

    // =========================================================
    // STATE (RENDER THREAD ONLY)
    // =========================================================
    private static boolean initialized = false;
    private static int count = 0;
    private static boolean IS_HOVERED = false;
    private static int HOVERED_ID = -1;

    // ID Generator (Managed synchronously on Logic Thread)
    private static int nextRenderId = 0;
    private static int[] idMap;

    // render commands
    private static final ConcurrentLinkedQueue<WaypointRendererCommand> queue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<WaypointRendererCommand> commandPool = new ConcurrentLinkedQueue<>();
    private static final AtomicReference<WaypointRendererCommand.Type> lastSentType = new AtomicReference<>(null);

    // --- Structure of Arrays (SoA) ---
    private static int[] ids;
    private static float[] xPos;
    private static float[] yPos;
    private static float[] zPos;
    private static int[] bgColor;
    private static int[] fgColor;
    private static String[] names;
    private static String[] initials;
    private static float[] nameTextWidth;
    private static float[] nameTextBgWidth;
    private static float[] initialsTextWidth;
    private static float[] initialsTextBgWidth;

    // =========================================================
    // MINECRAFT RENDERING CONTEXT
    // =========================================================
    public static final Matrix4f ModelViewMatrix = new Matrix4f();
    public static final Matrix4f ProjectionMatrix = new Matrix4f();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final TextRenderer textRenderer = mc.textRenderer;
    private static final int textHeight = textRenderer.fontHeight;
    private static final Window window = mc.getWindow();
    private static final Camera camera = mc.gameRenderer.getCamera();
    private static final Matrix4f identity = new Matrix4f();
    private static final Vector4f posVec = new Vector4f();

    // =========================================================
    // DATA TRANSFER OBJECTS
    // =========================================================
    private static class WaypointRendererCommand {
        enum Type { ADD, REMOVE, UPDATE, CLEAR_ALL, BULK_ADD, BULK_REMOVE }

        Type type;
        int renderId;
        float x, y, z;
        int bgColor;
        int fgColor;
        String name;
        String initials;
        float initialsWidth;
        float nameWidth;
        float initialsBgWidth;
        float nameBgWidth;

        SimpleWaypoint[] bulkWaypoints;
        int[] bulkIds;
        // only need foreground color here as waypoint color is the background color
        int[] bulkFgColor;
        float[] bulkNameWidth;
        float[] bulkNameBgWidth;
        float[] bulkInitialsWidth;
        float[] bulkInitialsBgWidth;
    }

    // =========================================================
    // 1. INITIALIZATION
    // =========================================================
    public static void init() {
        if (initialized) return;

        idMap = new int[MAX_RENDER_ID];
        Arrays.fill(idMap, -1);

        ids = new int[MAX_WAYPOINTS];
        xPos = new float[MAX_WAYPOINTS];
        yPos = new float[MAX_WAYPOINTS];
        zPos = new float[MAX_WAYPOINTS];
        bgColor = new int[MAX_WAYPOINTS];
        fgColor = new int[MAX_WAYPOINTS];
        names = new String[MAX_WAYPOINTS];
        initials = new String[MAX_WAYPOINTS];
        nameTextWidth = new float[MAX_WAYPOINTS];
        nameTextBgWidth = new float[MAX_WAYPOINTS];
        initialsTextWidth = new float[MAX_WAYPOINTS];
        initialsTextBgWidth = new float[MAX_WAYPOINTS];
        initialized = true;
        LOGGER.info("waypoint renderer initialized");
    }

    // =========================================================
    // 2. PUBLIC API (LOGIC THREAD)
    // =========================================================
    public static void clearScene() {
        // CHECK: Was the very last command also a CLEAR_ALL?
        if (lastSentType.get() == WaypointRendererCommand.Type.CLEAR_ALL) {
            //
            // If yes, we skip this one.
            // Result: The queue stays [..., CLEAR_ALL] instead of [..., CLEAR_ALL, CLEAR_ALL]
            return;
        }
        // 1. Reset the ID Counter
        nextRenderId = 0;

        // 2. Send Clear Command
        WaypointRendererCommand cmd = obtainCommand();
        cmd.type = WaypointRendererCommand.Type.CLEAR_ALL;
        offerCommand(cmd);
    }

    /**
     * Efficiently adds multiple WaypointLists in a single batch.
     * Only adds waypoints from lists where isShow() is true.
     */
    public static void loadScene(@Unmodifiable List<WaypointList> lists) {
        // 1. Estimate size to prevent ArrayList resizing overhead
        int estimatedSize = 0;
        for (WaypointList list : lists) {
            if (list.isShow()) {
                estimatedSize += list.simpleWaypoints().size();
            }
        }

        if (estimatedSize == 0) return;

        // 2. Flatten all visible lists into one collection
        // We use a raw array or ArrayList. ArrayList is easier here.
        SimpleWaypoint[] wps = new SimpleWaypoint[estimatedSize];
        int[] fgColors = new int[estimatedSize];
        float[] nameWidth = new float[estimatedSize];
        float[] initialsWidth = new float[estimatedSize];
        float[] nameBgWidth = new float[estimatedSize];
        float[] initialsBgWidth = new float[estimatedSize];

        int index = 0;
        for (WaypointList list : lists) {
            // SKIP hidden lists entirely
            if (!list.isShow()) continue;

            for (SimpleWaypoint wp : list.simpleWaypoints()) {
                // Assign ID if needed (Logic Side)
                generateBulkData(wps, fgColors, nameWidth, initialsWidth, nameBgWidth, initialsBgWidth, index, wp);
                index++;
            }
        }

        if (index == 0) return;

        // 3. Send Single Command
        sendBulkData(wps, fgColors, nameWidth, initialsWidth, nameBgWidth, initialsBgWidth);
    }

    /**
     * Adds the waypoint to the renderer and automatically assigns it an ID.
     */
    public static void add(SimpleWaypoint wp) {
        // Prevent adding the same object twice
        if (wp.renderId != -1) return;

        // 1. Assign ID immediately (Synchronous)
        int assignedId = nextRenderId++;

        // Safety check to prevent crash if running for too long without reset
        if (assignedId >= MAX_RENDER_ID) {
            LOGGER.error("Max Entity ID limit reached! Call clearScene() to reset.");
            return;
        }

        wp.renderId = assignedId;

        // 2. Send Command (Asynchronous)
        sendCommand(WaypointRendererCommand.Type.ADD, assignedId, wp.X(), wp.y(), wp.Z(), wp.rgb(), wp.name(), wp.initials());
    }

    public static void addList(@Unmodifiable List<SimpleWaypoint> newWaypoints) {
        if (newWaypoints.isEmpty()) return;

        int size = newWaypoints.size();
        SimpleWaypoint[] bulkData = new SimpleWaypoint[size];
        int[] fgColor = new int[size];
        float[] nameWidth = new float[size];
        float[] initialsWidth = new float[size];
        float[] nameBgWith = new float[size];
        float[] initialsBgWidth = new float[size];

        for (int i = 0; i < size ; i++) {
            SimpleWaypoint wp = newWaypoints.get(i);
            generateBulkData(bulkData, fgColor, nameWidth, initialsWidth, nameBgWith, initialsBgWidth, i, wp);
        }
        sendBulkData(bulkData, fgColor, nameWidth, initialsWidth, nameBgWith, initialsBgWidth);
    }

    private static void generateBulkData(SimpleWaypoint[] bulkData, int[] fgColor, float[] nameWidth, float[] initialsWidth, float[] nameBgWith, float[] initialsBgWidth, int i, SimpleWaypoint wp) {
        if (wp.renderId == -1) {
            wp.renderId = nextRenderId++;
        }
        bulkData[i] = wp;
        fgColor[i] = getSafeTextColor(wp.rgb());
        String name = wp.name();
        String initials1 = wp.initials();
        nameWidth[i] = getTextWidth(name);
        initialsWidth[i] = getTextWidth(initials1);
        nameBgWith[i] = getTextBgWidth(name);
        initialsBgWidth[i] = getTextBgWidth(initials1);
    }

    private static void sendBulkData(SimpleWaypoint[] bulkData,  int[] fgColor, float[] nameWidth, float[] initialsWidth, float[] nameBgWith, float[] initialsBgWidth) {
        WaypointRendererCommand cmd = obtainCommand();
        cmd.type = WaypointRendererCommand.Type.BULK_ADD;
        cmd.bulkWaypoints = bulkData;
        cmd.bulkFgColor = fgColor;
        cmd.bulkNameWidth = nameWidth;
        cmd.bulkInitialsWidth = initialsWidth;
        cmd.bulkNameBgWidth = nameBgWith;
        cmd.bulkInitialsBgWidth = initialsBgWidth;
        offerCommand(cmd);
    }

    /**
     * Removes the waypoint and resets its ID to -1.
     */
    public static void remove(SimpleWaypoint wp) {
        if (wp.renderId == -1) return; // Not in renderer

        // 1. Send Command using the stored ID
        sendCommand(WaypointRendererCommand.Type.REMOVE, wp.renderId, 0, 0, 0, 0, null, null);

        // 2. Reset ID immediately so Logic knows it's gone
        wp.renderId = -1;
    }

    /**
     * Efficiently removes a whole list of waypoints.
     */
    public static void removeList(List<SimpleWaypoint> list) {
        // Extract just the IDs to send to the Render Thread
        int[] idsToRemove = list.stream()
                .filter(wp -> wp.renderId != -1)
                .mapToInt(wp -> wp.renderId)
                .toArray();

        // Reset Logic IDs immediately so Logic knows they are hidden
        for (SimpleWaypoint wp : list) wp.renderId = -1;

        if (idsToRemove.length > 0) {
            WaypointRendererCommand cmd = obtainCommand();
            cmd.type = WaypointRendererCommand.Type.BULK_REMOVE;
            cmd.bulkIds = idsToRemove;
            offerCommand(cmd);
        }
    }

    public static void updateWaypoint(SimpleWaypoint wp) {
        if (wp.renderId != -1) {
            sendCommand(WaypointRendererCommand.Type.UPDATE, wp.renderId, wp.X(), wp.y(), wp.Z(), wp.rgb(), wp.name(), wp.initials());
        }
    }

    private static float getTextWidth(String text) {
        return textRenderer.getTextHandler().getWidth(text) - 1;
    }

    private static float getTextBgWidth(String text) {
        return Math.max(getTextWidth(text) + 2, textHeight);
    }

    /**
     * Gets a reusable command object from the pool, or creates a new one if empty.
     */
    private static WaypointRendererCommand obtainCommand() {
        WaypointRendererCommand cmd = commandPool.poll();
        if (cmd == null) {
            return new WaypointRendererCommand(); // Only happens during warmup/spikes
        }
        return cmd; // Reuse existing memory
    }

    /**
     * Returns a command to the pool for future reuse.
     */
    private static void freeCommand(WaypointRendererCommand cmd) {
        // Clear references to help GC (in case the pool grows too large)
        cmd.bulkWaypoints = null;
        cmd.name = null;
        commandPool.offer(cmd);
    }

    // =========================================================
    // UPDATED SENDER (LOGIC THREAD)
    // =========================================================
    private static void sendCommand(WaypointRendererCommand.Type type, int id, float x, float y, float z, int color, String name, String initials) {
        // 1. REUSE instead of NEW
        WaypointRendererCommand cmd = obtainCommand();

        // 2. Mutate the fields
        cmd.type = type;
        cmd.renderId = id;
        if (type == WaypointRendererCommand.Type.REMOVE) {
            offerCommand(cmd);
            return;
        };
        cmd.x = x;
        cmd.y = y;
        cmd.z = z;
        cmd.bgColor = color;
        cmd.fgColor = getSafeTextColor(color);
        cmd.name = name;
        cmd.initials = initials;
        cmd.initialsWidth = getTextWidth(initials);
        cmd.nameWidth = getTextWidth(name);
        cmd.initialsBgWidth = getTextBgWidth(initials);
        cmd.nameBgWidth = getTextBgWidth(name);
        // Ensure clean state
        cmd.bulkWaypoints = null;
        cmd.bulkFgColor = null;
        cmd.bulkNameWidth = null;
        cmd.bulkInitialsWidth = null;
        cmd.bulkIds = null;
        cmd.bulkInitialsBgWidth = null;
        cmd.bulkNameBgWidth = null;

        // 3. Send
        offerCommand(cmd);
    }

    // =========================================================
    // 3. RENDER LOOP (RENDER THREAD)
    // =========================================================
    public static void render(DrawContext context) {
        if (!initialized) return;

        // A. Process Queue
        WaypointRendererCommand cmd;
        while ((cmd = queue.poll()) != null) {
            processCommand(cmd);
            freeCommand(cmd);
        }

        // B. Render
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
            // hovered rendering
            float minDepth = Float.MAX_VALUE;
            float detail_winX = 0, detail_winY = 0, detail_scale = 0;
            double detail_distance = 0;
            // Pass 1: Render basic info and find the closest hovered waypoint
            for (int i = 0; i < count; i++) {
                // Retrieve raw data from SoA
                float wx = xPos[i];
                float wy = yPos[i];
                float wz = zPos[i];

                Vector4f pos = posVec.set(wx, wy, wz, 1F);
                pos.y += 0.5F;
                pos.add(camX, camY, camZ, 0F);

                float relativeX = pos.x();
                float relativeY = pos.y();
                float relativeZ = pos.z();

                pos.mul(ModelViewMatrix);
                pos.mul(ProjectionMatrix);
                float depth = pos.w();
                if (depth <= 0) continue;

                pos.div(depth);

                // ndc space
                float x = pos.x();
                float y = pos.y();

                // window space
                float winX = (x + 1F) * windowCenterX;
                float winY = (1F - y) * windowCenterY;

                // scale with perspective
                float scale = projectionScale / depth;
                if (scale < minBaseScale) {
                    scale = minBaseScale;
                }

                String initial = initials[i];
                int textBgColor = bgColor[i];
                int textColor = fgColor[i];
                float textWidth = initialsTextWidth[i];

                // center text and background
                float tx = winX - (textWidth * scale / 2F);
                float ty = winY - textHeight * scale / 2F;
                float bgWidth = initialsTextBgWidth[i];
                float bgXOffset = (textWidth - bgWidth) / 2F;
                VertexConsumer consumer = immediate.getBuffer(RenderLayer.getDebugQuads());
                Matrix4f matrix = identity.translation(tx, ty, -depth).scale(scale);
                drawQuad(consumer, bgXOffset, 0, bgWidth, textHeight, matrix, 0x80000000 | textBgColor);
                drawTextWithoutBg(0, 1, matrix, initial, textColor, immediate);
                identity.identity();

                // text hover area
                if (IS_HOVERED) {
                    if (i == HOVERED_ID) {
                        detail_winX = winX;
                        detail_winY = winY;
                        detail_scale = scale;
                        detail_distance = Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);
                    }
                } else {
                    float scaledRealBgWidth = bgWidth * scale;
                    float scaledRealBgHeight = textHeight * scale;
                    float upperCornerX = winX - scaledRealBgWidth / 2F;
                    float upperCornerY = winY - scaledRealBgHeight / 2F;
                    float lowerCornerX = upperCornerX + scaledRealBgWidth;
                    float lowerCornerY = upperCornerY + scaledRealBgHeight;
                    if (isIn2DBox(windowCenterX, windowCenterY, upperCornerX, upperCornerY, lowerCornerX, lowerCornerY)) {
                        if (depth < minDepth) {
                            minDepth = depth;
                            HOVERED_ID = i;
                            detail_winX = winX;
                            detail_winY = winY;
                            detail_scale = scale;
                            detail_distance = Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);
                        }
                    }
                }
            }

            if (HOVERED_ID != -1) {
                String name = names[HOVERED_ID];
                float textWidth = nameTextWidth[HOVERED_ID];
                float bgWidth = nameTextBgWidth[HOVERED_ID];
                int textBgColor = bgColor[HOVERED_ID];
                int textColor = fgColor[HOVERED_ID];

                float tx = detail_winX - textWidth * detail_scale / 2F;
                float halfHeight = textHeight * detail_scale / 2F;
                float ty = detail_winY - halfHeight;
                float bgXOffest = (textWidth - bgWidth) / 2F;

                Matrix4f matrix = identity.translation(tx, ty, 0F).scale(detail_scale);
                VertexConsumer consumer = immediate.getBuffer(RenderLayer.getDebugQuads());
                drawQuad(consumer, bgXOffest, 0, bgWidth, textHeight, matrix, 0xFF000000 | textBgColor);
                drawTextWithoutBg(0, 1, matrix, name, textColor, immediate);
                identity.identity();

                String distanceText;
                if (detail_distance >= 1000) {
                    distanceText = (Math.round(detail_distance / 100.0) / 10.0) + "km";
                } else {
                    distanceText = (Math.round(detail_distance * 10.0) / 10.0) + "m";
                }
                float distanceTextScale = detail_scale * 0.8F;
                Matrix4f distMatrix = identity.translation(tx + (bgXOffest + 1F - 0.2F) * detail_scale, detail_winY + halfHeight + distanceTextScale, 0F).scale(distanceTextScale);
                drawDefaultText(0, 0, distMatrix, distanceText, immediate);
                identity.identity();

                float scaledRealBgWidth = bgWidth * detail_scale;
                float scaledRealBgHeight = textHeight * detail_scale;
                float upperCornerX = detail_winX - scaledRealBgWidth / 2F;
                float upperCornerY = detail_winY - scaledRealBgHeight / 2F;
                float lowerCornerX = upperCornerX + scaledRealBgWidth;
                float lowerCornerY = upperCornerY + scaledRealBgHeight;
                IS_HOVERED = isIn2DBox(windowCenterX, windowCenterY, upperCornerX, upperCornerY, lowerCornerX, lowerCornerY);
                HOVERED_ID = IS_HOVERED ? HOVERED_ID : -1;
            }
        });
    }

    private static void drawDefaultText(float x, float y, Matrix4f matrix, String text, VertexConsumerProvider vertexConsumers) {
        textRenderer.draw(text, x, y, 0xFFFFFFFF, false, matrix, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0x80000000, MAX_LIGHT_COORDINATE);
    }

    private static void drawQuad(VertexConsumer consumer, float x0, float y0, float width, float height, Matrix4f matrix, int color) {
        float x1 = x0 + width;
        float y1 = y0 + height;
        consumer.vertex(matrix, x0, y0, 0).color(color);
        consumer.vertex(matrix, x0, y1, 0).color(color);
        consumer.vertex(matrix, x1, y1, 0).color(color);
        consumer.vertex(matrix, x1, y0, 0).color(color);
    }

    private static void drawTextWithoutBg(float x, float y, Matrix4f matrix, String text, int fgColor, VertexConsumerProvider vertexConsumers) {
        textRenderer.draw(text, x, y, fgColor, false, matrix, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, MAX_LIGHT_COORDINATE);
    }

    private static boolean isIn2DBox(float x, float y, float min_x, float min_y, float max_x, float max_y) {
        return (min_x <= x) && (x <= max_x) && (min_y <= y) && (y <= max_y);
    }

    // =========================================================
    // 4. INTERNAL HELPERS
    // =========================================================

    /**
     * Internal helper to push commands and update the tracker.
     */
    private static void offerCommand(WaypointRendererCommand cmd) {
        // 1. Update the "Last Sent" memory
        lastSentType.set(cmd.type);

        // 2. Push to queue
        queue.offer(cmd);
    }

    private static void processCommand(WaypointRendererCommand cmd) {
        switch (cmd.type) {
            case ADD:
                addInternal(cmd.renderId, cmd.x, cmd.y, cmd.z, cmd.bgColor, cmd.fgColor, cmd.name, cmd.initials, cmd.nameWidth, cmd.initialsWidth, cmd.nameBgWidth, cmd.initialsBgWidth);
                break;
            case REMOVE:
                removeInternal(cmd.renderId);
                HOVERED_ID = -1;
                IS_HOVERED = false;
                break;
            case UPDATE:
                int idx = idMap[cmd.renderId];
                if (idx != -1) {
                    xPos[idx] = cmd.x;
                    yPos[idx] = cmd.y;
                    zPos[idx] = cmd.z;
                    bgColor[idx] = cmd.bgColor;
                    fgColor[idx] = cmd.fgColor;
                    names[idx] = cmd.name;
                    initials[idx] = cmd.initials;
                    nameTextWidth[idx] = cmd.nameWidth;
                    initialsTextWidth[idx] = cmd.initialsWidth;
                    nameTextBgWidth[idx] = cmd.nameBgWidth;
                    initialsTextBgWidth[idx] = cmd.initialsBgWidth;
                }
                break;
            case CLEAR_ALL:
                clearInternal();
                break;
            case BULK_ADD:
                if (cmd.bulkWaypoints != null) {
                    for (int i = 0; i < cmd.bulkWaypoints.length; i++) {
                        SimpleWaypoint wp = cmd.bulkWaypoints[i];
                        addInternal(wp.renderId, wp.X(), wp.y(), wp.Z(), wp.rgb(), cmd.bulkFgColor[i], wp.name(), wp.initials(), cmd.bulkNameWidth[i], cmd.bulkInitialsWidth[i], cmd.bulkNameBgWidth[i], cmd.bulkInitialsBgWidth[i]);
                    }
                }
                break;
            case BULK_REMOVE:
                if (cmd.bulkIds != null) {
                    for (int id : cmd.bulkIds) {
                        removeInternal(id);
                    }
                }
                HOVERED_ID = -1;
                IS_HOVERED = false;
                break;
        }
    }

    private static void addInternal(int id, float x, float y, float z, int bg_color, int fg_color, String name, String initial, float nameWidth, float initialsWidth, float nameBgWidth, float initialsBgWidth) {
        if (id >= MAX_RENDER_ID || idMap[id] != -1) return;
        if (count >= MAX_WAYPOINTS) return;

        int i = count;
        ids[i] = id;
        xPos[i] = x;
        yPos[i] = y;
        zPos[i] = z;
        bgColor[i] = bg_color;
        fgColor[i] = fg_color;
        names[i] = name;
        initials[i] = initial;
        initialsTextWidth[i] = initialsWidth;
        nameTextWidth[i] = nameWidth;
        initialsTextBgWidth[i] = initialsBgWidth;
        nameTextBgWidth[i] = nameBgWidth;
        idMap[id] = i;
        count++;
    }

    private static void removeInternal(int id) {
        int indexToRemove = idMap[id];
        if (indexToRemove == -1) return;

        int lastIndex = count - 1;

        // "Swap and Pop"
        if (indexToRemove != lastIndex) {
            int lastEntityId = ids[lastIndex];

            ids[indexToRemove] = ids[lastIndex];
            xPos[indexToRemove] = xPos[lastIndex];
            yPos[indexToRemove] = yPos[lastIndex];
            zPos[indexToRemove] = zPos[lastIndex];
            bgColor[indexToRemove] = bgColor[lastIndex];
            fgColor[indexToRemove] = fgColor[lastIndex];
            names[indexToRemove] = names[lastIndex];
            initials[indexToRemove] = initials[lastIndex];
            initialsTextWidth[indexToRemove] = initialsTextWidth[lastIndex];
            nameTextWidth[indexToRemove] = nameTextWidth[lastIndex];
            initialsTextBgWidth[indexToRemove] = initialsTextBgWidth[lastIndex];
            nameTextBgWidth[indexToRemove] = nameTextBgWidth[lastIndex];
            idMap[lastEntityId] = indexToRemove;
        }

        // Clean up string reference to assist GC
        names[lastIndex] = null;
        initials[lastIndex] = null;
        idMap[id] = -1;
        count--;
    }

    private static void clearInternal() {
        // Clear string references for GC
        for (int i = 0; i < count; i++) {
            names[i] = null;
            initials[i] = null;
        }
        Arrays.fill(idMap, -1);
        count = 0;
        HOVERED_ID = -1;
        IS_HOVERED = false;
    }
}
