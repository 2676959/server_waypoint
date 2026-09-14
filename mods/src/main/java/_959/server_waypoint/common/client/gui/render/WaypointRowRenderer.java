//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.*;

/** Presentation shared by local editable rows and remote read-only rows. */
public final class WaypointRowRenderer {
    private WaypointRowRenderer() { }

    public static void background(GuiGraphicsExtractor context, int y, int width, int height,
                                  int rgb, boolean hovered, boolean selected) {
        context.fill(0, y, width, y + height, hovered ? 0x60000000 | rgb
                : selected ? getColor(SELECTION_BACKGROUND) : 0x10000000 | rgb);
        if (hovered || selected) renderOutline(context, 0, y, width, height,
                hovered ? 0xFF000000 | rgb : getColor(FOCUS_RING));
    }

    public static int initials(GuiGraphicsExtractor context, Font font, String initials, int x, int y,
                               int backgroundColor, int textColor) {
        int textWidth = font.width(initials);
        int width = Math.max(textWidth + 2, font.lineHeight);
        int textX = (width - Math.max(0, textWidth - 1)) / 2;
        context.fill(x, y, x + width, y + font.lineHeight, backgroundColor);
        drawText(context, font, initials, x + textX, y + 1, textColor, true);
        return width;
    }
}
