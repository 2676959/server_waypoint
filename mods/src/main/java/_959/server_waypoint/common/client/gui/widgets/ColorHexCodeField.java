//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.Colorable;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PLACEHOLDER;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.util.ColorUtils.hexCodeToRgb;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;

public class ColorHexCodeField extends TranslucentTextField implements Colorable {
    private final Font textRenderer;
    private int hintColor;
    private boolean hintColorInitialized;

    public ColorHexCodeField(int x, int y, net.minecraft.network.chat.Component text, Font textRenderer) {
        super(x, y, 39, text, textRenderer);
        this.textRenderer = textRenderer;
        this.setMaxLength(6);
        this.updateThemeHint();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        String text = this.getValue();
        if (!focused && text.length() < 6) {
            // complete hex code to length 6
            setColor(getColor());
        }
    }

    @Override
    public void setVisualWidth(int width) {
        this.setWidth(width - 6);
    }

    @Override
    public int getVisualWidth() {
        return this.width + 6;
    }

    @Override
    public int getVisualX() {
        return getX() - 8;
    }

    @Override
    public void insertText(String text) {
        if (text.isEmpty()) super.insertText(text);
        else if (text.matches("[0-9a-fA-F]+")) super.insertText(text);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.canConsumeInput()) {
            return false;
        } else if ((chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F')) {
            if (this.getValue().length() < 6) {
                this.insertText(Character.toString(chr).toUpperCase());
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
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int x1 = x - 6;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        this.updateThemeTextColors();
        this.updateThemeHint();
        this.isHovered = mouseX >= x1 && mouseY >= y && mouseX <= right && mouseY <= bottom;
        context.fill(x1 + 1, y + 1, right, bottom, WidgetThemeState.controlBackground(this.active, isHovered()));
        drawText(context, textRenderer, "#", x - 4, y + 2, WidgetThemeState.text(this.active), true);
        int bdColor = WidgetThemeState.border(this.active, isFocused(), isHovered());
        renderOutline(context, x1, y, this.width + 6, this.backgroundHeight, bdColor);
        this.renderTextField(context, mouseX, mouseY, deltaTicks);
    }

    private void updateThemeHint() {
        int color = WidgetThemeManager.getColor(this.active ? TEXT_PLACEHOLDER : TEXT_DISABLED);
        if (this.hintColorInitialized && this.hintColor == color) {
            return;
        }
        this.hintColor = color;
        this.hintColorInitialized = true;
        this.setHint(toVanillaText(Component.text("RRGGBB").color(TextColor.color(color & 0x00FFFFFF))));
    }

    @Override
    public int getColor() {
        if (this.getValue().isEmpty()) return 0;
        return hexCodeToRgb(this.getValue(), false);
    }

    @Override
    public void setColor(int rgb) {
        this.setValue(rgbToHexCode(rgb & 0xFFFFFF, false));
    }
}
