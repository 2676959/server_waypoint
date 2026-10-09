//~ resource_location_import
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ButtonClickCallback;
import _959.server_waypoint.common.client.gui.layout.Expandable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.texture;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IconButton extends ShiftableButtonWidget implements Expandable {
    private static final int ICON_PADDING = 2;
    private final
    //$ resource_location_type_swap
    Identifier
    icon;
    private final ButtonClickCallback callback;
    private boolean fillsAtRest = true;
    private boolean drawsBackground = true;
    private int iconPadding = ICON_PADDING;
    private IconRegion iconRegion;

    public IconButton(int x, int y, int width, int height, Component message,
    //$ resource_location_type_swap
    Identifier
    icon, ButtonClickCallback callback) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.callback = callback;
    }

    @Override
    protected void onPress() {
        this.callback.onClick();
    }

    /**
     * For a button on a panel that already paints a fill: it paints none of its own while idle, so the
     * panel shows through, and still fills when hovered and active.
     */
    public IconButton withoutRestingFill() {
        this.fillsAtRest = false;
        return this;
    }

    /** Removes the background fill in every state, including hover and disabled. */
    public IconButton withoutBackground() {
        this.drawsBackground = false;
        return this;
    }

    /** Sets the minimum inset around the icon, without changing the button's hitbox. */
    public IconButton withIconPadding(int padding) {
        if (padding < 0) {
            throw new IllegalArgumentException("Icon padding must be non-negative");
        }
        this.iconPadding = padding;
        return this;
    }

    /** Fits a source region within the inset, preserving its proportions and centering it. */
    public IconButton withIconRegion(int x, int y, int width, int height, int textureWidth, int textureHeight) {
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > textureWidth || y + height > textureHeight) {
            throw new IllegalArgumentException("Icon region must fit inside the texture");
        }
        this.iconRegion = new IconRegion(x, y, width, height, textureWidth, textureHeight);
        return this;
    }

    /** The fill painted behind the icon, for the button's current state. */
    protected int surfaceColor() {
        return this.drawsBackground
                ? WidgetThemeState.controlBackground(true, this.active && isHovered(), this.fillsAtRest)
                : 0;
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
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (this.drawsBackground) {
            context.fill(x, y, x + width, y + height, this.surfaceColor());
        }
        renderOutline(context, x, y, width, height, WidgetThemeState.border(this.active, isFocused(), isHovered()));
        int iconWidth = Math.max(0, width - this.iconPadding * 2);
        int iconHeight = Math.max(0, height - this.iconPadding * 2);
        if (iconWidth > 0 && iconHeight > 0) {
            IconRegion region = this.iconRegion;
            if (region != null) {
                double scale = Math.min((double) iconWidth / region.width(), (double) iconHeight / region.height());
                iconWidth = Math.max(1, (int) Math.round(region.width() * scale));
                iconHeight = Math.max(1, (int) Math.round(region.height() * scale));
            }
            // An inactive icon takes the disabled text color, so it reads as unavailable.
            texture(
                    context,
                    icon,
                    x + (width - iconWidth) / 2,
                    y + (height - iconHeight) / 2,
                    region == null ? 0 : region.x(),
                    region == null ? 0 : region.y(),
                    iconWidth,
                    iconHeight,
                    region == null ? iconWidth : region.width(),
                    region == null ? iconHeight : region.height(),
                    region == null ? iconWidth : region.textureWidth(),
                    region == null ? iconHeight : region.textureHeight(),
                    WidgetThemeState.iconTint(this.active)
            );
        }
        this.scheduleTooltip(mouseX, mouseY);
    }

    private record IconRegion(int x, int y, int width, int height, int textureWidth, int textureHeight) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.narrateTooltip(builder);
    }
}
