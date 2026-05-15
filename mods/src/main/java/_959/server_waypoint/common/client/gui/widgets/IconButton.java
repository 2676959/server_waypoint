//~ resource_location_import
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IconButton extends ShiftableClickableWidget {
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
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        if (isFocused() || isHovered()) {
            renderOutline(context, x, y, width, height, BORDER_FOCUS_COLOR);
        }
        int bgColor = isHovered() ? BUTTON_BG_HOVER_COLOR : 0;
        context.fill(x, y, x + width, y + height, bgColor);
        texture(context, icon, x, y, 0, 0, width, height, width, height);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
