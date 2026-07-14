package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.FOCUS_RING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_ON_ACCENT;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

final class WidgetThemeState {
    private WidgetThemeState() {
    }

    static int controlBackground(boolean active, boolean hovered) {
        if (!active) {
            return getColor(CONTROL_DISABLED_BACKGROUND);
        }
        return getColor(hovered ? CONTROL_HOVER_BACKGROUND : CONTROL_BACKGROUND);
    }

    static int border(boolean active, boolean focused, boolean hovered) {
        return getColor(active && (focused || hovered) ? FOCUS_RING : BORDER);
    }

    static int text(boolean active) {
        return getColor(active ? TEXT_PRIMARY : TEXT_DISABLED);
    }

    static int textOnAccent(boolean active) {
        return getColor(active ? TEXT_ON_ACCENT : TEXT_DISABLED);
    }

    static int disabledOverlay() {
        return (getColor(CONTROL_DISABLED_BACKGROUND) & 0x00FFFFFF) | 0x80000000;
    }
}
