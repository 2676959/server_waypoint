package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class TranslucentTextField extends EditBox implements Shiftable {
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;
    protected final int backgroundHeight;

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
        super(textRenderer, x, y, width, textRenderer.lineHeight, null, text);
        this.setTextColor(0xFFFFFFFF);
        this.setBordered(false);
        this.backgroundHeight = this.height + 2;
    }

    @Override
    public void
    //$ renderWidget_swap
    renderWidget
            (GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        context.fill(x + 1, y + 1, right, bottom, BUTTON_BG_COLOR);
        this.isHovered = mouseX >= x && mouseY >= y && mouseX <= right && mouseY <= bottom;
        int bdColor = isFocused() | isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        context.renderOutline(x, y, this.width, this.backgroundHeight, bdColor);
        super.
        //$ renderWidget_swap
        renderWidget
        (context, mouseX, mouseY, deltaTicks);
    }

    public void renderTextField(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.
        //$ renderWidget_swap
        renderWidget
        (context, mouseX, mouseY, deltaTicks);
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
