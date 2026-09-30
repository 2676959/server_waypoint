package _959.server_waypoint.common.client.render;

/** Visibility rules for projected in-world waypoints, independent of the render backend. */
final class WaypointRenderVisibility {
    private WaypointRenderVisibility() {
    }

    static boolean isVisible(boolean enabled, boolean worldPresent, boolean loading, boolean hideGui) {
        return enabled && worldPresent && !loading && !hideGui;
    }
}
