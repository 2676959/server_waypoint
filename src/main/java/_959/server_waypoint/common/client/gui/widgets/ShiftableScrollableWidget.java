package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;

public abstract class ShiftableScrollableWidget extends ScrollableWidget implements Shiftable {
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;

    public ShiftableScrollableWidget(int x, int y, int width, int height, Text text) {
        super(x, y, width, height, text);
    }

    @Override
    public int getX() {
        return this.shiftedX;
    }

    @Override
    public int getY() {
        return this.shiftedY;
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.shiftedX = x + this.xOffset;
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.shiftedY = y + this.yOffset;
    }

    @Override
    public void setXOffset(int x) {
        this.xOffset = x;
        this.shiftedX = super.getX() + x;
    }

    @Override
    public void setYOffset(int y) {
        this.yOffset = y;
        this.shiftedY = super.getY() + y;
    }

    @Override
    public int getShiftedX() {
        return this.shiftedX;
    }

    @Override
    public int getShiftedY() {
        return this.shiftedY;
    }
}
