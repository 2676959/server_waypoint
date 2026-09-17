package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;

import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.getVisualHeight;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.getVisualWidth;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.setVisualPosition;

/**
 * Packs children inward from either end of a fixed-size layout area.
 *
 * <p>For a horizontal pack, {@link Direction#FORWARD} anchors a child to the left and
 * {@link Direction#REVERSE} anchors it to the right. For a vertical pack, the same options anchor
 * children to the top and bottom respectively. Adding children never changes the pack's dimensions;
 * resize the pack explicitly through {@link Expandable} when its available area changes.
 *
 * <p>This class only manages layout. The owning screen or composite remains responsible for
 * registering and rendering the packed widgets.
 */
public class WidgetPack implements LayoutElement, Expandable {
    private int x;
    private int y;
    private int width;
    private int height;
    private final Orientation orientation;
    private final List<Entry> children = new ArrayList<>();

    public WidgetPack() {
        this(0, 0, 0, 0, Orientation.HORIZONTAL);
    }

    public WidgetPack(int width, int height) {
        this(0, 0, width, height, Orientation.HORIZONTAL);
    }

    public WidgetPack(Orientation orientation) {
        this(0, 0, 0, 0, orientation);
    }

    public WidgetPack(int width, int height, Orientation orientation) {
        this(0, 0, width, height, orientation);
    }

    public WidgetPack(int x, int y, int width, int height, Orientation orientation) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
    }

    /**
     * Adds a child that stacks inward from the selected side.
     */
    public <T extends LayoutElement> void addChild(T child, Direction anchor) {
        this.children.add(new Entry(child, anchor));
        this.layoutChildren();
    }

    /**
     * Adds a child that stacks from the left or top side.
     */
    public <T extends LayoutElement> void addChild(T child) {
        this.addChild(child, Direction.FORWARD);
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
            entry.widget().visitWidgets(consumer);
        }
    }

    public Orientation getOrientation() {
        return this.orientation;
    }

    private void layoutChildren() {
        if (this.orientation == Orientation.HORIZONTAL) {
            this.layoutHorizontal();
        } else {
            this.layoutVertical();
        }
    }

    private void layoutHorizontal() {
        int startX = this.x;
        int endX = this.x + this.width;

        for (Entry entry : this.children) {
            int childWidth = getVisualWidth(entry.widget());
            if (entry.anchor() == Direction.FORWARD) {
                setVisualPosition(entry.widget(), startX, this.y);
                startX += childWidth;
            } else {
                endX -= childWidth;
                setVisualPosition(entry.widget(), endX, this.y);
            }
        }
    }

    private void layoutVertical() {
        int startY = this.y;
        int endY = this.y + this.height;

        for (Entry entry : this.children) {
            int childHeight = getVisualHeight(entry.widget());
            if (entry.anchor() == Direction.FORWARD) {
                setVisualPosition(entry.widget(), this.x, startY);
                startY += childHeight;
            } else {
                endY -= childHeight;
                setVisualPosition(entry.widget(), this.x, endY);
            }
        }
    }

    private record Entry(LayoutElement widget, Direction anchor) {
    }
}
