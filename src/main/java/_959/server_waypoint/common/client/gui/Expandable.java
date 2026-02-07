package _959.server_waypoint.common.client.gui;

public interface Expandable {
    void setWidth(int width);
    void setHeight(int height);

    default void setDimensions(int width, int height) {
        this.setWidth(width);
        this.setHeight(height);
    }

    /**
     * must be reimplemented when implement with {@link Padding}
     * */
    default void setVisualWidth(int width) {
        setWidth(width);
    }

    /**
     * must be reimplemented when implement with {@link Padding}
     * */
    default void setVisualHeight(int height) {
        setHeight(height);
    }

    default void setVisualDimensions(int width, int height) {
        setVisualWidth(width);
        setVisualHeight(height);
    }
}
