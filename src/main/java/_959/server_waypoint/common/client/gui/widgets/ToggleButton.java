package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

public class ToggleButton extends ShiftableClickableWidget {
    protected final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    private final ToggleButtonCallback callback;
    private boolean state;
    private final Text state0Text;
    private final Text state1Text;
    private final int state0color;
    private final int state1color;


    public ToggleButton(int x, int y, int width, int height, Text state0Text, Text state1Text, int state0color, int state1color, ToggleButtonCallback callback) {
        super(x, y, width, height, Text.of("toggle button"));
        this.state0Text = state0Text;
        this.state1Text = state1Text;
        this.state0color = 0x99000000 | (0x00FFFFFF & state0color);
        this.state1color = 0x99000000 | (0x00FFFFFF & state1color);
        this.callback = callback;
        this.setYOffset(-1);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.state = !this.state;
        this.callback.onToggle(this.state);
    }
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            context.drawBorder(x - 1, y - 2, width + 2, height + 2, BORDER_FOCUS_COLOR);
        }
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : BUTTON_BG_COLOR;
        int fixedY = y - 1;
        context.fill(x, fixedY, x + width, fixedY + height, bgColor);
        int color = this.state ? state1color : state0color;
        context.fill(x, fixedY, x + width, fixedY + height, color);
        Text text = this.state ? state1Text : state0Text;
        int textWidth = textRenderer.getWidth(text);
        int centerX = centered(this.width, textWidth);
        int centerY = centered(this.height, textRenderer.fontHeight);
        context.drawText(textRenderer, text, x + centerX, y + centerY, 0xFFFFFFFF, true);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }
}
