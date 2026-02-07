package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import com.google.gson.annotations.Expose;

public class ClientConfig {
    @Expose private int viewDistance = 12;
    @Expose private boolean enableWaypointRender = true;
    @Expose private boolean autoSyncToXaerosMinimap = true;
    private long squaredViewDistanceInBlocks = 12 * 16 * 12 * 16;
    public static boolean isXaerosMinimapLoaded;

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
        this.squaredViewDistanceInBlocks = viewDistance * viewDistance * 16L * 16L;
    }

    public long getSquaredViewDistanceInBlocks() {
        return this.squaredViewDistanceInBlocks;
    }
}
