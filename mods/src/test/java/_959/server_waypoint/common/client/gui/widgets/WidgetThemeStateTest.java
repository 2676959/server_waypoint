package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
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
    void aControlWithoutRestingFillIsClearUntilHoveredOrDisabled() {
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0xFF010203);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0xFF040506);
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xFF070809);

        assertEquals(0, WidgetThemeState.controlBackground(true, false, false));
        assertEquals(0xFF040506, WidgetThemeState.controlBackground(true, true, false));
        assertEquals(0xFF070809, WidgetThemeState.controlBackground(false, false, false));
        assertEquals(0xFF010203, WidgetThemeState.controlBackground(true, false, true));
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
    void iconColorsUpdateIndependentlyOfTextColors() {
        WidgetThemeManager.setTheme(WidgetThemeJson.fromJson("""
                {"formatVersion": 1, "colors": {
                  "icon.primary": "#FF123456", "icon.disabled": "#80456789",
                  "text.primary": "#FFABCDEF", "text.disabled": "#FF444444"
                }}
                """));

        assertEquals(0xFF123456, WidgetThemeState.iconTint(true));
        assertEquals(0x80456789, WidgetThemeState.iconTint(false));
        assertEquals(0xFFABCDEF, WidgetThemeState.text(true));
        assertEquals(0xFF444444, WidgetThemeState.text(false));

        WidgetThemeManager.setTheme(WidgetThemeJson.fromJson("""
                {"formatVersion": 1, "colors": {
                  "icon.primary": "#FF987654", "icon.disabled": "#40345678"
                }}
                """));
        assertEquals(0xFF987654, WidgetThemeState.iconTint(true));
        assertEquals(0x40345678, WidgetThemeState.iconTint(false));
    }

    @Test
    void disabledOverlayPreservesThemeRgbWithTranslucentAlpha() {
        WidgetThemeManager.setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xFF123456);

        assertEquals(0x80123456, WidgetThemeState.disabledOverlay());
    }
}
