package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Padding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.Widget;

public class PaddingBackground implements Drawable, Padding {
    private final Widget widget;
    private final int topPadding;
    private final int bottomPadding;
    private final int leftPadding;
    private final int rightPadding;
    private final boolean border;
    private final int bgColor;
    private final int bdColor;

    public PaddingBackground(Widget widget, int topBottomPadding, int leftRightPadding, int bgColor, int bdColor, boolean border) {
        this(widget, topBottomPadding, topBottomPadding, leftRightPadding, leftRightPadding, bgColor, bdColor, border);
    }

    public PaddingBackground(Widget widget, int topPadding, int bottomPadding, int leftPadding, int rightPadding, int bgColor, int bdColor, boolean border) {
        this.widget = widget;
        this.topPadding = topPadding;
        this.bottomPadding = bottomPadding;
        this.leftPadding = leftPadding;
        this.rightPadding = rightPadding;
        this.bgColor = bgColor;
        this.bdColor = bdColor;
        this.border = border;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = this.widget.getX();
        int y = this.widget.getY();
        int width = this.widget.getWidth();
        int height = this.widget.getHeight();
        int x1 = x - this.leftPadding;
        int y1 = y - this.topPadding;
        context.fill(x1, y1, x + width + this.rightPadding, y + height + this.bottomPadding, bgColor);
        if (border) {
            context.drawBorder(x1, y1, getVisualWidth(), getVisualHeight(), bdColor);
        }
    }

    @Override
    public int getVisualHeight() {
        return this.widget.getHeight() + this.topPadding + this.bottomPadding;
    }

    @Override
    public int getVisualWidth() {
        return this.widget.getWidth() + this.leftPadding + this.rightPadding;
    }

    @Override
    public int getVisualX() {
        return this.widget.getX() - this.leftPadding;
    }

    @Override
    public int getVisualY() {
        return this.widget.getY() - this.topPadding;
    }

    @Override
    public void setPaddedX(int x) {
        this.widget.setX(x + this.leftPadding);
    }

    @Override
    public void setPaddedY(int y) {
        this.widget.setY(y + this.topPadding);
    }
}
