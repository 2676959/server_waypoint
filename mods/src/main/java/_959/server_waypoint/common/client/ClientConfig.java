package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.google.gson.annotations.Expose;
import com.mojang.blaze3d.platform.InputConstants;

public class ClientConfig {
    public static final boolean DEFAULT_ENABLE_WAYPOINT_RENDER = true;
    public static final boolean DEFAULT_RENDER_WAYPOINTS_UNDER_F1 = false;
    public static final int DEFAULT_WAYPOINT_SCALING_FACTOR = 100;
    public static final int DEFAULT_WAYPOINT_VERTICAL_OFFSET = 0;
    public static final int DEFAULT_WAYPOINT_BACKGROUND_ALPHA = 128;
    public static final int DEFAULT_VIEW_DISTANCE = 12;
    public static final boolean DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP = true;
    public static final boolean DEFAULT_XAERO_DEFAULT_LIST_DIRECT_SYNC = false;
    public static final boolean DEFAULT_AUTO_SYNC_TO_VOXELMAP = true;
    public static final TeleportMethod DEFAULT_TELEPORT_METHOD = TeleportMethod.KEY;
    public static final InputConstants.Key DEFAULT_TELEPORT_KEY = InputConstants.getKey("key.keyboard.t");
    public static final boolean DEFAULT_CLOSE_WAYPOINT_MANAGER_AFTER_TELEPORT = true;

    @Expose private boolean enableWaypointRender = DEFAULT_ENABLE_WAYPOINT_RENDER;
    @Expose private boolean renderWaypointsUnderF1 = DEFAULT_RENDER_WAYPOINTS_UNDER_F1;
    @Expose private int waypointScalingFactor = DEFAULT_WAYPOINT_SCALING_FACTOR; // in percent
    @Expose private int waypointVerticalOffset = DEFAULT_WAYPOINT_VERTICAL_OFFSET; // [-100, 100] in percent
    @Expose private int waypointBackgroundAlpha = DEFAULT_WAYPOINT_BACKGROUND_ALPHA; // [0, 255]
    @Expose private int viewDistance = DEFAULT_VIEW_DISTANCE;
    @Expose private boolean autoSyncToXaerosMinimap = DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP;
    @Expose private boolean xaeroDefaultListDirectSync = DEFAULT_XAERO_DEFAULT_LIST_DIRECT_SYNC;
    @Expose private boolean autoSyncToVoxelMap = DEFAULT_AUTO_SYNC_TO_VOXELMAP;
    @Expose private WaypointSorting.SortMode waypointManagerSortMode = WaypointSorting.SortMode.DEFAULT;
    @Expose private boolean waypointManagerSortReversed = false;
    @Expose private boolean waypointManagerGroupByLists = true;
    @Expose private boolean waypointManagerShowAllDimensions = false;
    @Expose private ClientWaypointQuery.View waypointManagerView = ClientWaypointQuery.View.COMBINED;
    @Expose private TeleportMethod teleportMethod = DEFAULT_TELEPORT_METHOD;
    @Expose private String teleportKey = DEFAULT_TELEPORT_KEY.getName();
    @Expose private boolean closeWaypointManagerAfterTeleport = DEFAULT_CLOSE_WAYPOINT_MANAGER_AFTER_TELEPORT;

    public ClientWaypointQuery.View getWaypointManagerView() {
        return waypointManagerView == null ? ClientWaypointQuery.View.COMBINED : waypointManagerView;
    }

    public void setWaypointManagerView(ClientWaypointQuery.View view) { this.waypointManagerView = java.util.Objects.requireNonNull(view); }

    public static boolean isXaerosMinimapLoaded = false;
    public static boolean isVoxelMapLoaded = false;

    private ClientConfig() {}

    public boolean isEnableWaypointRender() {
        return enableWaypointRender;
    }

    public void setEnableWaypointRender(boolean enableWaypointRender) {
        this.enableWaypointRender = enableWaypointRender;
        OptimizedWaypointRenderer.enableRendering(enableWaypointRender);
    }

    public boolean isRenderWaypointsUnderF1() {
        return renderWaypointsUnderF1;
    }

    public void setRenderWaypointsUnderF1(boolean renderWaypointsUnderF1) {
        this.renderWaypointsUnderF1 = renderWaypointsUnderF1;
    }

    public boolean isAutoSyncToXaerosMinimap() {
        return isXaerosMinimapLoaded && autoSyncToXaerosMinimap;
    }

    public void setAutoSyncToXaerosMinimap(boolean autoSyncToXaerosMinimap) {
        this.autoSyncToXaerosMinimap = autoSyncToXaerosMinimap;
    }

    public boolean isXaeroDefaultListDirectSync() {
        return xaeroDefaultListDirectSync;
    }

    public void setXaeroDefaultListDirectSync(boolean directSync) {
        this.xaeroDefaultListDirectSync = directSync;
    }

    public boolean isAutoSyncToVoxelMap() {
        return isVoxelMapLoaded && autoSyncToVoxelMap;
    }

    public void setAutoSyncToVoxelMap(boolean autoSyncToVoxelMap) {
        this.autoSyncToVoxelMap = autoSyncToVoxelMap;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        OptimizedWaypointRenderer.setViewDistance(viewDistance);
    }

    public int getWaypointScalingFactor() {
        return waypointScalingFactor;
    }

    public void setWaypointScalingFactor(int waypointScalingFactor) {
        this.waypointScalingFactor = waypointScalingFactor;
        OptimizedWaypointRenderer.setWaypointScalingFactor(waypointScalingFactor);
    }

    public int getWaypointVerticalOffset() {
        return waypointVerticalOffset;
    }

    public void setWaypointVerticalOffset(int waypointVerticalOffset) {
        this.waypointVerticalOffset = waypointVerticalOffset;
        OptimizedWaypointRenderer.setWaypointVerticalOffset(waypointVerticalOffset);
    }

    public int getWaypointBackgroundAlpha() {
        return waypointBackgroundAlpha;
    }

    public void setWaypointBackgroundAlpha(int waypointBackgroundAlpha) {
        this.waypointBackgroundAlpha = waypointBackgroundAlpha;
        OptimizedWaypointRenderer.setWaypointBgAlpha(waypointBackgroundAlpha);
    }

    public WaypointSorting.SortMode getWaypointManagerSortMode() {
        return waypointManagerSortMode == null
                ? WaypointSorting.SortMode.DEFAULT
                : waypointManagerSortMode;
    }

    public void setWaypointManagerSortMode(WaypointSorting.SortMode waypointManagerSortMode) {
        this.waypointManagerSortMode = waypointManagerSortMode == null
                ? WaypointSorting.SortMode.DEFAULT
                : waypointManagerSortMode;
        if (this.waypointManagerSortMode == WaypointSorting.SortMode.DEFAULT) {
            this.waypointManagerSortReversed = false;
            this.waypointManagerGroupByLists = true;
        }
    }

    public boolean isWaypointManagerSortReversed() {
        return getWaypointManagerSortMode() != WaypointSorting.SortMode.DEFAULT
                && waypointManagerSortReversed;
    }

    public void setWaypointManagerSortReversed(boolean waypointManagerSortReversed) {
        this.waypointManagerSortReversed = getWaypointManagerSortMode()
                != WaypointSorting.SortMode.DEFAULT && waypointManagerSortReversed;
    }

    public boolean isWaypointManagerGroupByLists() {
        return getWaypointManagerSortMode() == WaypointSorting.SortMode.DEFAULT
                || waypointManagerGroupByLists;
    }

    public void setWaypointManagerGroupByLists(boolean waypointManagerGroupByLists) {
        this.waypointManagerGroupByLists = getWaypointManagerSortMode()
                == WaypointSorting.SortMode.DEFAULT || waypointManagerGroupByLists;
    }

    public boolean isWaypointManagerShowAllDimensions() {
        return waypointManagerShowAllDimensions;
    }

    public void setWaypointManagerShowAllDimensions(boolean waypointManagerShowAllDimensions) {
        this.waypointManagerShowAllDimensions = waypointManagerShowAllDimensions;
    }

    public TeleportMethod getTeleportMethod() {
        return teleportMethod == null ? DEFAULT_TELEPORT_METHOD : teleportMethod;
    }

    public void setTeleportMethod(TeleportMethod teleportMethod) {
        this.teleportMethod = java.util.Objects.requireNonNull(teleportMethod);
    }

    public InputConstants.Key getTeleportKey() {
        if (teleportKey != null && (teleportKey.startsWith("key.keyboard.") || teleportKey.startsWith("scancode."))) {
            try {
                InputConstants.Key key = InputConstants.getKey(teleportKey);
                if (!key.equals(InputConstants.UNKNOWN)) {
                    return key;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall back to the default if the saved key name is invalid on this version.
            }
        }
        return DEFAULT_TELEPORT_KEY;
    }

    public void setTeleportKey(InputConstants.Key key) {
        this.teleportKey = key.getName();
    }

    public boolean matchesTeleportKey(InputConstants.Key key) {
        return getTeleportMethod() == TeleportMethod.KEY && getTeleportKey().equals(key);
    }

    public boolean isCloseWaypointManagerAfterTeleport() {
        return closeWaypointManagerAfterTeleport;
    }

    public void setCloseWaypointManagerAfterTeleport(boolean closeWaypointManagerAfterTeleport) {
        this.closeWaypointManagerAfterTeleport = closeWaypointManagerAfterTeleport;
    }

    public enum TeleportMethod {
        KEY,
        DOUBLE_CLICK,
        RIGHT_CLICK,
        MIDDLE_CLICK
    }
}
