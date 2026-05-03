package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.widgets.ShiftableWidget;
import _959.server_waypoint.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.layouts.LayoutElement;

/**
 * Stack widgets horizontally or vertically within one specific direction
 * */
public class WidgetStack extends ShiftableWidget {
    private final int defaultPdx;
    private final boolean toPositive;
    private final boolean isHorizontal;
    private final List<AbstractWidget> clickable = new ArrayList<>();
    private final List<Pair<LayoutElement, Integer>> children = new ArrayList<>();
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
        super(x, y, 0, 0);
        this.defaultPdx = defaultPdx;
        this.toPositive = toPositive;
        this.isHorizontal = isHorizontal;
    }

    public <W extends AbstractWidget & Padding> void addPaddedClickable(W child, int pdx) {
        this.addPadded(child, pdx);
        this.clickable.add(child);
    }

    public <W extends LayoutElement & Padding & Renderable> void addPadded(W child, int pdx) {
        int widgetSpan, relativePos, widgetPerpSpan;
        if (isHorizontal) {
            widgetSpan = child.getVisualWidth();
            widgetPerpSpan = child.getVisualHeight();
            relativePos = this.toPositive ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            child.setPaddedPosition(this.getShiftedX() + relativePos, this.getShiftedY());
        } else {
            widgetSpan = child.getVisualHeight();
            widgetPerpSpan = child.getVisualWidth();
            relativePos = this.toPositive ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            child.setPaddedPosition(this.getShiftedX(), this.getShiftedY() + relativePos);
        }
        if (widgetPerpSpan > offAxisSize) {
            this.offAxisSize = widgetPerpSpan;
        }
        this.drawables.add(child);
        this.children.add(new Pair<>(child, relativePos));
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
        if (isHorizontal) {
            widgetSpan = child.getWidth();
            widgetPerpSpan = child.getHeight();
            relativePos = this.toPositive ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            child.setPosition(this.getShiftedX() + relativePos, this.getShiftedY());
        } else {
            widgetSpan = child.getHeight();
            widgetPerpSpan = child.getWidth();
            relativePos = this.toPositive ? this.mainAxisSize + pdx : -(this.mainAxisSize + pdx + widgetSpan);
            child.setPosition(this.getShiftedX(), this.getShiftedY() + relativePos);
        }
        if (widgetPerpSpan > offAxisSize) {
            this.offAxisSize = widgetPerpSpan;
        }
        this.drawables.add(child);
        this.children.add(new Pair<>(child, relativePos));
        this.mainAxisSize += widgetSpan + pdx;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AbstractWidget child : clickable) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        for (Renderable child : drawables) {
            child.render(context, mouseX, mouseY, deltaTicks);
        }
    }

    private void updateX() {
        int shiftedX = this.getShiftedX();
        if (isHorizontal) {
            for (Pair<? extends LayoutElement, Integer> child : children) {
                LayoutElement widget = child.left();
                Integer relativePos = child.right();
                if (widget instanceof Padding) {
                    ((Padding) widget).setPaddedX(shiftedX + relativePos);
                } else {
                    widget.setX(shiftedX + relativePos);
                }
            }
        } else {
            for (Pair<? extends LayoutElement, Integer> child : children) {
                LayoutElement widget = child.left();
                if (widget instanceof Padding) {
                    ((Padding) widget).setPaddedX(shiftedX);
                } else {
                    widget.setX(shiftedX);
                }
            }
        }
    }

    private void updateY() {
        int shiftedY = this.getShiftedY();
        if (isHorizontal) {
            for (Pair<? extends LayoutElement, Integer> child : children) {
                LayoutElement widget = child.left();
                if (widget instanceof Padding) {
                    ((Padding) widget).setPaddedY(shiftedY);
                } else {
                    widget.setY(shiftedY);
                }
            }
        } else {
            for (Pair<? extends LayoutElement, Integer> child : children) {
                Integer relativePos = child.right();
                LayoutElement widget = child.left();
                if (widget instanceof Padding) {
                    ((Padding) widget).setPaddedY(shiftedY + relativePos);
                } else {
                    widget.setY(shiftedY + relativePos);
                }
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
        if (isHorizontal) {
            for (Pair<? extends LayoutElement, Integer> child : children) {
                LayoutElement widget = child.left();
                if (widget instanceof Padding) {
                    ((Padding) widget).setPaddedPosition(shiftedX + child.right(), shiftedY);
                } else {
                    widget.setPosition(shiftedX + child.right(), shiftedY);
                }
            }
        } else {
            for (Pair<? extends LayoutElement, Integer> child : children) {
                LayoutElement widget = child.left();
                if (widget instanceof Padding) {
                    ((Padding) widget).setPaddedPosition(shiftedX, shiftedY + child.right());
                } else {
                    widget.setPosition(shiftedX, shiftedY + child.right());
                }
            }
        }
    }

    @Override
    public int getWidth() {
        return isHorizontal ? mainAxisSize : offAxisSize;
    }

    @Override
    public int getHeight() {
        return isHorizontal ? offAxisSize : mainAxisSize;
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        this.clickable.forEach(consumer);
    }
}
