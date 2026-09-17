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
        assertEquals(0xFFE8F0F7, theme.getColor(WidgetThemeVariable.TEXT_PRIMARY));
        assertEquals(0xFF0B1016, theme.getColor(WidgetThemeVariable.SCREEN_BACKGROUND));
        assertEquals(0xFF236B87, theme.getColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND));
        assertEquals(0xFF5BC3DF, theme.getColor(WidgetThemeVariable.FOCUS_RING));
    }

    @Test
    void translucentDarkThemeDefinesEveryVariableAndSurfaceDepth() {
        WidgetTheme theme = WidgetThemes.TRANSLUCENT_DARK;

        assertEquals(WidgetThemeVariable.values().length, theme.getColors().size());
        assertEquals(0xFFE8E8E8, theme.getColor(WidgetThemeVariable.TEXT_PRIMARY));
        assertEquals(0x4D000000, theme.getColor(WidgetThemeVariable.SCREEN_BACKGROUND));
        assertEquals(0x59080808, theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND));
        assertEquals(0x73101010, theme.getColor(WidgetThemeVariable.POPUP_BACKGROUND));
        assertEquals(0xB3000000, theme.getColor(WidgetThemeVariable.DIALOG_BACKGROUND));
        assertEquals(0x4D0C0C0C, theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND));
        assertEquals(0x66484848, theme.getColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
        assertEquals(0x33484848, theme.getColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND));
        theme.getColors().forEach((variable, color) -> {
            if (variable != WidgetThemeVariable.SUCCESS_BACKGROUND
                    && variable != WidgetThemeVariable.DANGER_BACKGROUND) {
                assertGrayscale(color);
            }
        });
        assertMutedNonGrayscale(theme.getColor(WidgetThemeVariable.SUCCESS_BACKGROUND));
        assertMutedNonGrayscale(theme.getColor(WidgetThemeVariable.DANGER_BACKGROUND));
        assertTrue(alpha(theme.getColor(WidgetThemeVariable.SCREEN_BACKGROUND))
                < alpha(theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND)));
        assertTrue(alpha(theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND))
                < alpha(theme.getColor(WidgetThemeVariable.POPUP_BACKGROUND)));
    }

    @Test
    void translucentDarkThemeRetainsReadableContrastAcrossLayeredSurfaces() {
        WidgetTheme theme = WidgetThemes.TRANSLUCENT_DARK;
        int panel = theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND);

        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_PRIMARY), panel, 4.5D);
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_PLACEHOLDER),
                theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND),
                panel,
                4.5D
        );
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_ON_ACCENT),
                theme.getColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND),
                panel,
                4.5D
        );
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_ON_ACCENT),
                theme.getColor(WidgetThemeVariable.SUCCESS_BACKGROUND),
                panel,
                4.5D
        );
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_ON_ACCENT),
                theme.getColor(WidgetThemeVariable.DANGER_BACKGROUND),
                panel,
                4.5D
        );
        assertDarkerThan(
                theme.getColor(WidgetThemeVariable.BORDER),
                theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND),
                panel
        );
        assertDarkerThan(
                theme.getColor(WidgetThemeVariable.FOCUS_RING),
                theme.getColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND),
                panel
        );
        assertDarkerThan(
                theme.getColor(WidgetThemeVariable.DIALOG_BACKGROUND),
                theme.getColor(WidgetThemeVariable.POPUP_BACKGROUND),
                panel
        );
        assertDarkerThan(
                theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND),
                theme.getColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND),
                panel
        );
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.SCROLLBAR_THUMB),
                theme.getColor(WidgetThemeVariable.SCROLLBAR_TRACK),
                panel,
                3.0D
        );
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
        assertContrastAtLeast(foreground, theme.getColor(WidgetThemeVariable.SUCCESS_BACKGROUND), 4.5D);
        assertContrastAtLeast(foreground, theme.getColor(WidgetThemeVariable.WARNING_BACKGROUND), 4.5D);
        assertContrastAtLeast(foreground, theme.getColor(WidgetThemeVariable.DANGER_BACKGROUND), 4.5D);
    }

    @Test
    void disabledControlTextRemainsReadable() {
        WidgetTheme theme = WidgetThemes.MODERN_DARK;

        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_DISABLED),
                theme.getColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND),
                theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND),
                3.0D
        );
    }

    @Test
    void placeholderTextRemainsReadableAcrossControlStates() {
        WidgetTheme theme = WidgetThemes.MODERN_DARK;
        int placeholder = theme.getColor(WidgetThemeVariable.TEXT_PLACEHOLDER);
        int panel = theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND);

        assertContrastAtLeast(placeholder,
                theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND), panel, 4.5D);
        assertContrastAtLeast(placeholder,
                theme.getColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND), panel, 4.5D);
    }

    @Test
    void textHierarchyRemainsReadableOnCoreSurfaces() {
        WidgetTheme theme = WidgetThemes.MODERN_DARK;

        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_PRIMARY),
                theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND),
                4.5D
        );
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_MUTED),
                theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND),
                4.5D
        );
        assertContrastAtLeast(
                theme.getColor(WidgetThemeVariable.TEXT_PRIMARY),
                theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND),
                4.5D
        );
    }

    @Test
    void focusRingRemainsVisibleOnCoreSurfaces() {
        WidgetTheme theme = WidgetThemes.MODERN_DARK;
        int focusRing = theme.getColor(WidgetThemeVariable.FOCUS_RING);

        assertContrastAtLeast(focusRing, theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND), 3.0D);
        assertContrastAtLeast(focusRing, theme.getColor(WidgetThemeVariable.CONTROL_BACKGROUND), 3.0D);
    }

    @Test
    void scrollbarThumbRemainsVisibleAgainstTrack() {
        WidgetTheme theme = WidgetThemes.MODERN_DARK;
        int track = theme.getColor(WidgetThemeVariable.SCROLLBAR_TRACK);
        int panel = theme.getColor(WidgetThemeVariable.PANEL_BACKGROUND);

        assertContrastAtLeast(theme.getColor(WidgetThemeVariable.SCROLLBAR_THUMB), track, panel, 3.0D);
        assertContrastAtLeast(theme.getColor(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE), track, panel, 3.0D);
    }

    private static void assertContrastAtLeast(int foreground, int background, double minimum) {
        assertContrastAtLeast(foreground, background, 0xFF000000, minimum);
    }

    private static void assertContrastAtLeast(int foreground, int background, int parent, double minimum) {
        int opaqueParent = compositeOver(parent, 0xFF000000);
        int composedBackground = compositeOver(background, opaqueParent);
        int composedForeground = compositeOver(foreground, composedBackground);
        double foregroundLuminance = relativeLuminance(composedForeground);
        double backgroundLuminance = relativeLuminance(composedBackground);
        double lighter = Math.max(foregroundLuminance, backgroundLuminance);
        double darker = Math.min(foregroundLuminance, backgroundLuminance);
        double contrast = (lighter + 0.05D) / (darker + 0.05D);

        assertTrue(contrast >= minimum, () -> "Expected contrast >= " + minimum + ", got " + contrast);
    }

    private static void assertDarkerThan(int foreground, int background, int parent) {
        int opaqueParent = compositeOver(parent, 0xFF000000);
        int composedForeground = compositeOver(foreground, opaqueParent);
        int composedBackground = compositeOver(background, opaqueParent);

        assertTrue(relativeLuminance(composedForeground) < relativeLuminance(composedBackground));
    }

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static void assertGrayscale(int color) {
        int red = color >> 16 & 0xFF;
        assertEquals(red, color >> 8 & 0xFF);
        assertEquals(red, color & 0xFF);
    }

    private static void assertMutedNonGrayscale(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));

        assertTrue(maximum > minimum);
        assertTrue((maximum - minimum) * 100 <= maximum * 35);
    }

    private static int compositeOver(int foreground, int background) {
        double alpha = (foreground >>> 24) / 255.0D;
        int red = compositeChannel(foreground >> 16 & 0xFF, background >> 16 & 0xFF, alpha);
        int green = compositeChannel(foreground >> 8 & 0xFF, background >> 8 & 0xFF, alpha);
        int blue = compositeChannel(foreground & 0xFF, background & 0xFF, alpha);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int compositeChannel(int foreground, int background, double alpha) {
        return (int)Math.round(foreground * alpha + background * (1.0D - alpha));
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
