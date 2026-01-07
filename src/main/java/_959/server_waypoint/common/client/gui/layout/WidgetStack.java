package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.widgets.ShiftableWidget;
import _959.server_waypoint.util.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Stack widgets horizontally or vertically within one specific direction
 * */
public class WidgetStack extends ShiftableWidget {
    private final int defaultPdx;
    private final boolean toPositive;
    private final boolean isHorizontal;
    private final List<Pair<Widget, Integer>> children = new ArrayList<>();
    private final List<Drawable> drawables = new ArrayList<>();
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

    public <W extends Widget & Drawable> void addChild(W child) {
        this.addChild(child, this.defaultPdx);
    }

    public <W extends Widget & Drawable> void addChild(W child, int pdx) {
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        for (Drawable child : drawables) {
            child.render(context, mouseX, mouseY, deltaTicks);
        }
    }

    private void updateX() {
        int shiftedX = this.getShiftedX();
        if (isHorizontal) {
            for (Pair<? extends Widget, Integer> child : children) {
                child.left().setX(shiftedX + child.right());
            }
        } else {
            for (Pair<? extends Widget, Integer> child : children) {
                child.left().setX(shiftedX);
            }
        }
    }

    private void updateY() {
        int shiftedY = this.getShiftedY();
        if (isHorizontal) {
            for (Pair<? extends Widget, Integer> child : children) {
                child.left().setY(shiftedY);
            }
        } else {
            for (Pair<? extends Widget, Integer> child : children) {
                child.left().setY(shiftedY + child.right());
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
        super.setPosition(x, y);
        int shiftedX = this.getShiftedX();
        int shiftedY = this.getShiftedY();
        if (isHorizontal) {
            for (Pair<? extends Widget, Integer> child : children) {
                child.left().setPosition(shiftedX + child.right(), shiftedY);
            }
        } else {
            for (Pair<? extends Widget, Integer> child : children) {
                child.left().setPosition(shiftedX, shiftedY + child.right());
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
}
