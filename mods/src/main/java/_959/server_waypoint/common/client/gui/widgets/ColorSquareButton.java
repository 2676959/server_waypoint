//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.Colorable;
import _959.server_waypoint.common.client.gui.layout.Padding;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeColors.BORDER_COLOR;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeColors.BORDER_FOCUS_COLOR;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ColorSquareButton extends ShiftableClickableWidget implements Colorable, Padding {
    protected Runnable callback;
    protected int color;
    protected boolean renderBorder;

    public ColorSquareButton(int x, int y, int size, int rgb, boolean renderBorder, Runnable callback) {
        super(x, y, size, size, Component.nullToEmpty("Color picker"));
        this.callback = callback;
        this.color = 0xFF000000 | rgb;
        this.renderBorder = renderBorder;
        this.setYOffset(-1);
    }

    public ColorSquareButton(int x, int y, int size, Runnable callback) {
        this(x, y, size, 0, true, callback);
    }

    public void setCallback(Runnable callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.run();
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        int bdColor = isFocused() || isHovered() ? BORDER_FOCUS_COLOR : renderBorder ? BORDER_COLOR : 0;
        renderOutline(context, x - 1, y - 1, width + 2, width + 2, bdColor);
        context.fill(x, y, x + width, y + width, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    @Override
    public int getColor() {
        return this.color;
    }

    @Override
    public void setColor(int rgb) {
        this.color = 0xFF000000 | rgb;
    }

    @Override
    public int getVisualHeight() {
        return this.height + 2;
    }

    @Override
    public int getVisualWidth() {
        return this.width + 2;
    }

    @Override
    public int getVisualX() {
        return getX() - 1;
    }

    @Override
    public int getVisualY() {
        return getY() - 1;
    }
}
