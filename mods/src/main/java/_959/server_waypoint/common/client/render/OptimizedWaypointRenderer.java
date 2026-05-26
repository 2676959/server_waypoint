//~ gui_graphics_26
package _959.server_waypoint.common.client.render;

import _959.server_waypoint.common.util.MathHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import com.mojang.blaze3d.platform.Window;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.translate;
import static _959.server_waypoint.util.ColorUtils.getSafeTextColor;

public final class OptimizedWaypointRenderer {
    // =========================================================
    // CONFIGURATION
    // =========================================================
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_renderer");
    private static final int MAX_WAYPOINTS = 10000;
    private static final int MAX_RENDER_ID = 20000;
    private static final int DEPTH_RADIX_BITS = 8;
    private static final int DEPTH_RADIX_SIZE = 1 << DEPTH_RADIX_BITS;
    private static final int DEPTH_RADIX_MASK = DEPTH_RADIX_SIZE - 1;
    private static boolean DISABLED = false;
    private static float WAYPOINT_BASE_SCALE = 1.0F;
    private static int WAYPOINT_BG_ALPHA_MASK = 0x80000000;
    private static float WAYPOINT_VERTICAL_OFFSET = 0;
    private static long SQUARED_VIEW_DISTANCE = 12 * 16 * 12 * 16;
    private static final float MIN_DEPTH = 0F;

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
    private static final Set<SimpleWaypoint> trackedWaypointRefs = ConcurrentHashMap.newKeySet();

    // render commands
    private static final ConcurrentLinkedQueue<WaypointRendererCommand> queue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<WaypointRendererCommand> commandPool = new ConcurrentLinkedQueue<>();

    // --- Structure of Arrays (SoA) ---
    private static int[] ids;
    private static double[] xPos;
    private static double[] yPos;
    private static double[] zPos;
    private static int[] bgColor;
    private static int[] fgColor;
    private static String[] names;
    private static String[] initials;
    private static float[] nameTextWidth;
    private static float[] nameTextBgWidth;
    private static float[] initialsTextWidth;
    private static float[] initialsTextBgWidth;
    private static boolean[] local;
    private static SimpleWaypoint[] waypointRefs;

    private static int[] visibleIndex;
    private static float[] visibleWinX;
    private static float[] visibleWinY;
    private static float[] visibleIconScale;
    private static long[] visibleDepthSortKey;
    private static long[] visibleDepthSortScratch;
    private static final int[] depthSortCounts = new int[DEPTH_RADIX_SIZE];

    // =========================================================
    // MINECRAFT RENDERING CONTEXT
    // =========================================================
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Font textRenderer = mc.font;
    private static final int textHeight = textRenderer.lineHeight;
    private static final int textBgHeight = textHeight;
    private static final Window window = mc.getWindow();
    private static final Camera camera = mc.gameRenderer.getMainCamera();
    public static final Matrix4f ModelViewMatrix = new Matrix4f();
    public static final Matrix4f ProjectionMatrix = new Matrix4f();
    private static final Vector4f posVec = new Vector4f();
    private static boolean hasCameraSnapshot = false;
    private static double cameraSnapshotX = 0.0D;
    private static double cameraSnapshotY = 0.0D;
    private static double cameraSnapshotZ = 0.0D;

    // =========================================================
    // DATA TRANSFER OBJECTS
    // =========================================================
    private static class WaypointRendererCommand {
        enum Type {ADD, REMOVE, UPDATE, CLEAR_ALL, BULK_ADD, BULK_REMOVE}

        Type type;
        int renderId;
        double x, y, z;
        int bgColor;
        int fgColor;
        String name;
        String initials;
        SimpleWaypoint waypoint;
        float initialsWidth;
        float nameWidth;
        float initialsBgWidth;
        float nameBgWidth;
        boolean local;

        SimpleWaypoint[] bulkWaypoints;
        int bulkSize;
        int[] bulkIds;
        // only need foreground color here as waypoint color is the background color
        int[] bulkFgColor;
        float[] bulkNameWidth;
        float[] bulkNameBgWidth;
        float[] bulkInitialsWidth;
        float[] bulkInitialsBgWidth;
        boolean[] bulkLocal;
    }

    // =========================================================
    // INITIALIZATION
    // =========================================================
    public static void init() {
        if (initialized) return;

        idMap = new int[MAX_RENDER_ID];
        Arrays.fill(idMap, -1);

        ids = new int[MAX_WAYPOINTS];
        xPos = new double[MAX_WAYPOINTS];
        yPos = new double[MAX_WAYPOINTS];
        zPos = new double[MAX_WAYPOINTS];
        bgColor = new int[MAX_WAYPOINTS];
        fgColor = new int[MAX_WAYPOINTS];
        names = new String[MAX_WAYPOINTS];
        initials = new String[MAX_WAYPOINTS];
        nameTextWidth = new float[MAX_WAYPOINTS];
        nameTextBgWidth = new float[MAX_WAYPOINTS];
        initialsTextWidth = new float[MAX_WAYPOINTS];
        initialsTextBgWidth = new float[MAX_WAYPOINTS];
        local = new boolean[MAX_WAYPOINTS];
        waypointRefs = new SimpleWaypoint[MAX_WAYPOINTS];
        visibleIndex = new int[MAX_WAYPOINTS];
        visibleWinX = new float[MAX_WAYPOINTS];
        visibleWinY = new float[MAX_WAYPOINTS];
        visibleIconScale = new float[MAX_WAYPOINTS];
        visibleDepthSortKey = new long[MAX_WAYPOINTS];
        visibleDepthSortScratch = new long[MAX_WAYPOINTS];
        initialized = true;
        LOGGER.info("waypoint renderer initialized");
    }

    // =========================================================
    // PUBLIC API (LOGIC THREAD)
    // =========================================================
    public static void enableRendering(boolean enable) {
        DISABLED = !enable;
    }

    /**
     * set waypoint scale in percentage >=0%
     * */
    public static void setWaypointScalingFactor(int scale) {
        WAYPOINT_BASE_SCALE = scale / 100F;
    }

    /**
     * set view distance in chunks
     * */
    public static void setViewDistance(int chunks) {
        SQUARED_VIEW_DISTANCE = chunks * chunks * 256L;
    }

    /**
     * set waypoint rendering alpha in 0~255
     * */
    public static void setWaypointBgAlpha(int alpha) {
        WAYPOINT_BG_ALPHA_MASK = 0xFF000000 & (alpha << 24);
    }

    /**
     * set waypoint vertical offset in percentage -100~100%
     * */
    public static void setWaypointVerticalOffset(float offset) {
        WAYPOINT_VERTICAL_OFFSET = MathHelper.clamp(offset / 200F, -0.5F, 0.5F);
    }

    public static void clearScene() {
        nextRenderId = 0;
        clearTrackedWaypointRenderIds();

        WaypointRendererCommand cmd = obtainCommand();
        clearCommandReferences(cmd);
        cmd.type = WaypointRendererCommand.Type.CLEAR_ALL;
        offerCommand(cmd);
    }

    /**
     * Adds multiple WaypointLists in a single batch.
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
        int[] renderIds = new int[estimatedSize];
        float[] nameWidth = new float[estimatedSize];
        float[] initialsWidth = new float[estimatedSize];
        float[] nameBgWidth = new float[estimatedSize];
        float[] initialsBgWidth = new float[estimatedSize];
        boolean[] locals = new boolean[estimatedSize];

        int index = 0;
        for (WaypointList list : lists) {
            // SKIP hidden lists entirely
            if (!list.isShow()) continue;

            for (SimpleWaypoint wp : list.simpleWaypoints()) {
                if (generateBulkData(wps, renderIds, fgColors, nameWidth, initialsWidth, nameBgWidth, initialsBgWidth, locals, index, wp)) {
                    index++;
                }
            }
        }

        if (index == 0) return;

        sendBulkData(wps, renderIds, index, fgColors, nameWidth, initialsWidth, nameBgWidth, initialsBgWidth, locals);
    }

    /**
     * Adds the waypoint to the renderer and automatically assigns it an ID.
     */
    public static void add(SimpleWaypoint wp) {
        // Prevent adding the same object twice
        if (wp.renderId != -1) return;
        if (!assignRenderId(wp)) return;

        int assignedId = wp.renderId;
        sendCommand(WaypointRendererCommand.Type.ADD, assignedId, getWaypointX(wp), getWaypointY(wp), getWaypointZ(wp), wp.rgb(), wp.name(), wp.initials(), !wp.global(), wp);
    }

    public static void addList(@Unmodifiable List<SimpleWaypoint> newWaypoints) {
        if (newWaypoints.isEmpty()) return;

        int size = newWaypoints.size();
        SimpleWaypoint[] bulkData = new SimpleWaypoint[size];
        int[] renderIds = new int[size];
        int[] fgColor = new int[size];
        float[] nameWidth = new float[size];
        float[] initialsWidth = new float[size];
        float[] nameBgWith = new float[size];
        float[] initialsBgWidth = new float[size];
        boolean[] locals = new boolean[size];

        int index = 0;
        for (int i = 0; i < size; i++) {
            SimpleWaypoint wp = newWaypoints.get(i);
            if (generateBulkData(bulkData, renderIds, fgColor, nameWidth, initialsWidth, nameBgWith, initialsBgWidth, locals, index, wp)) {
                index++;
            }
        }
        sendBulkData(bulkData, renderIds, index, fgColor, nameWidth, initialsWidth, nameBgWith, initialsBgWidth, locals);
    }

    private static boolean generateBulkData(SimpleWaypoint[] bulkData, int[] renderIds, int[] fgColor, float[] nameWidth, float[] initialsWidth, float[] nameBgWith, float[] initialsBgWidth, boolean[] locals, int i, SimpleWaypoint wp) {
        if (wp.renderId != -1 || !assignRenderId(wp)) {
            return false;
        }
        bulkData[i] = wp;
        renderIds[i] = wp.renderId;
        fgColor[i] = getSafeTextColor(wp.rgb());
        String name = wp.name();
        String initials1 = wp.initials();
        nameWidth[i] = getTextWidth(name);
        initialsWidth[i] = getTextWidth(initials1);
        nameBgWith[i] = getTextBgWidth(name);
        initialsBgWidth[i] = getTextBgWidth(initials1);
        locals[i] = !wp.global();
        return true;
    }

    private static void sendBulkData(SimpleWaypoint[] bulkData, int[] renderIds, int size, int[] fgColor, float[] nameWidth, float[] initialsWidth, float[] nameBgWith, float[] initialsBgWidth, boolean[] locals) {
        if (size == 0) return;
        WaypointRendererCommand cmd = obtainCommand();
        clearCommandReferences(cmd);
        cmd.type = WaypointRendererCommand.Type.BULK_ADD;
        cmd.bulkWaypoints = bulkData;
        cmd.bulkSize = size;
        cmd.bulkIds = renderIds;
        cmd.bulkFgColor = fgColor;
        cmd.bulkNameWidth = nameWidth;
        cmd.bulkInitialsWidth = initialsWidth;
        cmd.bulkNameBgWidth = nameBgWith;
        cmd.bulkInitialsBgWidth = initialsBgWidth;
        cmd.bulkLocal = locals;
        offerCommand(cmd);
    }

    /**
     * Removes the waypoint and resets its ID to -1.
     */
    public static void remove(SimpleWaypoint wp) {
        if (wp.renderId == -1) return; // Not in renderer

        int renderId = wp.renderId;
        releaseRenderId(wp, renderId);
        sendCommand(WaypointRendererCommand.Type.REMOVE, renderId, 0, 0, 0, 0, null, null, false, null);
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
        for (SimpleWaypoint wp : list) {
            releaseRenderId(wp, wp.renderId);
        }

        if (idsToRemove.length > 0) {
            WaypointRendererCommand cmd = obtainCommand();
            cmd.type = WaypointRendererCommand.Type.BULK_REMOVE;
            cmd.bulkIds = idsToRemove;
            offerCommand(cmd);
        }
    }

    public static void updateCameraSnapshot(Vec3 cameraPos, Matrix4fc modelViewMatrix, Matrix4fc projectionMatrix) {
        cameraSnapshotX = cameraPos.x;
        cameraSnapshotY = cameraPos.y;
        cameraSnapshotZ = cameraPos.z;
        ModelViewMatrix.set(modelViewMatrix);
        ProjectionMatrix.set(projectionMatrix);
        hasCameraSnapshot = true;
    }

    public static void updateWaypoint(SimpleWaypoint wp) {
        if (wp.renderId != -1) {
            sendCommand(WaypointRendererCommand.Type.UPDATE, wp.renderId, getWaypointX(wp), getWaypointY(wp), getWaypointZ(wp), wp.rgb(), wp.name(), wp.initials(), !wp.global(), null);
        }
    }

    private static double getWaypointX(SimpleWaypoint waypoint) {
        return waypoint.x() + 0.5D;
    }

    private static double getWaypointY(SimpleWaypoint waypoint) {
        return waypoint.y() + 0.5D;
    }

    private static double getWaypointZ(SimpleWaypoint waypoint) {
        return waypoint.z() + 0.5D;
    }

    private static float getTextWidth(String text) {
        return text == null ? 0 : textRenderer.width(text);
    }

    private static float getTextBgWidth(String text) {
        return Math.max(getTextWidth(text) + 2, textBgHeight);
    }

    /**
     * Gets a reusable command object from the pool or creates a new one if empty.
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
        clearCommandReferences(cmd);
        commandPool.offer(cmd);
    }

    // =========================================================
    // UPDATED SENDER (LOGIC THREAD)
    // =========================================================

    private static void cleanCommandBulkData(WaypointRendererCommand cmd) {
        cmd.bulkWaypoints = null;
        cmd.bulkSize = 0;
        cmd.bulkFgColor = null;
        cmd.bulkNameWidth = null;
        cmd.bulkInitialsWidth = null;
        cmd.bulkIds = null;
        cmd.bulkInitialsBgWidth = null;
        cmd.bulkNameBgWidth = null;
        cmd.bulkLocal = null;
    }

    private static void cleanCommandWaypointData(WaypointRendererCommand cmd) {
        cmd.waypoint = null;
        cmd.name = null;
        cmd.initials = null;
    }

    private static void clearCommandReferences(WaypointRendererCommand cmd) {
        cleanCommandBulkData(cmd);
        cleanCommandWaypointData(cmd);
    }

    private static void sendCommand(WaypointRendererCommand.Type type, int id, double x, double y, double z, int color, String name, String initials, boolean isLocal, SimpleWaypoint waypoint) {
        // 1. REUSE instead of NEW
        WaypointRendererCommand cmd = obtainCommand();
        clearCommandReferences(cmd);

        // 2. Mutate the fields
        cmd.type = type;
        cmd.renderId = id;
        cmd.waypoint = waypoint;
        if (type == WaypointRendererCommand.Type.REMOVE) {
            offerCommand(cmd);
            return;
        }
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
        cmd.local = isLocal;
        offerCommand(cmd);
    }

    // =========================================================
    // RENDER LOOP (RENDER THREAD)
    // =========================================================
    public static void
    render
            (GuiGraphicsExtractor context) {
        if (!initialized) return;

        // A. Process Queue
        WaypointRendererCommand cmd;
        while ((cmd = queue.poll()) != null) {
            processCommand(cmd);
            freeCommand(cmd);
        }

        if (DISABLED) return;

        // B. Render projected world waypoints as GUI elements.
        int scaledWidth = window.getGuiScaledWidth();
        float windowCenterX = scaledWidth / 2F;
        int scaledHeight = window.getGuiScaledHeight();
        float windowCenterY = scaledHeight / 2F;
        float guiScaleFactor = (float) window.getGuiScale();
        int framebufferHeight = window.getHeight();
        float projectionConstant = ProjectionMatrix.m11();
        float baseScale = WAYPOINT_BASE_SCALE * 0.01F * framebufferHeight / guiScaleFactor;
        float projectionScale = baseScale * projectionConstant;
        float minBaseScale = baseScale / 5F;
        double camX;
        double camY;
        double camZ;
        if (hasCameraSnapshot) {
            camX = cameraSnapshotX;
            camY = cameraSnapshotY;
            camZ = cameraSnapshotZ;
        } else {
            //? if >= 1.21.6 {
            Vec3 cameraPos = camera.position();
            //?} else {
            /*Vec3 cameraPos = camera.getPosition();
            *///?}
            camX = cameraPos.x;
            camY = cameraPos.y;
            camZ = cameraPos.z;
        }
        float minDepth = Float.MAX_VALUE;
        int detailIndex = -1;
        float detailWinX = 0.0F;
        float detailWinY = 0.0F;
        float detailScale = 1.0F;
        double detailDistance = 0.0;
        int renderCount = 0;

        if (HOVERED_ID >= count) {
            HOVERED_ID = -1;
            IS_HOVERED = false;
        }

        for (int i = 0; i < count; i++) {
            double relX = xPos[i] - camX;
            double relY = yPos[i] + WAYPOINT_VERTICAL_OFFSET - camY;
            double relZ = zPos[i] - camZ;
            double horizontalDistanceSquared = relX * relX + relZ * relZ;
            if (local[i] && horizontalDistanceSquared > SQUARED_VIEW_DISTANCE) {
                continue;
            }

            Vector4f projected = posVec.set((float) relX, (float) relY, (float) relZ, 1.0F);
            projected.mul(ModelViewMatrix);
            projected.mul(ProjectionMatrix);

            float depth = projected.w();
            if (!Float.isFinite(depth) || depth <= MIN_DEPTH) {
                continue;
            }

            float ndcX = projected.x() / depth;
            float ndcY = projected.y() / depth;
            float iconScale = getIconScale(depth, projectionScale, minBaseScale);
            float marginX = initialsTextBgWidth[i] * iconScale / (scaledWidth >> 1);
            float marginY = textBgHeight * iconScale / (scaledHeight >> 1);
            if (ndcX < -1.0F - marginX || ndcX > 1.0F + marginX || ndcY < -1.0F - marginY || ndcY > 1.0F + marginY) {
                continue;
            }

            float winX = (ndcX + 1.0F) * windowCenterX;
            float winY = (1.0F - ndcY) * windowCenterY;

            visibleIndex[renderCount] = i;
            visibleWinX[renderCount] = winX;
            visibleWinY[renderCount] = winY;
            visibleIconScale[renderCount] = iconScale;
            visibleDepthSortKey[renderCount] = packDepthSortKey(depth, renderCount);
            renderCount++;

            if (IS_HOVERED) {
                if (i == HOVERED_ID) {
                    detailIndex = i;
                    detailWinX = winX;
                    detailWinY = winY;
                    detailScale = iconScale;
                    detailDistance = Math.sqrt(horizontalDistanceSquared + relY * relY);
                }
            } else {
                float halfWidth = initialsTextBgWidth[i] * iconScale * 0.5F;
                float halfHeight = textBgHeight * iconScale * 0.5F;
                if (isIn2DBox(windowCenterX, windowCenterY, winX - halfWidth, winY - halfHeight, winX + halfWidth, winY + halfHeight) && depth < minDepth) {
                    minDepth = depth;
                    HOVERED_ID = i;
                    detailIndex = i;
                    detailWinX = winX;
                    detailWinY = winY;
                    detailScale = iconScale;
                    detailDistance = Math.sqrt(horizontalDistanceSquared + relY * relY);
                }
            }
        }

        drawWaypointIcons(context, renderCount);

        if (detailIndex != -1) {
            String name = names[detailIndex];
            float textWidth = nameTextWidth[detailIndex];
            float bgWidth = nameTextBgWidth[detailIndex];
            float halfHeight = textBgHeight * detailScale * 0.5F;
            float labelTop = detailWinY - halfHeight;
            float labelBgLeft = getBoxLeft(detailWinX, bgWidth, detailScale);
            float labelBgBottom = labelTop + textBgHeight * detailScale;
            drawTextBox(context, name, detailWinX, labelTop, detailScale, textWidth, bgWidth, 0xFF000000 | bgColor[detailIndex], fgColor[detailIndex]);
            String distanceText = getDistanceText(detailDistance);
            float distanceWidth = getTextWidth(distanceText);
            float distanceBgWidth = getTextBgWidth(distanceText);
            float distanceScale = detailScale * 0.8F;
            drawTextBoxAt(context, distanceText, labelBgLeft, labelBgBottom, distanceScale, distanceWidth, distanceBgWidth, 0x88000000, 0xFFFFFFFF);
            float scaledRealBgWidth = bgWidth * detailScale;
            float scaledRealBgHeight = textBgHeight * detailScale;
            float upperCornerX = detailWinX - scaledRealBgWidth * 0.5F;
            float upperCornerY = detailWinY - scaledRealBgHeight * 0.5F;
            float lowerCornerX = upperCornerX + scaledRealBgWidth;
            float lowerCornerY = upperCornerY + scaledRealBgHeight;
            IS_HOVERED = isIn2DBox(windowCenterX, windowCenterY, upperCornerX, upperCornerY, lowerCornerX, lowerCornerY);
            HOVERED_ID = IS_HOVERED ? detailIndex : -1;
        } else {
            HOVERED_ID = -1;
            IS_HOVERED = false;
        }
    }

    private static void drawWaypointIcons(GuiGraphicsExtractor context, int renderCount) {
        if (renderCount == 0) {
            return;
        }

        sortVisibleDepthKeys(renderCount);
        for (int sortedIndex = renderCount - 1; sortedIndex >= 0; sortedIndex--) {
            int visibleSlot = unpackVisibleSlot(visibleDepthSortKey[sortedIndex]);
            int waypointIndex = visibleIndex[visibleSlot];
            float iconScale = visibleIconScale[visibleSlot];
            float bgWidth = initialsTextBgWidth[waypointIndex];
            float left = getBoxLeft(visibleWinX[visibleSlot], bgWidth, iconScale);
            float top = visibleWinY[visibleSlot] - textBgHeight * iconScale * 0.5F;

            drawTextBoxAt(context, initials[waypointIndex], left, top, iconScale, initialsTextWidth[waypointIndex], bgWidth, WAYPOINT_BG_ALPHA_MASK | bgColor[waypointIndex], fgColor[waypointIndex]);
        }
    }

    private static long packDepthSortKey(float depth, int visibleSlot) {
        return ((Float.floatToRawIntBits(depth) & 0xFFFFFFFFL) << 32) | (visibleSlot & 0xFFFFFFFFL);
    }

    private static void sortVisibleDepthKeys(int renderCount) {
        long[] from = visibleDepthSortKey;
        long[] to = visibleDepthSortScratch;

        for (int shift = Integer.SIZE; shift < Long.SIZE; shift += DEPTH_RADIX_BITS) {
            Arrays.fill(depthSortCounts, 0);
            for (int i = 0; i < renderCount; i++) {
                depthSortCounts[(int) (from[i] >>> shift) & DEPTH_RADIX_MASK]++;
            }

            int offset = 0;
            for (int i = 0; i < DEPTH_RADIX_SIZE; i++) {
                int bucketSize = depthSortCounts[i];
                depthSortCounts[i] = offset;
                offset += bucketSize;
            }

            for (int i = 0; i < renderCount; i++) {
                long key = from[i];
                int bucket = (int) (key >>> shift) & DEPTH_RADIX_MASK;
                to[depthSortCounts[bucket]++] = key;
            }

            long[] swap = from;
            from = to;
            to = swap;
        }

        if (from != visibleDepthSortKey) {
            System.arraycopy(from, 0, visibleDepthSortKey, 0, renderCount);
        }
    }

    private static int unpackVisibleSlot(long depthSortKey) {
        return (int) depthSortKey;
    }

    private static float getIconScale(float depth, float projectionScale, float minBaseScale) {
        float scale = WAYPOINT_BASE_SCALE * projectionScale / depth;
        return scale < minBaseScale ? minBaseScale : scale;
    }

    private static String getDistanceText(double distance) {
        if (distance >= 1000.0) {
            return (Math.round(distance / 100.0) / 10.0) + "km";
        }
        return (Math.round(distance * 10.0) / 10.0) + "m";
    }

    private static float getBoxLeft(float centerX, float backgroundWidth, float boxScale) {
        return centerX - (float) Math.ceil(backgroundWidth) * boxScale * 0.5F;
    }

    private static float getCenteredTextX(float backgroundWidth, float textWidth) {
        return ((float) Math.ceil(backgroundWidth) - getCenteredTextWidth(textWidth)) * 0.5F;
    }

    private static float getCenteredTextWidth(float textWidth) {
        return Math.max(0.0F, textWidth - 1.0F);
    }

    private static void drawTextBox(GuiGraphicsExtractor context, String text, float centerX, float topY, float boxScale, float textWidth, float backgroundWidth, int backgroundColor, int textColor) {
        int bgWidth = (int) Math.ceil(backgroundWidth);
        float left = centerX - bgWidth * boxScale * 0.5F;
        float textX = getCenteredTextX(bgWidth, textWidth);

        push(context);
        translate(context, left, topY);
        scale(context, boxScale, boxScale);
        context.fill(0, 0, bgWidth, textBgHeight, backgroundColor);
        translate(context, textX, 0.0F);
        drawText(context, textRenderer, text, 0, 1, textColor, false);
        pop(context);
        finishGuiLayer(context);
    }

    private static void drawTextBoxAt(GuiGraphicsExtractor context, String text, float left, float topY, float boxScale, float textWidth, float backgroundWidth, int backgroundColor, int textColor) {
        int bgWidth = (int) Math.ceil(backgroundWidth);
        float textX = getCenteredTextX(bgWidth, textWidth);

        push(context);
        translate(context, left, topY);
        scale(context, boxScale, boxScale);
        context.fill(0, 0, bgWidth, textBgHeight, backgroundColor);
        translate(context, textX, 0.0F);
        drawText(context, textRenderer, text, 0, 1, textColor, false);
        pop(context);
        finishGuiLayer(context);
    }

    private static void finishGuiLayer(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        context.nextStratum();
        //?} else {
        /*context.flush();
        *///?}
    }

    private static boolean isIn2DBox(float x, float y, float min_x, float min_y, float max_x, float max_y) {
        return (min_x <= x) && (x <= max_x) && (min_y <= y) && (y <= max_y);
    }

    // =========================================================
    // INTERNAL HELPERS
    // =========================================================

    private static void offerCommand(WaypointRendererCommand cmd) {
        queue.offer(cmd);
    }

    private static void processCommand(WaypointRendererCommand cmd) {
        switch (cmd.type) {
            case ADD:
                addInternal(cmd.waypoint, cmd.renderId, cmd.x, cmd.y, cmd.z, cmd.bgColor, cmd.fgColor, cmd.name, cmd.initials, cmd.nameWidth, cmd.initialsWidth, cmd.nameBgWidth, cmd.initialsBgWidth, cmd.local);
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
                    local[idx] = cmd.local;
                }
                break;
            case CLEAR_ALL:
                clearInternal();
                break;
            case BULK_ADD:
                if (cmd.bulkWaypoints != null) {
                    for (int i = 0; i < cmd.bulkSize; i++) {
                        SimpleWaypoint wp = cmd.bulkWaypoints[i];
                        addInternal(wp, cmd.bulkIds[i], getWaypointX(wp), getWaypointY(wp), getWaypointZ(wp), wp.rgb(), cmd.bulkFgColor[i], wp.name(), wp.initials(), cmd.bulkNameWidth[i], cmd.bulkInitialsWidth[i], cmd.bulkNameBgWidth[i], cmd.bulkInitialsBgWidth[i], cmd.bulkLocal[i]);
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

    private static void addInternal(SimpleWaypoint waypoint, int id, double x, double y, double z, int bg_color, int fg_color, String name, String initial, float nameWidth, float initialsWidth, float nameBgWidth, float initialsBgWidth, boolean isLocal) {
        if (waypoint != null && waypoint.renderId != id) return;
        if (id < 0 || id >= MAX_RENDER_ID) {
            releaseRenderId(waypoint, id);
            return;
        }
        int existingIndex = idMap[id];
        if (existingIndex != -1) {
            if (waypointRefs[existingIndex] != waypoint) {
                releaseRenderId(waypoint, id);
            }
            return;
        }
        if (count >= MAX_WAYPOINTS) {
            releaseRenderId(waypoint, id);
            return;
        }

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
        local[i] = isLocal;
        waypointRefs[i] = waypoint;
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
            local[indexToRemove] = local[lastIndex];
            waypointRefs[indexToRemove] = waypointRefs[lastIndex];
            idMap[lastEntityId] = indexToRemove;
        }

        // Clean up string reference to assist GC
        names[lastIndex] = null;
        initials[lastIndex] = null;
        waypointRefs[lastIndex] = null;
        idMap[id] = -1;
        count--;
    }

    private static void clearInternal() {
        // Clear string references for GC
        for (int i = 0; i < count; i++) {
            names[i] = null;
            initials[i] = null;
            waypointRefs[i] = null;
        }
        Arrays.fill(idMap, -1);
        count = 0;
        HOVERED_ID = -1;
        IS_HOVERED = false;
    }

    private static boolean assignRenderId(SimpleWaypoint waypoint) {
        if (nextRenderId >= MAX_RENDER_ID) {
            LOGGER.error("Max Entity ID limit reached! Call clearScene() to reset.");
            return false;
        }
        waypoint.renderId = nextRenderId++;
        trackWaypointRef(waypoint);
        return true;
    }

    private static void releaseRenderId(SimpleWaypoint waypoint, int id) {
        if (waypoint != null && waypoint.renderId == id) {
            waypoint.renderId = -1;
            untrackWaypointRef(waypoint);
        }
    }

    private static void trackWaypointRef(SimpleWaypoint waypoint) {
        if (waypoint != null) {
            trackedWaypointRefs.add(waypoint);
        }
    }

    private static void untrackWaypointRef(SimpleWaypoint waypoint) {
        trackedWaypointRefs.remove(waypoint);
    }

    private static void clearTrackedWaypointRenderIds() {
        for (SimpleWaypoint waypoint : trackedWaypointRefs) {
            waypoint.renderId = -1;
        }
        trackedWaypointRefs.clear();
    }
}
