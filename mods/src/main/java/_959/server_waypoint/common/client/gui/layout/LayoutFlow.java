package _959.server_waypoint.common.client.gui.layout;

public final class LayoutFlow {
    private LayoutFlow() {
    }

    public enum Orientation {
        HORIZONTAL,
        VERTICAL;

        Axis axis() {
            return this == HORIZONTAL ? Axis.HORIZONTAL : Axis.VERTICAL;
        }
    }

    public enum Direction {
        FORWARD,
        REVERSE
    }

    enum Axis {
        HORIZONTAL,
        VERTICAL
    }
}
