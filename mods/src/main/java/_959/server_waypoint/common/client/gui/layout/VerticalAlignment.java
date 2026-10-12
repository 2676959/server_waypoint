package _959.server_waypoint.common.client.gui.layout;

/** Adjusts vertical row centering for content whose visible center differs from its layout box. */
public interface VerticalAlignment {
    /** Visible content center in screen pixels below the top of the element's visual bounds. */
    float getVerticalAlignmentCenter();
}
