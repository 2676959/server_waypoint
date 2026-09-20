package _959.server_waypoint.common.client.gui.render;

/**
 * Built-in glass themes with translucent surfaces to keep the world visible.
 */
public final class WidgetThemes {
    public static final WidgetTheme MODERN_DARK = WidgetTheme.builder()
            .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFE8F0F7)
            .setColor(WidgetThemeVariable.TEXT_MUTED, 0xFFAAB8C5)
            .setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFF7D8B99)
            .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFF9DADBA)
            .setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFFF8FCFF)
            .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0x4D0B1016)
            .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0x59151C25)
            .setColor(WidgetThemeVariable.POPUP_BACKGROUND, 0x731A232E)
            .setColor(WidgetThemeVariable.DIALOG_BACKGROUND, 0xB30B1016)
            .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0x4D202A36)
            .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0x66293746)
            .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0x26181F28)
            .setColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND, 0x66236B87)
            .setColor(WidgetThemeVariable.BORDER, 0xFF3A4A59)
            .setColor(WidgetThemeVariable.FOCUS_RING, 0xFF5BC3DF)
            .setColor(WidgetThemeVariable.ACCENT, 0xFF5BC3DF)
            .setColor(WidgetThemeVariable.ACCENT_HOVER, 0xFF83D5E9)
            .setColor(WidgetThemeVariable.SELECTION_BACKGROUND, 0x665BC3DF)
            .setColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND, 0x335BC3DF)
            .setColor(WidgetThemeVariable.SCROLLBAR_TRACK, 0x66303D49)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB, 0xFF5E778B)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE, 0xFF7892A4)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED, 0xFF3C4A57)
            .setColor(WidgetThemeVariable.SLIDER_THUMB_DISABLED, 0xFF536A7B)
            .setColor(WidgetThemeVariable.SUCCESS, 0xFF58D39B)
            .setColor(WidgetThemeVariable.WARNING, 0xFFF0C36A)
            .setColor(WidgetThemeVariable.DANGER, 0xFFF27D87)
            .setColor(WidgetThemeVariable.SUCCESS_BACKGROUND, 0x801F6F52)
            .setColor(WidgetThemeVariable.WARNING_BACKGROUND, 0x80805719)
            .setColor(WidgetThemeVariable.DANGER_BACKGROUND, 0x809B3E4B)
            .build();

    /**
     * Neutral glass-like surfaces with increasing opacity from the screen overlay to popups.
     */
    public static final WidgetTheme TRANSLUCENT_DARK = WidgetTheme.builder()
            .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFE8E8E8)
            .setColor(WidgetThemeVariable.TEXT_MUTED, 0xFFB8B8B8)
            .setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFF808080)
            .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFFA8A8A8)
            .setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFFEEEEEE)
            .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0x4D000000)
            .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0x59080808)
            .setColor(WidgetThemeVariable.POPUP_BACKGROUND, 0x73101010)
            .setColor(WidgetThemeVariable.DIALOG_BACKGROUND, 0xB3000000)
            .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0x4D0C0C0C)
            .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0x66484848)
            .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0x26040404)
            .setColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND, 0x66202020)
            .setColor(WidgetThemeVariable.BORDER, 0x80000000)
            .setColor(WidgetThemeVariable.FOCUS_RING, 0xB3000000)
            .setColor(WidgetThemeVariable.ACCENT, 0xFFB0B0B0)
            .setColor(WidgetThemeVariable.ACCENT_HOVER, 0xFFCCCCCC)
            .setColor(WidgetThemeVariable.SELECTION_BACKGROUND, 0x33404040)
            .setColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND, 0x33484848)
            .setColor(WidgetThemeVariable.SCROLLBAR_TRACK, 0x26000000)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB, 0x80B2B2B2)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE, 0x99C0C0C0)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED, 0x33484848)
            .setColor(WidgetThemeVariable.SLIDER_THUMB_DISABLED, 0x4D686868)
            .setColor(WidgetThemeVariable.SUCCESS, 0xFFB8B8B8)
            .setColor(WidgetThemeVariable.WARNING, 0xFFA0A0A0)
            .setColor(WidgetThemeVariable.DANGER, 0xFFC8C8C8)
            .setColor(WidgetThemeVariable.SUCCESS_BACKGROUND, 0x8034473A)
            .setColor(WidgetThemeVariable.WARNING_BACKGROUND, 0x4D121212)
            .setColor(WidgetThemeVariable.DANGER_BACKGROUND, 0x804A3638)
            .build();

    /**
     * Stronger glass tint with bright text, crisp outlines, and a yellow keyboard focus ring.
     */
    public static final WidgetTheme HIGH_CONTRAST = WidgetTheme.builder()
            .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFFFFFFF)
            .setColor(WidgetThemeVariable.TEXT_MUTED, 0xFFE0E0E0)
            .setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFFB0B0B0)
            .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFFD0D0D0)
            .setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFFFFFFFF)
            .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0x4D000000)
            .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0x80080808)
            .setColor(WidgetThemeVariable.POPUP_BACKGROUND, 0x99141414)
            .setColor(WidgetThemeVariable.DIALOG_BACKGROUND, 0xCC000000)
            .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0x66101010)
            .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0x80303030)
            .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0x4D181818)
            .setColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND, 0x9900384D)
            .setColor(WidgetThemeVariable.BORDER, 0xFFFFFFFF)
            .setColor(WidgetThemeVariable.FOCUS_RING, 0xFFFFFF00)
            .setColor(WidgetThemeVariable.ACCENT, 0xFF00FFFF)
            .setColor(WidgetThemeVariable.ACCENT_HOVER, 0xFF99FFFF)
            .setColor(WidgetThemeVariable.SELECTION_BACKGROUND, 0x8000384D)
            .setColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND, 0x66303030)
            .setColor(WidgetThemeVariable.SCROLLBAR_TRACK, 0x4D181818)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB, 0xFFFFFFFF)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE, 0xFFFFFF00)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED, 0xFF909090)
            .setColor(WidgetThemeVariable.SLIDER_THUMB_DISABLED, 0xFF909090)
            .setColor(WidgetThemeVariable.SUCCESS, 0xFF66FF99)
            .setColor(WidgetThemeVariable.WARNING, 0xFFFFFF00)
            .setColor(WidgetThemeVariable.DANGER, 0xFFFF9999)
            .setColor(WidgetThemeVariable.SUCCESS_BACKGROUND, 0x99005020)
            .setColor(WidgetThemeVariable.WARNING_BACKGROUND, 0x99594000)
            .setColor(WidgetThemeVariable.DANGER_BACKGROUND, 0x99780020)
            .build();

    /**
     * Theme used by initial state, resets, and persistence fallbacks.
     */
    public static final WidgetTheme DEFAULT = TRANSLUCENT_DARK;

    private WidgetThemes() {
    }
}
