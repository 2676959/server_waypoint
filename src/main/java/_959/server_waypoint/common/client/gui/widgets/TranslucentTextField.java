package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.BUTTON_BG_COLOR;

public class TranslucentTextField extends TextFieldWidget implements Shiftable {
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;

    public TranslucentTextField(TextRenderer textRenderer, int x, int y, int width, Text text) {
        super(textRenderer, x, y, width, 11, null, text);
        this.setEditableColor(0xFFFFFFFF);
        this.setDrawsBackground(false);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        context.fill(x, y, x + this.width, y + this.height, BUTTON_BG_COLOR);
        int bdColor = isFocused() | isHovered() ? 0xFFFFFFFF : 0x55FFFFFF;
        context.drawBorder(x, y, this.width, this.height, bdColor);
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
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
