//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ToggleButton extends ShiftableClickableWidget implements Expandable, Padding {
    private static final int DEFAULT_Y_OFFSET = -1;
    static final int OUTLINE_LEFT_PADDING = 1;
    static final int OUTLINE_TOP_PADDING = 2;

    protected final Font textRenderer = Minecraft.getInstance().font;
    private final ToggleButtonCallback callback;
    private final AnchorMode anchorMode;
    private int anchorX;
    private int anchorY;
    private boolean state;
    private final Component state0Text;
    private final Component state1Text;
    private final int state0color;
    private final int state1color;

    public ToggleButton(int x, int y, int width, int height, Component state0Text,
                        Component state1Text, int state0color, int state1color,
                        ToggleButtonCallback callback) {
        this(x, y, width, height, state0Text, state1Text, state0color, state1color, callback, AnchorMode.CONTENT);
    }

    public ToggleButton(int x, int y, int width, int height, Component state0Text,
                        Component state1Text, int state0color, int state1color,
                        ToggleButtonCallback callback, AnchorMode anchorMode) {
        super(
                AnchorMode.normalize(anchorMode).getContentX(x, OUTLINE_LEFT_PADDING),
                AnchorMode.normalize(anchorMode).getContentY(y, OUTLINE_TOP_PADDING),
                width,
                height,
                Component.nullToEmpty("toggle button")
        );
        this.anchorMode = AnchorMode.normalize(anchorMode);
        this.state0Text = state0Text;
        this.state1Text = state1Text;
        this.state0color = 0x99000000 | (0x00FFFFFF & state0color);
        this.state1color = 0x99000000 | (0x00FFFFFF & state1color);
        this.callback = callback;
        this.setX(x);
        this.setY(y);
        if (this.anchorMode == AnchorMode.CONTENT) {
            this.setYOffset(DEFAULT_Y_OFFSET);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.state = !this.state;
        this.callback.onToggle(this.state);
    }

    @Override
    public void setX(int x) {
        this.anchorX = x;
        super.setX(this.anchorMode.getContentX(x, OUTLINE_LEFT_PADDING));
        this.shiftedX = this.anchorMode.getContentX(x + this.xOffset, OUTLINE_LEFT_PADDING);
    }

    @Override
    public void setY(int y) {
        this.anchorY = y;
        super.setY(this.anchorMode.getContentY(y, OUTLINE_TOP_PADDING));
        this.shiftedY = this.anchorMode.getContentY(y + this.yOffset, OUTLINE_TOP_PADDING);
    }

    @Override
    public void setXOffset(int x) {
        this.xOffset = x;
        this.shiftedX = this.anchorMode.getContentX(this.anchorX + x, OUTLINE_LEFT_PADDING);
    }

    @Override
    public void setYOffset(int y) {
        this.yOffset = y;
        this.shiftedY = this.anchorMode.getContentY(this.anchorY + y, OUTLINE_TOP_PADDING);
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public void setVisualWidth(int width) {
        this.setWidth(width - (OUTLINE_LEFT_PADDING << 1));
    }

    @Override
    public void setVisualHeight(int height) {
        this.setHeight(height - (OUTLINE_TOP_PADDING << 1));
    }

    @Override
    public int getVisualHeight() {
        return this.height + (OUTLINE_TOP_PADDING << 1);
    }

    @Override
    public int getVisualWidth() {
        return this.width + (OUTLINE_LEFT_PADDING << 1);
    }

    @Override
    public int getVisualX() {
        return getX() - OUTLINE_LEFT_PADDING;
    }

    @Override
    public int getVisualY() {
        return getY() - OUTLINE_TOP_PADDING;
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            renderOutline(context, x - 1, y - 2, width + 2, height + 2, BORDER_FOCUS_COLOR);
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
        drawText(context, textRenderer, text, x + centerX, y + centerY, 0xFFFFFFFF, true);
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
