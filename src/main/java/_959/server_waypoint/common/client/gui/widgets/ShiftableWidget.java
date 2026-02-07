package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;

import java.util.function.Consumer;

public abstract class ShiftableWidget implements Widget, Shiftable, Drawable {
    private int x;
    private int y;
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;
    private int width;
    private int height;

    public ShiftableWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.shiftedX = x;
        this.shiftedY = y;
        this.xOffset = 0;
        this.yOffset = 0;
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
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void setX(int x) {
        this.x = x;
        this.shiftedX = x + this.xOffset;
    }

    @Override
    public void setY(int y) {
        this.y = y;
        this.shiftedY = y + this.yOffset;
    }

    @Override
    public void setXOffset(int x) {
        this.xOffset = x;
        this.shiftedX = this.x + x;
    }

    @Override
    public void setYOffset(int y) {
        this.yOffset = y;
        this.shiftedY = this.y + y;
    }

    @Override
    public int getShiftedX() {
        return this.shiftedX;
    }

    @Override
    public int getShiftedY() {
        return this.shiftedY;
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {

    }
}
