//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ButtonClickCallback;
import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.layout.VisualBounds;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawScaledText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class TranslucentButton extends ShiftableButtonWidget implements Expandable, Padding {
    private static final int DEFAULT_Y_OFFSET = -1;
    static final int OUTLINE_LEFT_PADDING = 1;
    static final int OUTLINE_TOP_PADDING = 2;
    private static final VisualBounds VISUAL_BOUNDS = new VisualBounds(0, 1, 0, -1);
    private static final int FITTED_MIN_WIDTH = 50;
    private static final int FITTED_TEXT_PADDING = 10;
    private static final int FITTED_HEIGHT = 11;

    private final ButtonClickCallback callback;
    private final AnchorMode anchorMode;
    private int anchorX;
    private int anchorY;
    protected Component text;
    protected final Font textRenderer = Minecraft.getInstance().font;
    protected int textWidth;
    private float textScale = 1.0F;

    /** Changes the label scale; the caller owns the button's dimensions. */
    public void setTextScale(float textScale) {
        this.textScale = textScale;
    }

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

    /**
     * A button 11 pixels high and as wide as its label plus 5 pixels on each side, but at least 50,
     * so short labels line up and long translations still fit.
     */
    public static TranslucentButton fitted(Component label, ButtonClickCallback callback) {
        TranslucentButton button = new TranslucentButton(0, 0, 0, FITTED_HEIGHT, label, callback);
        button.setWidth(fittedWidth(button.textWidth));
        return button;
    }

    static int fittedWidth(int textWidth) {
        return Math.max(FITTED_MIN_WIDTH, textWidth + FITTED_TEXT_PADDING);
    }

    public void setText(Component text) {
        this.text = text;
        this.textWidth = textRenderer.width(text);
        this.setMessage(text);
    }

    @Override
    protected void onPress() {
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
        this.setWidth(VISUAL_BOUNDS.contentWidth(width));
    }

    @Override
    public void setVisualHeight(int height) {
        this.setHeight(VISUAL_BOUNDS.contentHeight(height));
    }

    @Override
    public int getVisualHeight() {
        return VISUAL_BOUNDS.height(this.height);
    }

    @Override
    public int getVisualWidth() {
        return VISUAL_BOUNDS.width(this.width);
    }

    @Override
    public int getVisualX() {
        return VISUAL_BOUNDS.x(getX());
    }

    @Override
    public int getVisualY() {
        return VISUAL_BOUNDS.y(getY());
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        int bgColor = WidgetThemeState.controlBackground(this.active, isHovered());
        int fixedY = VISUAL_BOUNDS.y(y);
        context.fill(x, fixedY, x + width, fixedY + height, bgColor);
        renderOutline(context, x, fixedY, width, height, WidgetThemeState.border(this.active, isFocused(), isHovered()));
        int centerX = centered(this.width, Math.round(textWidth * this.textScale));
        int centerY = centered(this.height, Math.round(textRenderer.lineHeight * this.textScale));
        drawScaledText(context, textRenderer, this.text, x + centerX, y + centerY,
                WidgetThemeState.text(this.active), this.textScale);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
