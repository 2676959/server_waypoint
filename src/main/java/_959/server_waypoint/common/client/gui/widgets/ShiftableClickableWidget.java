package _959.server_waypoint.common.client.gui.widgets;

//? if <= 1.20.1
/*import net.minecraft.client.gui.DrawContext;*/
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public abstract class ShiftableClickableWidget extends ClickableWidget implements Shiftable {
    protected int shiftedX;
    protected int shiftedY;
    protected int xOffset;
    protected int yOffset;

    public ShiftableClickableWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    //? if <= 1.20.1 {
    /*public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {}

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.renderWidget(context, mouseX, mouseY, deltaTicks);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?}

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
