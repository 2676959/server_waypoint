package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

public class TranslucentButton extends ShiftableClickableWidget {
    private final ButtonClickCallback callback;
    protected final Text text;
    protected final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

    public TranslucentButton(int x, int y, int width, int height, Text text) {
        super(x, y, width, height, text);
        this.text = text;
        this.callback = () -> {};
    }

    public TranslucentButton(int x, int y, int width, int height, Text text, ButtonClickCallback callback) {
        super(x, y, width, height, text);
        this.text = text;
        this.callback = callback;
        this.setYOffset(-1);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            context.drawBorder(x - 1, y - 1, width + 2, height + 2, BORDER_COLOR);
        }
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : BUTTON_BG_COLOR;
        context.fill(x, y, x + width, y + height, bgColor);
        int textWidth = textRenderer.getWidth(text);
        int centerX = centered(this.width, textWidth);
        int centerY = centered(this.height, textRenderer.fontHeight);
        context.drawText(textRenderer, this.text, x + centerX, y + centerY, 0xFFFFFFFF, true);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
