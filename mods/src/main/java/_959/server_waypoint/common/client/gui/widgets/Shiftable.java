package _959.server_waypoint.common.client.gui.widgets;

public interface Shiftable {
    void setXOffset(int x);
    void setYOffset(int y);
    int getShiftedX();
    int getShiftedY();

    default void setOffsets(int x, int y) {
        setXOffset(x);
        setYOffset(y);
    }
}
