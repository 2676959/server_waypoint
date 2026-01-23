package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

public class IconButton extends ShiftableClickableWidget {
    private final Identifier icon;
    private final ButtonClickCallback callback;

    public IconButton(int x, int y, int width, int height, Text message, Identifier icon, ButtonClickCallback callback) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.callback = callback;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            context.drawBorder(x, y, width, height, BORDER_FOCUS_COLOR);
        }
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : 0;
        context.fill(x, y, x + width, y + height, bgColor);
        context.drawTexture(RenderLayer::getGuiTextured, icon, x, y, 0, 0, width, height,  width, height);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
