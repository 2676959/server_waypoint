//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;

/** Suggestion-capable text input with the standard translucent surface. */
public class TranslucentTextField extends SuggestingTextInput {
    private boolean invalid;

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
        super(x, y, width, text, textRenderer);
    }

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer, AnchorMode anchorMode) {
        super(x, y, width, text, textRenderer, anchorMode);
    }

    /** The fill painted beneath the outline, for the field's current state. */
    protected int surfaceColor() {
        return WidgetThemeState.controlBackground(this.active, isHovered());
    }

    /** Draws the outline in the danger color instead of the border color, for a value the form rejects. */
    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        this.updateThemeTextColors();
        this.isHovered = mouseX >= x && mouseY >= y && mouseX <= right && mouseY <= bottom;
        context.fill(x, y, x + this.width, y + this.backgroundHeight, this.surfaceColor());
        int bdColor = this.invalid ? getColor(DANGER) : WidgetThemeState.border(this.active, isFocused(), isHovered());
        renderOutline(context, x, y, this.width, this.backgroundHeight, bdColor);
        this.renderTextField(context, mouseX, mouseY, deltaTicks);
    }

}
