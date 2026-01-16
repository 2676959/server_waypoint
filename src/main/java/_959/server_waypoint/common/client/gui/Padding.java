package _959.server_waypoint.common.client.gui;

public interface Padding {
    int getVisualHeight();
    int getVisualWidth();
    int getVisualX();
    int getVisualY();
    void setPaddedX(int x);
    void setPaddedY(int y);
    default void setPaddedPosition(int x, int y) {
        setPaddedX(x);
        setPaddedY(y);
    }
}
