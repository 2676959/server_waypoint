//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class TranslucentButton extends ShiftableClickableWidget {
    private final ButtonClickCallback callback;
    protected final Component text;
    protected final Font textRenderer = Minecraft.getInstance().font;
    protected final int textWidth;

    public TranslucentButton(int x, int y, int width, int height, Component text, ButtonClickCallback callback) {
        super(x, y, width, height, text);
        this.text = text;
        this.callback = callback;
        this.textWidth = textRenderer.width(text);
        this.setYOffset(-1);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        int bdColor = isFocused() || isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        renderOutline(context, x - 1, y - 2, width + 2, height + 2, bdColor);
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : BUTTON_BG_COLOR;
        int fixedY = y - 1;
        context.fill(x, fixedY, x + width, fixedY + height, bgColor);
        int centerX = centered(this.width, textWidth);
        int centerY = centered(this.height, textRenderer.lineHeight);
        context.
        //$ gui_text_method_swap
        text
                (textRenderer, this.text, x + centerX, y + centerY, 0xFFFFFFFF, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
