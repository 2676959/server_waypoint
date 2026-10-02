package _959.server_waypoint.common.client.gui.render;

/**
 * Built-in glass themes with translucent surfaces to keep the world visible.
 */
public final class WidgetThemes {
    /**
     * Blue-gray glass with cyan focus highlights and dark teal accent fills for light text.
     */
    public static final WidgetTheme MODERN_DARK = WidgetTheme.builder()
            .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFE8F0F7)
            .setColor(WidgetThemeVariable.TEXT_MUTED, 0xFFBFCCD8)
            .setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFF95A5B5)
            .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFFB8C7D4)
            .setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFFF8FCFF)
            .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xA60B1016)
            .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xB31A232E)
            .setColor(WidgetThemeVariable.POPUP_BACKGROUND, 0xFA202C38)
            .setColor(WidgetThemeVariable.DIALOG_BACKGROUND, 0xFC111922)
            .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0xD924303D)
            .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0xE6324353)
            .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xB31D2732)
            .setColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND, 0xE61F4C60)
            .setColor(WidgetThemeVariable.BORDER, 0xFF90A5B5)
            .setColor(WidgetThemeVariable.FOCUS_RING, 0xFF5BC3DF)
            .setColor(WidgetThemeVariable.ACCENT, 0xFF20526A)
            .setColor(WidgetThemeVariable.ACCENT_HOVER, 0xFF28627A)
            .setColor(WidgetThemeVariable.SELECTION_BACKGROUND, 0xE6224E63)
            .setColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND, 0x99384F61)
            .setColor(WidgetThemeVariable.SCROLLBAR_TRACK, 0xB31A242F)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB, 0xFF91AABD)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE, 0xFF5BC3DF)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED, 0xFF566878)
            .setColor(WidgetThemeVariable.SLIDER_THUMB_DISABLED, 0xFF687F91)
            .setColor(WidgetThemeVariable.SUCCESS, 0xFF58D39B)
            .setColor(WidgetThemeVariable.WARNING, 0xFFF0C36A)
            .setColor(WidgetThemeVariable.DANGER, 0xFFF59AA4)
            .setColor(WidgetThemeVariable.SUCCESS_BACKGROUND, 0xE6244737)
            .setColor(WidgetThemeVariable.WARNING_BACKGROUND, 0xE6514129)
            .setColor(WidgetThemeVariable.DANGER_BACKGROUND, 0xE6542E3A)
            .build();

    /**
     * Neutral glass with stable control contrast in bright and dark worlds.
     * Floating surfaces are nearly opaque to suppress text showing through from below.
     */
    public static final WidgetTheme TRANSLUCENT_DARK = WidgetTheme.builder()
            .setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFFE8E8E8)
            .setColor(WidgetThemeVariable.TEXT_MUTED, 0xFFC4C4C4)
            .setColor(WidgetThemeVariable.TEXT_DISABLED, 0xFF999999)
            .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFFBDBDBD)
            .setColor(WidgetThemeVariable.TEXT_ON_ACCENT, 0xFFF5F5F5)
            .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xA60F0F0F)
            .setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xB31C1C1C)
            .setColor(WidgetThemeVariable.POPUP_BACKGROUND, 0xFA202020)
            .setColor(WidgetThemeVariable.DIALOG_BACKGROUND, 0xFC181818)
            .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0xD9262626)
            .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0xE6383838)
            .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xB3202020)
            .setColor(WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND, 0xE6444444)
            .setColor(WidgetThemeVariable.BORDER, 0xFFA0A0A0)
            .setColor(WidgetThemeVariable.FOCUS_RING, 0xFFE0E0E0)
            .setColor(WidgetThemeVariable.ACCENT, 0xFF484848)
            .setColor(WidgetThemeVariable.ACCENT_HOVER, 0xFF606060)
            .setColor(WidgetThemeVariable.SELECTION_BACKGROUND, 0xE64C4C4C)
            .setColor(WidgetThemeVariable.ROW_HOVER_BACKGROUND, 0x99484848)
            .setColor(WidgetThemeVariable.SCROLLBAR_TRACK, 0xB3181818)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB, 0xFFA0A0A0)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE, 0xFFD0D0D0)
            .setColor(WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED, 0xFF606060)
            .setColor(WidgetThemeVariable.SLIDER_THUMB_DISABLED, 0xFF737373)
            .setColor(WidgetThemeVariable.SUCCESS, 0xFFB8D8BF)
            .setColor(WidgetThemeVariable.WARNING, 0xFFE0C68C)
            .setColor(WidgetThemeVariable.DANGER, 0xFFE6B6B6)
            .setColor(WidgetThemeVariable.SUCCESS_BACKGROUND, 0xE634473A)
            .setColor(WidgetThemeVariable.WARNING_BACKGROUND, 0xE64D4433)
            .setColor(WidgetThemeVariable.DANGER_BACKGROUND, 0xE64A3638)
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
