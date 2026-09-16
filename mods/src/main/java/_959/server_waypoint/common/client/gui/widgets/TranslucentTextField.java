//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;

/** Suggestion-capable text input with the standard translucent surface. */
public class TranslucentTextField extends SuggestingTextInput {
    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
        super(x, y, width, text, textRenderer);
    }

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer, AnchorMode anchorMode) {
        super(x, y, width, text, textRenderer, anchorMode);
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
        context.fill(x + 1, y + 1, right, bottom, WidgetThemeState.controlBackground(this.active, isHovered()));
        int bdColor = WidgetThemeState.border(this.active, isFocused(), isHovered());
        renderOutline(context, x, y, this.width, this.backgroundHeight, bdColor);
        this.renderTextField(context, mouseX, mouseY, deltaTicks);
    }

}
