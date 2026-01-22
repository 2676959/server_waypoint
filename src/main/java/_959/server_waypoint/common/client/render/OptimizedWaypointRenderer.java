package _959.server_waypoint.common.client.render;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
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

    // ID Generator (Managed synchronously on Logic Thread)
    private static int nextRenderId = 0;
    private static int[] idMap;

    private static final ConcurrentLinkedQueue<WaypointRendererCommand> queue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<WaypointRendererCommand> commandPool = new ConcurrentLinkedQueue<>();


    // --- Structure of Arrays (SoA) ---
    private static int[] ids;
    private static float[] xPos;
    private static float[] yPos;
    private static float[] zPos;
    private static int[] colors;
    private static String[] names;
    private static String[] initials;
    private static int[] initialsTextWidth;
    private static int[] nameTextWidth;

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
        int entityId;
        float x, y, z;
        int colorArgb;
        String name;
        String initials;
        SimpleWaypoint[] bulkData;
        int[] bulkIds;
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
        colors = new int[MAX_WAYPOINTS];
        names = new String[MAX_WAYPOINTS];
        initials = new String[MAX_WAYPOINTS];
        initialsTextWidth = new int[MAX_WAYPOINTS];
        nameTextWidth = new int[MAX_WAYPOINTS];

        initialized = true;
        LOGGER.info("waypoint renderer initialized");
    }

    // =========================================================
    // 2. PUBLIC API (LOGIC THREAD)
    // =========================================================
    public static void clearScene() {
        // 1. Reset the ID Counter
        nextRenderId = 0;

        // 2. Send Clear Command
        WaypointRendererCommand cmd = obtainCommand();
        cmd.type = WaypointRendererCommand.Type.CLEAR_ALL;
        queue.offer(cmd);
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
        List<SimpleWaypoint> batch = new java.util.ArrayList<>(estimatedSize);

        for (WaypointList list : lists) {
            // SKIP hidden lists entirely
            if (!list.isShow()) continue;

            for (SimpleWaypoint wp : list.simpleWaypoints()) {
                // Assign ID if needed (Logic Side)
                if (wp.renderId == -1) {
                    wp.renderId = nextRenderId++;
                }
                batch.add(wp);
            }
        }

        if (batch.isEmpty()) return;

        // 3. Send Single Command
        WaypointRendererCommand cmd = obtainCommand();
        cmd.type = WaypointRendererCommand.Type.BULK_ADD;
        cmd.bulkData = batch.toArray(new SimpleWaypoint[0]);
        queue.offer(cmd);
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
        sendCommand(WaypointRendererCommand.Type.ADD, assignedId, wp.X(), wp.Y(), wp.Z(), wp.rgb(), wp.name(), wp.initials());
    }

    public static void addList(@Unmodifiable List<SimpleWaypoint> newWaypoints) {
        for (SimpleWaypoint wp : newWaypoints) {
            if (wp.renderId == -1) {
                wp.renderId = nextRenderId++;
            }
        }
        WaypointRendererCommand cmd = obtainCommand();
        cmd.type = WaypointRendererCommand.Type.BULK_ADD;
        cmd.bulkData = newWaypoints.toArray(new SimpleWaypoint[0]);
        queue.offer(cmd);
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
            queue.offer(cmd);
        }
    }

    public static void updateWaypoint(SimpleWaypoint wp) {
        if (wp.renderId != -1) {
            sendCommand(WaypointRendererCommand.Type.UPDATE, wp.renderId, wp.X(), wp.Y(), wp.Z(), wp.rgb(), wp.name(), wp.initials());
        }
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
        cmd.bulkData = null;
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
        cmd.entityId = id;
        cmd.x = x;
        cmd.y = y;
        cmd.z = z;
        cmd.colorArgb = color;
        cmd.name = name;
        cmd.initials = initials;
        cmd.bulkData = null; // Ensure clean state

        queue.offer(cmd);
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
            // --- New variables for detailed rendering ---
            int closestHoveredIndex = -1;
            float minDepth = Float.MAX_VALUE;
            float detail_winX = 0, detail_winY = 0, detail_scale = 0;
            double detail_distance = 0;
            // Pass 1: Render basic info and find the closest hovered waypoint
            for (int i = 0; i < count; i++) {
                // Retrieve raw data from SoA
                float wx = xPos[i];
                float wy = yPos[i];
                float wz = zPos[i];
                int color = colors[i];
                String initial = initials[i];

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
                float winX = (x + 1) * windowCenterX;
                float winY = (1 - y) * windowCenterY;

                // scale with perspective
                float scale = projectionScale / depth;
                if (scale < minBaseScale) {
                    scale = minBaseScale;
                }

                // center text
                int textWidth = initialsTextWidth[i];
                float tx = winX - ((textWidth - 1) * scale / 2F);
                float ty = winY - textHeight * scale;

                // text hover area
                float scaledRealTextWidth = (textWidth + 1) * scale;
                float scaledRealTextHeight = (textHeight + 1) * scale;
                float upperCornerX = winX - (scaledRealTextWidth / 2F);
                float upperCornerY = winY - scaledRealTextHeight;
                float lowerCornerX = upperCornerX + scaledRealTextWidth;
                float lowerCornerY = upperCornerY + scaledRealTextHeight;

                if (isIn2DBox(windowCenterX, windowCenterY, upperCornerX, upperCornerY, lowerCornerX, lowerCornerY)) {
                    if (depth < minDepth) {
                        minDepth = depth;
                        closestHoveredIndex = i;
                        detail_winX = winX;
                        detail_winY = winY;
                        detail_scale = scale;
                        detail_distance = Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);
                    }
                }
                Matrix4f matrix = identity.translation(tx, ty, -depth).scale(scale);
                drawWaypointOnHud(matrix, 0, 0, initial, 0x80000000 | color, immediate, TextRenderer.TextLayerType.NORMAL);
                identity.identity();
            }

            if (closestHoveredIndex != -1) {
                String name = names[closestHoveredIndex];
                int textWidth = nameTextWidth[closestHoveredIndex];
                int color = colors[closestHoveredIndex];

                float tx = detail_winX - ((textWidth - 1) * detail_scale / 2F);
                float ty = detail_winY - textHeight * detail_scale;

                Matrix4f matrix = identity.translation(tx, ty, 0F).scale(detail_scale);
                drawWaypointOnHud(matrix, 0, 0, name, 0xFF000000 | color, immediate, TextRenderer.TextLayerType.NORMAL);
                identity.identity();

                String distanceText;
                if (detail_distance >= 1000) {
                    distanceText = (Math.round(detail_distance / 100.0) / 10.0) + "km";
                } else {
                    distanceText = (Math.round(detail_distance * 10.0) / 10.0) + "m";
                }
                float distanceTextScale = detail_scale / 1.25F;
                Matrix4f distMatrix = identity.translation(tx -0.2F * detail_scale, detail_winY + distanceTextScale, 0F).scale(distanceTextScale);
                drawWaypointOnHud(distMatrix, 0, 0, distanceText, 0x80000000, immediate, TextRenderer.TextLayerType.NORMAL);
                identity.identity();
            }
        });
    }

    public static void drawWaypointOnHud(Matrix4f matrix, float x, float y, String text, int color, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType textLayerType) {
        textRenderer.draw(text, x, y, 0xFFFFFFFF, false, matrix, vertexConsumers, textLayerType, color, 0xF000F0);
    }

    private static boolean isIn2DBox(float x, float y, float min_x, float min_y, float max_x, float max_y) {
        return (min_x <= x) && (x <= max_x) && (min_y <= y) && (y <= max_y);
    }

    // =========================================================
    // 4. INTERNAL HELPERS
    // =========================================================
    private static void processCommand(WaypointRendererCommand cmd) {
        switch (cmd.type) {
            case ADD:
                addInternal(cmd.entityId, cmd.x, cmd.y, cmd.z, cmd.colorArgb, cmd.name, cmd.initials);
                break;
            case REMOVE:
                removeInternal(cmd.entityId);
                break;
            case UPDATE:
                int idx = idMap[cmd.entityId];
                if (idx != -1) {
                    xPos[idx] = cmd.x;
                    yPos[idx] = cmd.y;
                    zPos[idx] = cmd.z;
                    colors[idx] = cmd.colorArgb;
                    names[idx] = cmd.name;
                    initials[idx] = cmd.initials;
                    initialsTextWidth[idx] = textRenderer.getWidth(cmd.initials);
                    nameTextWidth[idx] = textRenderer.getWidth(cmd.name);
                }
                break;
            case CLEAR_ALL:
                clearInternal();
                break;
            case BULK_ADD:
                if (cmd.bulkData != null) {
                    for (SimpleWaypoint wp : cmd.bulkData) {
                        addInternal(wp.renderId, wp.X(), wp.Y(), wp.Z(), wp.rgb(), wp.name(), wp.initials());
                    }
                }
                break;
            case BULK_REMOVE:
                if (cmd.bulkIds != null) {
                    for (int id : cmd.bulkIds) {
                        removeInternal(id);
                    }
                }
                break;
        }
    }

    private static void addInternal(int id, float x, float y, float z, int color, String name, String initial) {
        if (id >= MAX_RENDER_ID || idMap[id] != -1) return;
        if (count >= MAX_WAYPOINTS) return;

        int i = count;
        ids[i] = id;
        xPos[i] = x;
        yPos[i] = y;
        zPos[i] = z;
        colors[i] = color;
        names[i] = name;
        initials[i] = initial;
        initialsTextWidth[i] = textRenderer.getWidth(initial);
        nameTextWidth[i] = textRenderer.getWidth(name);

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
            colors[indexToRemove] = colors[lastIndex];
            names[indexToRemove] = names[lastIndex];
            initials[indexToRemove] = initials[lastIndex];
            initialsTextWidth[indexToRemove] = initialsTextWidth[lastIndex];
            nameTextWidth[indexToRemove] = nameTextWidth[lastIndex];

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
    }
}
