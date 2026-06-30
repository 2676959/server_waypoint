package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Axis;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;

public class ExpandableManager implements LayoutElement, Expandable {
    private int x;
    private int y;
    private int width;
    private int height;
    private final Orientation orientation;
    private final Direction direction;
    private final List<Entry> children = new ArrayList<>();

    public ExpandableManager() {
        this(0, 0, 0, 0);
    }

    public ExpandableManager(int width, int height) {
        this(0, 0, width, height);
    }

    public ExpandableManager(Orientation orientation, Direction direction) {
        this(0, 0, 0, 0, orientation, direction);
    }

    public ExpandableManager(int width, int height, Orientation orientation, Direction direction) {
        this(0, 0, width, height, orientation, direction);
    }

    public ExpandableManager(int x, int y, int width, int height) {
        this(x, y, width, height, Orientation.HORIZONTAL, Direction.FORWARD);
    }

    public ExpandableManager(int x, int y, int width, int height, Orientation orientation, Direction direction) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.direction = direction;
    }

    /**
     * Adds a child to this virtual container.
     *
     * <p>On the stacking axis, a ratio of {@code 0} keeps the child's current visual size fixed.
     * Positive ratios split the remaining space after all fixed-size children have been measured.
     * For example, in a horizontal 100px container with one fixed 20px child and two flexible
     * children using width ratios {@code 1} and {@code 3}, the flexible children receive
     * {@code 20px} and {@code 60px}.
     *
     * <p>On the cross axis, a ratio of {@code 0} keeps the child's current visual size. Any positive
     * ratio makes the child fill the container on that axis. The exact positive value is ignored on
     * the cross axis because children are not competing for that space.
     */
    public <T extends LayoutElement & Expandable> void addChild(T child, int widthRatio, int heightRatio) {
        if (widthRatio < 0 || heightRatio < 0) {
            throw new IllegalArgumentException("Expandable ratios cannot be negative");
        }

        this.children.add(new Entry(child, child, widthRatio, heightRatio));
        this.layoutChildren();
    }

    @Override
    public void setX(int x) {
        this.x = x;
        this.layoutChildren();
    }

    @Override
    public void setY(int y) {
        this.y = y;
        this.layoutChildren();
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
        this.layoutChildren();
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.layoutChildren();
    }

    @Override
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        this.layoutChildren();
    }

    @Override
    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
        this.layoutChildren();
    }

    @Override
    public void setVisualWidth(int width) {
        this.setWidth(width);
    }

    @Override
    public void setVisualHeight(int height) {
        this.setHeight(height);
    }

    @Override
    public void setVisualDimensions(int width, int height) {
        this.setDimensions(width, height);
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        for (Entry entry : this.children) {
            entry.widget.visitWidgets(consumer);
        }
    }

    public Orientation getOrientation() {
        return this.orientation;
    }

    public Direction getDirection() {
        return this.direction;
    }

    private void layoutChildren() {
        if (this.children.isEmpty()) {
            return;
        }

        List<Bounds> bounds = new ArrayList<>(this.children.size());
        int[] widths = allocateAxis(Axis.HORIZONTAL);
        int[] heights = allocateAxis(Axis.VERTICAL);

        for (int i = 0; i < this.children.size(); i++) {
            Entry entry = this.children.get(i);
            entry.expandable.setVisualDimensions(widths[i], heights[i]);
            bounds.add(new Bounds(widths[i], heights[i]));
        }

        if (this.orientation == Orientation.HORIZONTAL) {
            layoutHorizontal(bounds);
        } else {
            layoutVertical(bounds);
        }
    }

    private int[] allocateAxis(Axis axis) {
        int containerSize = axis == Axis.HORIZONTAL ? this.width : this.height;
        if (axis != this.orientation.axis()) {
            return allocateCrossAxis(axis, containerSize);
        }

        int totalRatio = 0;
        int totalFixedSize = 0;

        for (Entry entry : this.children) {
            int ratio = entry.ratio(axis);
            if (ratio > 0) {
                totalRatio += ratio;
            } else {
                totalFixedSize += entry.visualSize(axis);
            }
        }

        int availableSpace = Math.max(0, containerSize - totalFixedSize);
        int[] sizes = new int[this.children.size()];

        int currentRatioSum = 0;
        int allocatedSpace = 0;
        for (int i = 0; i < this.children.size(); i++) {
            Entry entry = this.children.get(i);
            int ratio = entry.ratio(axis);
            if (ratio > 0) {
                currentRatioSum += ratio;
                int targetSpace = (int) ((long) availableSpace * currentRatioSum / totalRatio);
                int size = targetSpace - allocatedSpace;
                allocatedSpace += size;
                sizes[i] = size;
            } else {
                sizes[i] = entry.visualSize(axis);
            }
        }

        return sizes;
    }

    private int[] allocateCrossAxis(Axis axis, int containerSize) {
        int[] sizes = new int[this.children.size()];
        for (int i = 0; i < this.children.size(); i++) {
            Entry entry = this.children.get(i);
            sizes[i] = entry.ratio(axis) > 0 ? containerSize : entry.visualSize(axis);
        }

        return sizes;
    }

    private void layoutHorizontal(List<Bounds> bounds) {
        if (this.direction == Direction.FORWARD) {
            int childX = this.x;
            for (int i = 0; i < this.children.size(); i++) {
                Bounds childBounds = bounds.get(i);
                setChildVisualPosition(this.children.get(i), childX, this.y);
                childX += childBounds.width;
            }
        } else {
            int childRight = this.x + this.width;
            for (int i = 0; i < this.children.size(); i++) {
                Bounds childBounds = bounds.get(i);
                childRight -= childBounds.width;
                setChildVisualPosition(this.children.get(i), childRight, this.y);
            }
        }
    }

    private void layoutVertical(List<Bounds> bounds) {
        if (this.direction == Direction.FORWARD) {
            int childY = this.y;
            for (int i = 0; i < this.children.size(); i++) {
                Bounds childBounds = bounds.get(i);
                setChildVisualPosition(this.children.get(i), this.x, childY);
                childY += childBounds.height;
            }
        } else {
            int childBottom = this.y + this.height;
            for (int i = 0; i < this.children.size(); i++) {
                Bounds childBounds = bounds.get(i);
                childBottom -= childBounds.height;
                setChildVisualPosition(this.children.get(i), this.x, childBottom);
            }
        }
    }

    private void setChildVisualPosition(Entry entry, int x, int y) {
        if (entry.widget instanceof Padding padding) {
            padding.setPaddedPosition(x, y);
        } else {
            entry.widget.setPosition(x, y);
        }
    }

    private record Bounds(int width, int height) {
    }

    private record Entry(LayoutElement widget, Expandable expandable, int widthRatio, int heightRatio) {
        private int ratio(Axis axis) {
            return axis == Axis.HORIZONTAL ? this.widthRatio : this.heightRatio;
        }

        private int visualSize(Axis axis) {
            if (this.widget instanceof Padding padding) {
                return axis == Axis.HORIZONTAL ? padding.getVisualWidth() : padding.getVisualHeight();
            }

            return axis == Axis.HORIZONTAL ? this.widget.getWidth() : this.widget.getHeight();
        }
    }
}
