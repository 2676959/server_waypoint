package _959.server_waypoint.common.client.gui.layout;

public interface Expandable {
    void setWidth(int width);
    void setHeight(int height);

    /**
     * Sets the element's layout size. Implementations with expensive relayout work may override this
     * to update both axes in a single pass.
     */
    default void setDimensions(int width, int height) {
        this.setWidth(width);
        this.setHeight(height);
    }

    /**
     * Sets the outer visual width used by layout containers. Padded widgets should override this so
     * the requested width includes their padding.
     */
    default void setVisualWidth(int width) {
        setWidth(width);
    }

    /**
     * Sets the outer visual height used by layout containers. Padded widgets should override this so
     * the requested height includes their padding.
     */
    default void setVisualHeight(int height) {
        setHeight(height);
    }

    /**
     * Sets both outer visual dimensions. Override this when both axes should be applied atomically.
     */
    default void setVisualDimensions(int width, int height) {
        setVisualWidth(width);
        setVisualHeight(height);
    }
}
