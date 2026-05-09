package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.BORDER_COLOR;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.BORDER_FOCUS_COLOR;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ColorSquareButton extends ShiftableClickableWidget implements Colorable {
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
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
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
}
