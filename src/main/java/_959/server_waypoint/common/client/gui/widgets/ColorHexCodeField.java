package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.BUTTON_BG_COLOR;
import static _959.server_waypoint.util.ColorUtils.hexCodeToRgb;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;

public class ColorHexCodeField extends TextFieldWidget implements Shiftable {
    private final TextRenderer textRenderer;
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;

    public ColorHexCodeField(TextRenderer textRenderer, int x, int y, Text text) {
        super(textRenderer, x, y, 39, 11, text);
        this.textRenderer = textRenderer;
        this.setEditableColor(0xFFFFFFFF);
        this.setDrawsBackground(false);
        this.setMaxLength(6);
        this.setPlaceholder(Text.literal("RRGGBB").withColor(Colors.LIGHT_GRAY));
    }

    @Override
    public void write(String text) {
        if (text.isEmpty()) super.write(text);
        else if (text.matches("[0-9a-fA-F]+")) super.write(text);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.isActive()) {
            return false;
        } else if ((chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F')) {
            if (this.getText().length() < 6) {
                this.write(Character.toString(chr).toUpperCase());
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int x1 = x - 6;
        context.fill(x1, y, x + this.width, y + this.height, BUTTON_BG_COLOR);
        context.drawText(textRenderer, "#", x - 4, y + 2, 0xFFFFFFFF, true);
        int bdColor = isFocused() | isHovered() ? 0xFFFFFFFF : 0x55FFFFFF;
        context.drawBorder(x1, y, this.width + 6, this.height, bdColor);
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
    }

    public int getColor() {
        if (this.getText().isEmpty()) return 0;
        return hexCodeToRgb(this.getText(), false);
    }

    public void setColor(int rgb) {
        this.setText(rgbToHexCode(rgb, false));
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
