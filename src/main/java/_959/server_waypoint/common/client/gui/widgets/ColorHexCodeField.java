package _959.server_waypoint.common.client.gui.widgets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.util.ColorUtils.hexCodeToRgb;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;

public class ColorHexCodeField extends TranslucentTextField implements Colorable {
    private final TextRenderer textRenderer;

    public ColorHexCodeField(int x, int y, Text text, TextRenderer textRenderer) {
        super(x, y, 39, text, textRenderer);
        this.textRenderer = textRenderer;
        this.setMaxLength(6);
        this.setPlaceholder(toVanillaText(Component.text("RRGGBB").color(TextColor.color(MUTED_FONT_COLOR))));
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        String text = this.getText();
        if (!focused && text.length() < 6) {
            // complete hex code to length 6
            setColor(getColor());
        }
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
    public void
    //$ renderWidget_swap
    renderWidget
            (DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int x1 = x - 6;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        context.fill(x1 + 1, y + 1, right, bottom, BUTTON_BG_COLOR);
        context.drawText(textRenderer, "#", x - 4, y + 2, 0xFFFFFFFF, true);
        this.hovered = mouseX >= x1 && mouseY >= y && mouseX <= right && mouseY <= bottom;
        int bdColor = isFocused() | isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        context.drawBorder(x1, y, this.width + 6, this.backgroundHeight, bdColor);
        this.renderTextField(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public int getColor() {
        if (this.getText().isEmpty()) return 0;
        return hexCodeToRgb(this.getText(), false);
    }

    @Override
    public void setColor(int rgb) {
        this.setText(rgbToHexCode(rgb & 0xFFFFFF, false));
    }
}
