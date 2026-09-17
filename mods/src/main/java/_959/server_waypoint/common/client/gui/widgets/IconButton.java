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

public class IconButton extends ShiftableClickableWidget implements Expandable {
    private static final int ICON_PADDING = 2;
    private final
    //$ resource_location_type_swap
    Identifier
    icon;
    private final ButtonClickCallback callback;

    public IconButton(int x, int y, int width, int height, Component message,
    //$ resource_location_type_swap
    Identifier
    icon, ButtonClickCallback callback) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.callback = callback;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
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
        int bgColor = WidgetThemeState.controlBackground(this.active, isHovered());
        context.fill(x, y, x + width, y + height, bgColor);
        renderOutline(context, x, y, width, height, WidgetThemeState.border(this.active, isFocused(), isHovered()));
        int iconWidth = Math.max(0, width - ICON_PADDING * 2);
        int iconHeight = Math.max(0, height - ICON_PADDING * 2);
        if (iconWidth > 0 && iconHeight > 0) {
            texture(
                    context,
                    icon,
                    x + ICON_PADDING,
                    y + ICON_PADDING,
                    0,
                    0,
                    iconWidth,
                    iconHeight,
                    iconWidth,
                    iconHeight
            );
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
