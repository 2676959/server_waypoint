package _959.server_waypoint.common.client.gui.render;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/**
 * Owns the active GUI theme and exposes atomic runtime updates.
 */
public final class WidgetThemeManager {
    private static final AtomicReference<WidgetTheme> ACTIVE_THEME =
            new AtomicReference<>(WidgetThemes.DEFAULT);

    private WidgetThemeManager() {
    }

    public static WidgetTheme getTheme() {
        return ACTIVE_THEME.get();
    }

    public static int getColor(WidgetThemeVariable variable) {
        return getTheme().getColor(variable);
    }

    public static void setTheme(WidgetTheme theme) {
        ACTIVE_THEME.set(Objects.requireNonNull(theme, "theme"));
    }

    public static void setColor(WidgetThemeVariable variable, int color) {
        updateTheme(theme -> theme.withColor(variable, color));
    }

    public static IntSupplier getColorSupplier(WidgetThemeVariable variable) {
        Objects.requireNonNull(variable, "variable");
        return () -> getColor(variable);
    }

    public static WidgetTheme updateTheme(UnaryOperator<WidgetTheme> update) {
        Objects.requireNonNull(update, "update");
        return ACTIVE_THEME.updateAndGet(theme ->
                Objects.requireNonNull(update.apply(theme), "updated theme"));
    }

    public static void resetTheme() {
        setTheme(WidgetThemes.DEFAULT);
    }
}
