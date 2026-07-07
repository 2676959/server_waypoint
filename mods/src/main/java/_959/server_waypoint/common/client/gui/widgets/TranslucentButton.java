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

public class TranslucentButton extends ShiftableClickableWidget implements Expandable, Padding {
    private static final int DEFAULT_Y_OFFSET = -1;
    static final int OUTLINE_LEFT_PADDING = 1;
    static final int OUTLINE_TOP_PADDING = 2;

    private final ButtonClickCallback callback;
    private final AnchorMode anchorMode;
    private int anchorX;
    private int anchorY;
    protected Component text;
    protected final Font textRenderer = Minecraft.getInstance().font;
    protected int textWidth;

    public TranslucentButton(int x, int y, int width, int height, Component text, ButtonClickCallback callback) {
        this(x, y, width, height, text, callback, AnchorMode.CONTENT);
    }

    public TranslucentButton(int x, int y, int width, int height, Component text, ButtonClickCallback callback, AnchorMode anchorMode) {
        super(
                AnchorMode.normalize(anchorMode).getContentX(x, OUTLINE_LEFT_PADDING),
                AnchorMode.normalize(anchorMode).getContentY(y, OUTLINE_TOP_PADDING),
                width,
                height,
                text
        );
        this.anchorMode = AnchorMode.normalize(anchorMode);
        this.text = text;
        this.callback = callback;
        this.textWidth = textRenderer.width(text);
        this.setX(x);
        this.setY(y);
        if (this.anchorMode == AnchorMode.CONTENT) {
            this.setYOffset(DEFAULT_Y_OFFSET);
        }
    }

    public void setText(Component text) {
        this.text = text;
        this.textWidth = textRenderer.width(text);
        this.setMessage(text);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
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
        int bdColor = isFocused() || isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        renderOutline(context, x - 1, y - 2, width + 2, height + 2, bdColor);
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : BUTTON_BG_COLOR;
        int fixedY = y - 1;
        context.fill(x, fixedY, x + width, fixedY + height, bgColor);
        int centerX = centered(this.width, textWidth);
        int centerY = centered(this.height, textRenderer.lineHeight);
        drawText(context, textRenderer, this.text, x + centerX, y + centerY, 0xFFFFFFFF, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
