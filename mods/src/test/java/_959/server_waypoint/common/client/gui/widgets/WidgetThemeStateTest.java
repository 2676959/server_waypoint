package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetThemeStateTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void controlStatesResolveCurrentThemeAtCallTime() {
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0xFF010203);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0xFF040506);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xFF070809);

        assertEquals(0xFF010203, WidgetThemeState.controlBackground(true, false));
        assertEquals(0xFF040506, WidgetThemeState.controlBackground(true, true));
        assertEquals(0xFF070809, WidgetThemeState.controlBackground(false, true));

        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0xFF101112);

        assertEquals(0xFF101112, WidgetThemeState.controlBackground(true, false));
    }

    @Test
    void borderAndTextStatesIgnoreFocusWhenDisabled() {
        WidgetThemeManager.setColor(WidgetThemeVariable.BORDER, 0xFF111111);
        WidgetThemeManager.setColor(WidgetThemeVariable.FOCUS_RING, 0xFF222222);
        WidgetThemeManager.setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFF333333);
        WidgetThemeManager.setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFF444444);
        WidgetThemeManager.setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFF555555);

        assertEquals(0xFF222222, WidgetThemeState.border(true, true, false));
        assertEquals(0xFF111111, WidgetThemeState.border(false, true, true));
        assertEquals(0xFF333333, WidgetThemeState.text(true));
        assertEquals(0xFF444444, WidgetThemeState.text(false));
        assertEquals(0xFF555555, WidgetThemeState.textOnAccent(true));
        assertEquals(0xFF444444, WidgetThemeState.textOnAccent(false));
    }

    @Test
    void disabledOverlayPreservesThemeRgbWithTranslucentAlpha() {
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xFF123456);

        assertEquals(0x80123456, WidgetThemeState.disabledOverlay());
    }
}
