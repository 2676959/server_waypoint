package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;

import java.util.Objects;

public final class DimensionIconLayout {
    private final int iconSize;
    private final Orientation orientation;
    private final Direction direction;

    public DimensionIconLayout(int iconSize, Orientation orientation, Direction direction) {
        if (iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be positive");
        }

        this.iconSize = iconSize;
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public Bounds viewport(int widgetWidth, int widgetHeight, int headerHeight) {
        int width = Math.max(0, widgetWidth);
        int bodyHeight = Math.max(0, widgetHeight - headerHeight);
        if (this.orientation == Orientation.HORIZONTAL) {
            return new Bounds(0, headerHeight, width, Math.min(this.iconSize, bodyHeight));
        }
        return new Bounds(0, headerHeight, Math.min(this.iconSize, width), bodyHeight);
    }

    public Position iconPosition(int iconIndex, float scrollPosition, Bounds viewport) {
        int viewportSpan = mainSpan(viewport);
        float mainPosition;
        if (this.direction == Direction.FORWARD) {
            mainPosition = scrollPosition + iconIndex * this.iconSize;
        } else {
            mainPosition = viewportSpan - this.iconSize - scrollPosition - iconIndex * this.iconSize;
        }

        if (this.orientation == Orientation.HORIZONTAL) {
            return new Position(viewport.x() + mainPosition, viewport.y());
        }
        return new Position(viewport.x(), viewport.y() + mainPosition);
    }

    public int iconIndexAt(double x, double y, float scrollPosition, int iconCount, Bounds viewport) {
        if (!viewport.contains(x, y)) {
            return -1;
        }

        double mainPosition = this.orientation == Orientation.HORIZONTAL ? x - viewport.x() : y - viewport.y();
        double relativePosition;
        int iconIndex;
        if (this.direction == Direction.FORWARD) {
            relativePosition = mainPosition - scrollPosition;
            iconIndex = (int) Math.floor(relativePosition / this.iconSize);
        } else {
            relativePosition = mainSpan(viewport) - mainPosition - scrollPosition;
            iconIndex = (int) Math.ceil(relativePosition / this.iconSize) - 1;
        }

        return iconIndex >= 0 && iconIndex < iconCount ? iconIndex : -1;
    }

    public float scrollBy(float currentPosition, double amount, int iconCount, Bounds viewport) {
        return clampScroll(currentPosition + (float) amount, iconCount, viewport);
    }

    public float clampScroll(float currentPosition, int iconCount, Bounds viewport) {
        int minimumPosition = Math.min(0, mainSpan(viewport) - iconCount * this.iconSize);
        return Math.max(minimumPosition, Math.min(0, currentPosition));
    }

    public Orientation orientation() {
        return this.orientation;
    }

    public Direction direction() {
        return this.direction;
    }

    private int mainSpan(Bounds viewport) {
        return this.orientation == Orientation.HORIZONTAL ? viewport.width() : viewport.height();
    }

    public record Bounds(int x, int y, int width, int height) {
        boolean contains(double x, double y) {
            return x >= this.x && x < this.x + this.width && y >= this.y && y < this.y + this.height;
        }
    }

    public record Position(float x, float y) {
    }
}
