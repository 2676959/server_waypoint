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
        return controlBackground(active, hovered, true);
    }

    /**
     * The fill of a control in its current state. A control on a panel that already paints a fill passes
     * {@code fillAtRest} false, so while idle it paints none and the panel shows through; hovering and
     * the disabled look still fill.
     */
    static int controlBackground(boolean active, boolean hovered, boolean fillAtRest) {
        if (!active) {
            return getColor(CONTROL_DISABLED_BACKGROUND);
        }
        if (hovered) {
            return getColor(CONTROL_HOVER_BACKGROUND);
        }
        return fillAtRest ? getColor(CONTROL_BACKGROUND) : 0;
    }

    static int border(boolean active, boolean focused, boolean hovered) {
        return getColor(active && (focused || hovered) ? FOCUS_RING : BORDER);
    }

    static int text(boolean active) {
        return getColor(active ? TEXT_PRIMARY : TEXT_DISABLED);
    }

    /** Active input colors shared by catalog-backed fields; empty text keeps the normal theme. */
    static int matchingInputText(String value, boolean matched, boolean focused) {
        if (value.isEmpty()) {
            return text(true);
        }
        if (!matched) {
            return 0xFFFF5555;
        }
        return focused ? 0xFFFFFF55 : text(true);
    }

    static int textOnAccent(boolean active) {
        return getColor(active ? TEXT_ON_ACCENT : TEXT_DISABLED);
    }

    /** What an icon's pixels are multiplied by: white keeps an active icon's colors. */
    static int iconTint(boolean active) {
        return active ? 0xFFFFFFFF : getColor(TEXT_DISABLED);
    }

    static int disabledOverlay() {
        return (getColor(CONTROL_DISABLED_BACKGROUND) & 0x00FFFFFF) | 0x80000000;
    }
}
