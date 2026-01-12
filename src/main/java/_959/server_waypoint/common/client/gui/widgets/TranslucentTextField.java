package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

public class TranslucentTextField extends TextFieldWidget implements Shiftable {
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;
    protected final int backgroundHeight;

    public TranslucentTextField(int x, int y, int width, Text text, TextRenderer textRenderer) {
        super(textRenderer, x, y, width, textRenderer.fontHeight, null, text);
        this.setEditableColor(0xFFFFFFFF);
        this.setDrawsBackground(false);
        this.backgroundHeight = this.height + 2;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        context.fill(x + 1, y + 1, right, bottom, BUTTON_BG_COLOR);
        this.hovered = mouseX >= x && mouseY >= y && mouseX <= right && mouseY <= bottom;
        int bdColor = isFocused() | isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        context.drawBorder(x, y, this.width, this.backgroundHeight, bdColor);
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
    }

    public void renderTextField(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
    }

    public int getVisualHeight() {
        return this.backgroundHeight;
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
