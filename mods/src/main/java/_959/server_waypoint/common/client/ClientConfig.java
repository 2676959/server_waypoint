package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import com.google.gson.annotations.Expose;

public class ClientConfig {
    @Expose private boolean enableWaypointRender = true;
    @Expose private int waypointScalingFactor = 100; // in percent
    @Expose private int waypointVerticalOffset = 0; // [-100, 100] in percent
    @Expose private int waypointBackgroundAlpha = 0x80; // [0, 255]
    @Expose private int viewDistance = 12;
    @Expose private boolean autoSyncToXaerosMinimap = true;
    public static boolean isXaerosMinimapLoaded = false;

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
}
