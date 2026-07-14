package _959.server_waypoint.common.client.gui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeTest {
    @Test
    void modernDarkThemeDefinesEveryVariable() {
        WidgetTheme theme = WidgetTheme.modernDark();

        assertEquals(WidgetThemeVariable.values().length, theme.getColors().size());
        assertEquals(0xFFF8FAFC, theme.getColor(WidgetThemeVariable.TEXT_PRIMARY));
        assertEquals(0xE60F172A, theme.getColor(WidgetThemeVariable.SCREEN_BACKGROUND));
        assertEquals(0xFF2563EB, theme.getColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND));
        assertEquals(0xFF60A5FA, theme.getColor(WidgetThemeVariable.FOCUS_RING));
    }

    @Test
    void customColorDoesNotMutateBaseTheme() {
        WidgetTheme baseTheme = WidgetTheme.modernDark();
        WidgetTheme customTheme = baseTheme.withColor(WidgetThemeVariable.ACCENT, 0xFF123456);

        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.ACCENT),
                baseTheme.getColor(WidgetThemeVariable.ACCENT));
        assertEquals(0xFF123456, customTheme.getColor(WidgetThemeVariable.ACCENT));
        assertNotEquals(baseTheme, customTheme);
    }

    @Test
    void builderCanOverrideSeveralSemanticVariables() {
        WidgetTheme theme = WidgetTheme.builder(WidgetThemes.MODERN_DARK)
                .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFF101010)
                .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xEE202020)
                .build();

        assertEquals(0xFF101010, theme.getColor(WidgetThemeVariable.TEXT_PRIMARY));
        assertEquals(0xEE202020, theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND));
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.BORDER),
                theme.getColor(WidgetThemeVariable.BORDER));
    }

    @Test
    void incompleteThemesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WidgetTheme.builder()
                .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFFFFFFF)
                .build());
    }

    @Test
    void accentSurfaceTextMeetsNormalTextContrast() {
        WidgetTheme theme = WidgetThemes.MODERN_DARK;
        int foreground = theme.getColor(WidgetThemeVariable.TEXT_ON_ACCENT);

        assertContrastAtLeast(foreground,
                theme.getColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND), 4.5D);
        assertContrastAtLeast(foreground, theme.getColor(WidgetThemeVariable.SUCCESS), 4.5D);
        assertContrastAtLeast(foreground, theme.getColor(WidgetThemeVariable.WARNING), 4.5D);
        assertContrastAtLeast(foreground, theme.getColor(WidgetThemeVariable.DANGER), 4.5D);
    }

    private static void assertContrastAtLeast(int foreground, int background, double minimum) {
        double foregroundLuminance = relativeLuminance(foreground);
        double backgroundLuminance = relativeLuminance(background);
        double lighter = Math.max(foregroundLuminance, backgroundLuminance);
        double darker = Math.min(foregroundLuminance, backgroundLuminance);
        double contrast = (lighter + 0.05D) / (darker + 0.05D);

        assertTrue(contrast >= minimum, () -> "Expected contrast >= " + minimum + ", got " + contrast);
    }

    private static double relativeLuminance(int color) {
        return 0.2126D * linearChannel(color >> 16 & 0xFF)
                + 0.7152D * linearChannel(color >> 8 & 0xFF)
                + 0.0722D * linearChannel(color & 0xFF);
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0D;
        return value <= 0.04045D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }
}
