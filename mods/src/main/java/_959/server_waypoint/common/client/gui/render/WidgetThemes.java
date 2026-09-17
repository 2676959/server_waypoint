package _959.server_waypoint.common.client.gui.render;

/**
 * Built-in complete widget themes.
 */
public final class WidgetThemes {
    public static final WidgetTheme MODERN_DARK = WidgetTheme.builder()
            .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFE8F0F7)
            .setColor(WidgetThemeVariable.TEXT_MUTED, 0xFFAAB8C5)
            .setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFF7D8B99)
            .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFF9DADBA)
            .setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFFF8FCFF)
            .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xFF0B1016)
            .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xFF151C25)
            .setColor(WidgetThemeVariable.POPUP_BACKGROUND, 0xFF1A232E)
            .setColor(WidgetThemeVariable.DIALOG_BACKGROUND, 0xFF0B1016)
            .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0xFF202A36)
            .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0xFF293746)
            .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xFF181F28)
            .setColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND, 0xFF236B87)
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
            .setColor(WidgetThemeVariable.SUCCESS_BACKGROUND, 0xFF1F6F52)
            .setColor(WidgetThemeVariable.WARNING_BACKGROUND, 0xFF805719)
            .setColor(WidgetThemeVariable.DANGER_BACKGROUND, 0xFF9B3E4B)
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
     * Theme used by initial state, resets, and persistence fallbacks.
     */
    public static final WidgetTheme DEFAULT = TRANSLUCENT_DARK;

    private WidgetThemes() {
    }
}
