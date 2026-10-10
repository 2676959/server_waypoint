//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.Colorable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.util.ColorUtils.hexCodeToRgb;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;

/**
 * A hexadecimal color input after a {@code #}. It edits an opaque RGB color as six digits;
 * {@link #argb} makes one that edits an ARGB color as eight.
 */
public class ColorHexCodeField extends TranslucentTextField implements Colorable {
    private static final int RGB_DIGITS = 6;
    private static final int ARGB_DIGITS = 8;
    // Six or eight digits of 6 pixels each, plus 3.
    private static final int RGB_WIDTH = 39;
    private static final int ARGB_WIDTH = 51;

    private final Font textRenderer;
    private final boolean alpha;
    /** An ARGB field's last complete value, which it falls back to while its text is incomplete. */
    private int completeColor;

    public ColorHexCodeField(int x, int y, Component text, Font textRenderer) {
        this(x, y, text, textRenderer, false);
    }

    /**
     * A field for an ARGB color: eight digits, the placeholder {@code AARRGGBB}, and a color that
     * {@link #getColor()} and {@link #setColor(int)} carry with its alpha.
     */
    public static ColorHexCodeField argb(int x, int y, Component text, Font textRenderer) {
        return new ColorHexCodeField(x, y, text, textRenderer, true);
    }

    private ColorHexCodeField(int x, int y, Component text, Font textRenderer, boolean alpha) {
        super(x, y, alpha ? ARGB_WIDTH : RGB_WIDTH, text, textRenderer);
        this.textRenderer = textRenderer;
        this.alpha = alpha;
        this.setMaxLength(this.digitCount());
        this.setPlaceholder(() -> Component.literal(alpha ? "AARRGGBB" : "RRGGBB"));
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.commit();
        }
    }

    /**
     * Finishes an unfinished value, as the field does when it loses focus and a screen can do on Enter.
     * An RGB field pads a short value to six digits. An ARGB field turns six digits into RGB under the
     * alpha of its current color and replaces any other incomplete value with that color.
     */
    public void commit() {
        String text = this.getValue();
        if (text.length() >= this.digitCount()) {
            return;
        }
        if (this.alpha && text.length() == RGB_DIGITS) {
            this.setColor((this.completeColor & 0xFF000000) | Integer.parseUnsignedInt(text, 16));
        } else {
            this.setColor(this.getColor());
        }
    }

    private int digitCount() {
        return this.alpha ? ARGB_DIGITS : RGB_DIGITS;
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
    public void setValue(String value) {
        super.setValue(value);
        this.rememberCompleteColor();
    }

    /** Takes hexadecimal digits only; one leading {@code #}, as in a pasted {@code #D9262626}, is dropped. */
    @Override
    public void insertText(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (text.isEmpty() || digits.matches("[0-9a-fA-F]+")) {
            super.insertText(digits);
            this.rememberCompleteColor();
        }
    }

    /** An ARGB field remembers each value its text holds with all eight digits, however it got there. */
    private void rememberCompleteColor() {
        if (this.alpha && this.getValue().length() == ARGB_DIGITS) {
            this.completeColor = this.getColor();
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.canConsumeInput()) {
            return false;
        } else if ((chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F')) {
            if (this.getValue().length() < this.digitCount()) {
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
        this.isHovered = mouseX >= x1 && mouseY >= y && mouseX <= right && mouseY <= bottom;
        context.fill(x1, y, x1 + this.width + 6, y + this.backgroundHeight, this.surfaceColor());
        drawText(context, textRenderer, "#", x - 4, y + 2, WidgetThemeState.text(this.active), true);
        int bdColor = WidgetThemeState.border(this.active, isFocused(), isHovered());
        renderOutline(context, x1, y, this.width + 6, this.backgroundHeight, bdColor);
        this.renderTextField(context, mouseX, mouseY, deltaTicks);
    }

    /**
     * The color the text spells. An ARGB field reads it only while the text has all eight digits and
     * otherwise returns its last complete value, so an unfinished edit never changes the color.
     */
    @Override
    public int getColor() {
        String text = this.getValue();
        if (this.alpha) {
            return text.length() == ARGB_DIGITS ? Integer.parseUnsignedInt(text, 16) : this.completeColor;
        }
        if (text.isEmpty()) return 0;
        return hexCodeToRgb(text, false);
    }

    /** Shows the color's eight digits in an ARGB field, and only its RGB in the other. */
    @Override
    public void setColor(int color) {
        this.setValue(this.alpha ? String.format("%08X", color) : rgbToHexCode(color & 0xFFFFFF, false));
    }
}
