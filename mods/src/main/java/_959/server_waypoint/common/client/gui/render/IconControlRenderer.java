//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.common.client.gui.widgets.ShiftableClickableWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeColors.getIconColor;

/** Shared rendering for icon dropdowns and icon toggles placed on a panel. */
public final class IconControlRenderer {
    private static final int CONTROL_ICON_PADDING = 2;

    private IconControlRenderer() {
    }

    public static void render(
            GuiGraphicsExtractor context,
            ShiftableClickableWidget widget,
            //$ resource_location_type_swap
            Identifier
            icon,
            boolean selected,
            boolean focusVisible,
            boolean fillAtRest
    ) {
        WidgetThemeVariable fill = resolveFill(widget.active, selected, widget.isHovered(), fillAtRest);
        int borderColor = WidgetThemeManager.getColor(resolveBorder(
                widget.active,
                widget.isFocused(),
                widget.isHovered(),
                focusVisible
        ));
        int x = widget.getX();
        int y = widget.getY();
        if (fill != null) {
            context.fill(x, y, x + widget.getWidth(), y + widget.getHeight(), WidgetThemeManager.getColor(fill));
        }
        renderOutline(context, x, y, widget.getWidth(), widget.getHeight(), borderColor);
        int iconWidth = Math.max(0, widget.getWidth() - CONTROL_ICON_PADDING * 2);
        int iconHeight = Math.max(0, widget.getHeight() - CONTROL_ICON_PADDING * 2);
        if (iconWidth > 0 && iconHeight > 0) {
            texture(
                    context,
                    icon,
                    x + CONTROL_ICON_PADDING,
                    y + CONTROL_ICON_PADDING,
                    0,
                    0,
                    iconWidth,
                    iconHeight,
                    iconWidth,
                    iconHeight,
                    getIconColor(widget.active)
            );
        }
    }

    /**
     * The fill behind an icon control, or null for none. Controls on a panel pass {@code fillAtRest} false:
     * the panel already paints a translucent fill, so a second one at rest would stack on it. A popup row
     * floats over other widgets and keeps its fill. Disabled panel controls dim their icon and keep
     * the panel visible instead of adding a disabled fill.
     */
    public static @Nullable WidgetThemeVariable resolveFill(
            boolean active,
            boolean selected,
            boolean hovered,
            boolean fillAtRest
    ) {
        if (!active) {
            return fillAtRest ? WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND : null;
        }
        if (selected) {
            return WidgetThemeVariable.SELECTION_BACKGROUND;
        }
        if (hovered) {
            return WidgetThemeVariable.CONTROL_HOVER_BACKGROUND;
        }
        return fillAtRest ? WidgetThemeVariable.CONTROL_BACKGROUND : null;
    }

    public static WidgetThemeVariable resolveBorder(
            boolean active,
            boolean focused,
            boolean hovered,
            boolean focusVisible
    ) {
        return active && (hovered || (focused && focusVisible))
                ? WidgetThemeVariable.FOCUS_RING
                : WidgetThemeVariable.BORDER;
    }
}
