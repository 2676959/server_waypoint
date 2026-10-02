package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemeSelection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns one theme-editing transaction and its live preview lifecycle.
 */
final class WidgetThemeEditorSession {
    private final WidgetTheme originalTheme;
    private final Path themePath;
    private final WidgetThemeJson.Settings originalSettings;
    private WidgetThemeJson.Settings settings;
    private boolean closed;

    WidgetThemeEditorSession(WidgetTheme originalTheme, Path themePath) {
        this(originalTheme, themePath, new WidgetThemeJson.Settings(WidgetThemeSelection.CUSTOM, originalTheme));
    }

    WidgetThemeEditorSession(WidgetTheme originalTheme, Path themePath, WidgetThemeJson.Settings settings) {
        this.originalSettings = Objects.requireNonNull(settings, "settings");
        this.settings = settings;
        this.originalTheme = Objects.requireNonNull(originalTheme, "originalTheme");
        this.themePath = Objects.requireNonNull(themePath, "themePath");
    }

    WidgetTheme getDraftTheme() {
        return this.settings.theme();
    }

    void setColor(WidgetThemeVariable variable, int color) {
        this.ensureOpen();
        this.settings = new WidgetThemeJson.Settings(WidgetThemeSelection.CUSTOM,
                this.getDraftTheme().withColor(variable, color));
        WidgetThemeManager.setTheme(this.getDraftTheme());
    }

    void reset() {
        this.select(WidgetThemeSelection.TRANSLUCENT_DARK);
    }

    WidgetThemeSelection getSelection() {
        return this.settings.selection();
    }

    void select(WidgetThemeSelection selection) {
        this.ensureOpen();
        this.settings = new WidgetThemeJson.Settings(selection, this.settings.customTheme());
        WidgetThemeManager.setTheme(this.getDraftTheme());
    }

    boolean isDirty() {
        return !this.originalSettings.equals(this.settings);
    }

    void save() throws IOException {
        this.ensureOpen();
        WidgetThemeJson.save(this.themePath, this.settings);
        this.closed = true;
    }

    void cancel() {
        if (this.closed) {
            return;
        }
        WidgetThemeManager.setTheme(this.originalTheme);
        this.closed = true;
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("Widget theme editor session is already closed");
        }
    }
}
