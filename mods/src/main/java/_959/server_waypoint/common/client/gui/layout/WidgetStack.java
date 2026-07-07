//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.widgets.ShiftableWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.layouts.LayoutElement;

import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.getVisualHeight;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.getVisualWidth;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.setVisualPosition;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.setVisualX;
import static _959.server_waypoint.common.client.gui.layout.VisualPositioning.setVisualY;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}

/**
 * Stack widgets horizontally or vertically within one specific direction
 * */
public class WidgetStack extends ShiftableWidget {
    private final int defaultPdx;
    private final Direction direction;
    private final Orientation orientation;
    private final boolean useVisualBounds;
    private final List<AbstractWidget> clickable = new ArrayList<>();
    private final List<Entry> children = new ArrayList<>();
    private final List<Renderable> drawables = new ArrayList<>();
    private int mainAxisSize = 0;
    private int offAxisSize = 0;

    public WidgetStack(int x, int y, int defaultPdx) {
        this(x, y, defaultPdx, true, true);
    }

    public WidgetStack(int x, int y, int defaultPdx, boolean toPositive) {
        this(x, y, defaultPdx, toPositive, true);
    }

    public WidgetStack(int x, int y, int defaultPdx, boolean toPositive, boolean isHorizontal) {
        this(x, y, defaultPdx, orientationFromBoolean(isHorizontal), directionFromBoolean(toPositive));
    }

    public WidgetStack(int x, int y, int defaultPdx, boolean toPositive, boolean isHorizontal, boolean useVisualBounds) {
        this(x, y, defaultPdx, orientationFromBoolean(isHorizontal), directionFromBoolean(toPositive), useVisualBounds);
    }

    public WidgetStack(int x, int y, int defaultPdx, Orientation orientation, Direction direction) {
        this(x, y, defaultPdx, orientation, direction, false);
    }

    public WidgetStack(int x, int y, int defaultPdx, Orientation orientation, Direction direction, boolean useVisualBounds) {
        super(x, y, 0, 0);
        this.defaultPdx = defaultPdx;
        this.direction = direction;
        this.orientation = orientation;
        this.useVisualBounds = useVisualBounds;
    }

    public <W extends AbstractWidget & Padding> void addPaddedClickable(W child, int pdx) {
        this.addPadded(child, pdx);
        this.clickable.add(child);
    }

    public <W extends LayoutElement & Padding & Renderable> void addPadded(W child, int pdx) {
        int widgetSpan, relativePos, widgetPerpSpan;
        if (this.orientation == Orientation.HORIZONTAL) {
            widgetSpan = child.getVisualWidth();
            widgetPerpSpan = child.getVisualHeight();
            relativePos = this.direction == Direction.FORWARD ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            setVisualPosition(child, this.getShiftedX() + relativePos, this.getShiftedY());
        } else {
            widgetSpan = child.getVisualHeight();
            widgetPerpSpan = child.getVisualWidth();
            relativePos = this.direction == Direction.FORWARD ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            setVisualPosition(child, this.getShiftedX(), this.getShiftedY() + relativePos);
        }
        if (widgetPerpSpan > offAxisSize) {
            this.offAxisSize = widgetPerpSpan;
        }
        this.drawables.add(child);
        this.children.add(new Entry(child, relativePos, true));
        this.mainAxisSize += widgetSpan + pdx;
    }

    public <W extends AbstractWidget> void addClickable(W child) {
        this.addChild(child, this.defaultPdx);
        this.clickable.add(child);
    }

    public <W extends AbstractWidget> void addClickable(W child, int pdx) {
        this.addChild(child, pdx);
        this.clickable.add(child);
    }

    public <W extends LayoutElement & Renderable> void addChild(W child) {
        this.addChild(child, this.defaultPdx);
    }

    public <W extends LayoutElement & Renderable> void addChild(W child, int pdx) {
        int widgetSpan, relativePos, widgetPerpSpan;
        if (this.orientation == Orientation.HORIZONTAL) {
            widgetSpan = getWidth(child);
            widgetPerpSpan = getHeight(child);
            relativePos = this.direction == Direction.FORWARD ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            setPosition(child, relativePos, this.getShiftedX() + relativePos, this.getShiftedY());
        } else {
            widgetSpan = getHeight(child);
            widgetPerpSpan = getWidth(child);
            relativePos = this.direction == Direction.FORWARD ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            setPosition(child, relativePos, this.getShiftedX(), this.getShiftedY() + relativePos);
        }
        if (widgetPerpSpan > offAxisSize) {
            this.offAxisSize = widgetPerpSpan;
        }
        this.drawables.add(child);
        this.children.add(new Entry(child, relativePos, this.useVisualBounds));
        this.mainAxisSize += widgetSpan + pdx;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //? if >= 1.21.9 {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
        for (AbstractWidget child : clickable) {
            if (child.mouseClicked(event, false)) {
                return true;
            }
        }
        //?} else {
        /*for (AbstractWidget child : clickable) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        *///?}
        return false;
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        for (Renderable child : drawables) {
            child.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
        }
    }

    private void updateX() {
        int shiftedX = this.getShiftedX();
        if (this.orientation == Orientation.HORIZONTAL) {
            for (Entry child : children) {
                setX(child, shiftedX + child.relativePos());
            }
        } else {
            for (Entry child : children) {
                setX(child, shiftedX);
            }
        }
    }

    private void updateY() {
        int shiftedY = this.getShiftedY();
        if (this.orientation == Orientation.HORIZONTAL) {
            for (Entry child : children) {
                setY(child, shiftedY);
            }
        } else {
            for (Entry child : children) {
                setY(child, shiftedY + child.relativePos());
            }
        }
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.updateX();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.updateY();
    }

    @Override
    public void setXOffset(int x) {
        super.setXOffset(x);
        this.updateX();
    }

    @Override
    public void setYOffset(int y) {
        super.setYOffset(y);
        this.updateY();
    }

    @Override
    public void setPosition(int x, int y) {
        super.setX(x);
        super.setY(y);
        int shiftedX = this.getShiftedX();
        int shiftedY = this.getShiftedY();
        if (this.orientation == Orientation.HORIZONTAL) {
            for (Entry child : children) {
                setPosition(child, shiftedX + child.relativePos(), shiftedY);
            }
        } else {
            for (Entry child : children) {
                setPosition(child, shiftedX, shiftedY + child.relativePos());
            }
        }
    }

    @Override
    public int getWidth() {
        return this.orientation == Orientation.HORIZONTAL ? mainAxisSize : offAxisSize;
    }

    @Override
    public int getHeight() {
        return this.orientation == Orientation.HORIZONTAL ? offAxisSize : mainAxisSize;
    }

    public Orientation getOrientation() {
        return this.orientation;
    }

    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        this.clickable.forEach(consumer);
    }

    private static Direction directionFromBoolean(boolean toPositive) {
        return toPositive ? Direction.FORWARD : Direction.REVERSE;
    }

    private static Orientation orientationFromBoolean(boolean isHorizontal) {
        return isHorizontal ? Orientation.HORIZONTAL : Orientation.VERTICAL;
    }

    private int getWidth(LayoutElement child) {
        return this.useVisualBounds ? getVisualWidth(child) : child.getWidth();
    }

    private int getHeight(LayoutElement child) {
        return this.useVisualBounds ? getVisualHeight(child) : child.getHeight();
    }

    private void setPosition(LayoutElement widget, int relativePos, int x, int y) {
        setPosition(new Entry(widget, relativePos, this.useVisualBounds), x, y);
    }

    private static void setPosition(Entry entry, int x, int y) {
        if (entry.usesVisualBounds()) {
            setVisualPosition(entry.widget(), x, y);
        } else {
            entry.widget().setPosition(x, y);
        }
    }

    private static void setX(Entry entry, int x) {
        if (entry.usesVisualBounds()) {
            setVisualX(entry.widget(), x);
        } else {
            entry.widget().setX(x);
        }
    }

    private static void setY(Entry entry, int y) {
        if (entry.usesVisualBounds()) {
            setVisualY(entry.widget(), y);
        } else {
            entry.widget().setY(y);
        }
    }

    private record Entry(LayoutElement widget, int relativePos, boolean useVisualBounds) {
        private boolean usesVisualBounds() {
            return this.useVisualBounds && this.widget instanceof Padding;
        }
    }
}
