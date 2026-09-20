package _959.server_waypoint.common.client.gui.render;

import java.util.Locale;

/** The built-in presets and the user's independently retained custom palette. */
public enum WidgetThemeSelection {
    CUSTOM(null),
    TRANSLUCENT_DARK(WidgetThemes.TRANSLUCENT_DARK),
    MODERN_DARK(WidgetThemes.MODERN_DARK),
    HIGH_CONTRAST(WidgetThemes.HIGH_CONTRAST);

    private final WidgetTheme preset;

    WidgetThemeSelection(WidgetTheme preset) {
        this.preset = preset;
    }

    public String getId() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public WidgetTheme resolve(WidgetTheme customTheme) {
        return this == CUSTOM ? customTheme : this.preset;
    }
}
