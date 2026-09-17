package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;

import java.util.Objects;

public final class DimensionIconLayout {
    private final int iconSize;
    private final Orientation orientation;
    private final Direction direction;
    private final int iconSpacing;

    public DimensionIconLayout(int iconSize, Orientation orientation, Direction direction) {
        this(iconSize, orientation, direction, 0);
    }

    public DimensionIconLayout(
            int iconSize,
            Orientation orientation,
            Direction direction,
            int iconSpacing
    ) {
        if (iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be positive");
        }
        if (iconSpacing < 0) {
            throw new IllegalArgumentException("Icon spacing must be non-negative");
        }

        this.iconSize = iconSize;
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.iconSpacing = iconSpacing;
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
        int iconStride = this.iconSize + this.iconSpacing;
        float mainPosition;
        if (this.direction == Direction.FORWARD) {
            mainPosition = scrollPosition + iconIndex * iconStride;
        } else {
            mainPosition = viewportSpan - this.iconSize - scrollPosition - iconIndex * iconStride;
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

        double mainCoordinate = this.orientation == Orientation.HORIZONTAL ? x : y;
        double viewportStart = this.orientation == Orientation.HORIZONTAL ? viewport.x() : viewport.y();
        double mainPosition = mainCoordinate - viewportStart;
        int iconStride = this.iconSize + this.iconSpacing;
        double relativePosition;
        int iconIndex;
        if (this.direction == Direction.FORWARD) {
            relativePosition = mainPosition - scrollPosition;
            iconIndex = (int) Math.floor(relativePosition / iconStride);
        } else {
            relativePosition = mainSpan(viewport) - mainPosition - scrollPosition;
            iconIndex = (int) Math.ceil(relativePosition / iconStride) - 1;
        }

        if (iconIndex < 0 || iconIndex >= iconCount) {
            return -1;
        }
        Position iconPosition = this.iconPosition(iconIndex, scrollPosition, viewport);
        double iconStart = this.orientation == Orientation.HORIZONTAL
                ? iconPosition.x()
                : iconPosition.y();
        return mainCoordinate >= iconStart && mainCoordinate < iconStart + this.iconSize
                ? iconIndex
                : -1;
    }

    public float scrollBy(float currentPosition, double amount, int iconCount, Bounds viewport) {
        return clampScroll(currentPosition + (float) amount, iconCount, viewport);
    }

    public float clampScroll(float currentPosition, int iconCount, Bounds viewport) {
        int contentSpan = iconCount <= 0
                ? 0
                : iconCount * this.iconSize + (iconCount - 1) * this.iconSpacing;
        int minimumPosition = Math.min(0, mainSpan(viewport) - contentSpan);
        return Math.max(minimumPosition, Math.min(0, currentPosition));
    }

    public Orientation orientation() {
        return this.orientation;
    }

    public Direction direction() {
        return this.direction;
    }

    public int iconSpacing() {
        return this.iconSpacing;
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
