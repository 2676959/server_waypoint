package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ToggleButton extends ShiftableClickableWidget {
    protected final Font textRenderer = Minecraft.getInstance().font;
    private final ToggleButtonCallback callback;
    private boolean state;
    private final Component state0Text;
    private final Component state1Text;
    private final int state0color;
    private final int state1color;


    public ToggleButton(int x, int y, int width, int height, Component state0Text, Component state1Text, int state0color, int state1color, ToggleButtonCallback callback) {
        super(x, y, width, height, Component.nullToEmpty("toggle button"));
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
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            context.renderOutline(x - 1, y - 2, width + 2, height + 2, BORDER_FOCUS_COLOR);
        }
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : BUTTON_BG_COLOR;
        int fixedY = y - 1;
        context.fill(x, fixedY, x + width, fixedY + height, bgColor);
        int color = this.state ? state1color : state0color;
        context.fill(x, fixedY, x + width, fixedY + height, color);
        Component text = this.state ? state1Text : state0Text;
        int textWidth = textRenderer.width(text);
        int centerX = centered(this.width, textWidth);
        int centerY = centered(this.height, textRenderer.lineHeight);
        context.drawString(textRenderer, text, x + centerX, y + centerY, 0xFFFFFFFF, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }
}
