package _959.server_waypoint.common.client.gui.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WidgetThemeManagerTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void setColorPublishesACompleteUpdatedTheme() {
        WidgetTheme originalTheme = WidgetThemeManager.getTheme();

        WidgetThemeManager.setColor(WidgetThemeVariable.ACCENT, 0xFFABCDEF);

        assertEquals(0xFFABCDEF, WidgetThemeManager.getColor(WidgetThemeVariable.ACCENT));
        assertEquals(originalTheme.getColor(WidgetThemeVariable.TEXT_PRIMARY),
                WidgetThemeManager.getColor(WidgetThemeVariable.TEXT_PRIMARY));
    }

    @Test
    void setThemePublishesTheProvidedTheme() {
        WidgetTheme theme = WidgetTheme.builder(WidgetThemes.MODERN_DARK)
                .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xCC010203)
                .build();

        WidgetThemeManager.setTheme(theme);

        assertSame(theme, WidgetThemeManager.getTheme());
    }

    @Test
    void resetThemeRestoresTheBuiltInDefault() {
        WidgetThemeManager.setTheme(WidgetThemes.MODERN_DARK);

        WidgetThemeManager.resetTheme();

        assertSame(WidgetThemes.DEFAULT, WidgetThemeManager.getTheme());
        assertSame(WidgetThemes.TRANSLUCENT_DARK, WidgetThemeManager.getTheme());
    }

    @Test
    void updateThemeCanChangeVariablesAtomically() {
        WidgetTheme updatedTheme = WidgetThemeManager.updateTheme(theme -> WidgetTheme.builder(theme)
                .setColor(WidgetThemeVariable.SUCCESS, 0xFF010203)
                .setColor(WidgetThemeVariable.DANGER, 0xFF040506)
                .build());

        assertSame(updatedTheme, WidgetThemeManager.getTheme());
        assertEquals(0xFF010203, WidgetThemeManager.getColor(WidgetThemeVariable.SUCCESS));
        assertEquals(0xFF040506, WidgetThemeManager.getColor(WidgetThemeVariable.DANGER));
    }

    @Test
    void colorSupplierResolvesTheLatestThemeValue() {
        var supplier = WidgetThemeManager.getColorSupplier(WidgetThemeVariable.ACCENT);

        WidgetThemeManager.setColor(WidgetThemeVariable.ACCENT, 0xFF010203);
        assertEquals(0xFF010203, supplier.getAsInt());

        WidgetThemeManager.setColor(WidgetThemeVariable.ACCENT, 0xFF040506);
        assertEquals(0xFF040506, supplier.getAsInt());
    }
}
