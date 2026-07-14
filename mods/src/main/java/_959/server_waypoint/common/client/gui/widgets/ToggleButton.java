//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ToggleButtonCallback;
import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.layout.VisualBounds;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;

import java.util.Objects;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ToggleButton extends ShiftableClickableWidget implements Expandable, Padding {
    private static final int DEFAULT_Y_OFFSET = -1;
    private static final VisualBounds VISUAL_BOUNDS = new VisualBounds(0, 1, 0, -1);
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
    private final IntSupplier state0Color;
    private final IntSupplier state1Color;

    public ToggleButton(int x, int y, int width, int height, Component state0Text,
                        Component state1Text, int state0color, int state1color,
                        ToggleButtonCallback callback) {
        this(x, y, width, height, state0Text, state1Text, state0color, state1color, callback, AnchorMode.CONTENT);
    }

    public ToggleButton(int x, int y, int width, int height, Component state0Text,
                        Component state1Text, int state0color, int state1color,
                        ToggleButtonCallback callback, AnchorMode anchorMode) {
        this(x, y, width, height, state0Text, state1Text, fixedStateColor(state0color), fixedStateColor(state1color), callback, anchorMode);
    }

    public ToggleButton(int x, int y, int width, int height, Component state0Text,
                        Component state1Text, WidgetThemeVariable state0Color, WidgetThemeVariable state1Color,
                        ToggleButtonCallback callback) {
        this(x, y, width, height, state0Text, state1Text, state0Color, state1Color, callback, AnchorMode.CONTENT);
    }

    public ToggleButton(int x, int y, int width, int height, Component state0Text,
                        Component state1Text, WidgetThemeVariable state0Color, WidgetThemeVariable state1Color,
                        ToggleButtonCallback callback, AnchorMode anchorMode) {
        this(
                x,
                y,
                width,
                height,
                state0Text,
                state1Text,
                WidgetThemeManager.getColorSupplier(state0Color),
                WidgetThemeManager.getColorSupplier(state1Color),
                callback,
                anchorMode
        );
    }

    private ToggleButton(int x, int y, int width, int height, Component state0Text,
                         Component state1Text, IntSupplier state0Color, IntSupplier state1Color,
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
        this.state0Color = Objects.requireNonNull(state0Color, "state0Color");
        this.state1Color = Objects.requireNonNull(state1Color, "state1Color");
        this.callback = callback;
        this.setX(x);
        this.setY(y);
        if (this.anchorMode == AnchorMode.CONTENT) {
            this.setYOffset(DEFAULT_Y_OFFSET);
        }
    }

    private static IntSupplier fixedStateColor(int color) {
        int translucentColor = 0x99000000 | (0x00FFFFFF & color);
        return () -> translucentColor;
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
        int bgColor = this.active
                ? (this.state ? this.state1Color.getAsInt() : this.state0Color.getAsInt())
                : WidgetThemeState.controlBackground(false, false);
        int fixedY = VISUAL_BOUNDS.y(y);
        context.fill(x, fixedY, x + width, fixedY + height, bgColor);
        renderOutline(context, x, fixedY, width, height, WidgetThemeState.border(this.active, isFocused(), isHovered()));
        Component text = this.state ? state1Text : state0Text;
        int textWidth = textRenderer.width(text);
        int centerX = centered(this.width, textWidth);
        int centerY = centered(this.height, textRenderer.lineHeight);
        drawText(context, textRenderer, text, x + centerX, y + centerY, WidgetThemeState.textOnAccent(this.active), true);
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
