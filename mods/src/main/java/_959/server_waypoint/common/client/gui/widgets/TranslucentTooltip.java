//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.layout.VisualBounds;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;

/**
 * The themed box a tooltip is drawn in: the popup fill, a one-pixel border and the primary text color,
 * all resolved each time it is drawn. The text wraps at 170 pixels like vanilla's tooltip, and the box
 * extends 4 pixels past the text block on every side.
 * <p>
 * It is never registered with a screen and takes no input; {@link TooltipLayer} places it and draws it
 * at the end of a frame. Its position is the text block's top-left corner.
 */
public final class TranslucentTooltip extends ShiftableWidget implements Padding {
    private static final int MAX_WIDTH = 170;
    private static final VisualBounds VISUAL_BOUNDS = new VisualBounds(4, 4, 4, 4);

    private final ScalableText text;
    private @Nullable Component message;
    private @Nullable Language wrappedWith;

    public TranslucentTooltip(Font font) {
        super(0, 0, 0, 0);
        this.text = new ScalableText(0, 0, Component.empty(), 1.0F, WidgetThemeVariable.TEXT_PRIMARY, MAX_WIDTH, font);
    }

    /**
     * Replaces the text. It wraps again only when the message differs from the current one or the game's
     * language has changed since the last wrap, as vanilla's tooltip caches its lines.
     */
    public void setMessage(Component message) {
        Language language = Language.getInstance();
        if (!message.equals(this.message) || language != this.wrappedWith) {
            this.text.setText(message);
        }
        this.message = message;
        this.wrappedWith = language;
    }

    /** Whether the wrapped text has no characters, so there is nothing to draw. */
    public boolean isEmpty() {
        return this.text.getTextWidth() == 0;
    }

    @Override
    public int getWidth() {
        return this.text.getTextWidth();
    }

    @Override
    public int getHeight() {
        // ScalableText is 9 pixels per line at scale 1; the text block is one pixel less.
        return this.text.getHeight() - 1;
    }

    @Override
    public int getVisualX() {
        return VISUAL_BOUNDS.x(this.getX());
    }

    @Override
    public int getVisualY() {
        return VISUAL_BOUNDS.y(this.getY());
    }

    @Override
    public int getVisualWidth() {
        return VISUAL_BOUNDS.width(this.getWidth());
    }

    @Override
    public int getVisualHeight() {
        return VISUAL_BOUNDS.height(this.getHeight());
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = this.getVisualX();
        int y = this.getVisualY();
        int width = this.getVisualWidth();
        int height = this.getVisualHeight();
        context.fill(x, y, x + width, y + height, WidgetThemeManager.getColor(WidgetThemeVariable.POPUP_BACKGROUND));
        renderOutline(context, x, y, width, height, WidgetThemeManager.getColor(WidgetThemeVariable.BORDER));
        this.text.setPosition(this.getX(), this.getY());
        this.text.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }
}
