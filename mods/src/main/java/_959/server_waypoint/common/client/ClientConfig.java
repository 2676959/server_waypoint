package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.google.gson.annotations.Expose;

public class ClientConfig {
    @Expose private boolean enableWaypointRender = true;
    @Expose private int waypointScalingFactor = 100; // in percent
    @Expose private int waypointVerticalOffset = 0; // [-100, 100] in percent
    @Expose private int waypointBackgroundAlpha = 0x80; // [0, 255]
    @Expose private int viewDistance = 12;
    @Expose private boolean autoSyncToXaerosMinimap = true;
    @Expose private boolean autoSyncToVoxelMap = true;
    @Expose private WaypointSorting.SortMode waypointManagerSortMode = WaypointSorting.SortMode.DEFAULT;
    @Expose private boolean waypointManagerSortReversed = false;
    @Expose private boolean waypointManagerGroupByLists = true;
    @Expose private boolean waypointManagerShowAllDimensions = false;
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

    public boolean isAutoSyncToXaerosMinimap() {
        return isXaerosMinimapLoaded && autoSyncToXaerosMinimap;
    }

    public void setAutoSyncToXaerosMinimap(boolean autoSyncToXaerosMinimap) {
        this.autoSyncToXaerosMinimap = autoSyncToXaerosMinimap;
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
}
