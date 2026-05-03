package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class IconButton extends ShiftableClickableWidget {
    private final ResourceLocation icon;
    private final ButtonClickCallback callback;

    public IconButton(int x, int y, int width, int height, Component message, ResourceLocation icon, ButtonClickCallback callback) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.callback = callback;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            context.renderOutline(x, y, width, height, BORDER_FOCUS_COLOR);
        }
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : 0;
        context.fill(x, y, x + width, y + height, bgColor);
        texture(context, icon, x, y, 0, 0, width, height, width, height);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
