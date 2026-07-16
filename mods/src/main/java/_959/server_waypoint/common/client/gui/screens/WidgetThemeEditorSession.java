package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns one theme-editing transaction and its live preview lifecycle.
 */
final class WidgetThemeEditorSession {
    private final WidgetTheme originalTheme;
    private final Path themePath;
    private WidgetTheme draftTheme;
    private boolean closed;

    WidgetThemeEditorSession(WidgetTheme originalTheme, Path themePath) {
        this.originalTheme = Objects.requireNonNull(originalTheme, "originalTheme");
        this.themePath = Objects.requireNonNull(themePath, "themePath");
        this.draftTheme = originalTheme;
    }

    WidgetTheme getDraftTheme() {
        return this.draftTheme;
    }

    void setColor(WidgetThemeVariable variable, int color) {
        this.ensureOpen();
        this.draftTheme = this.draftTheme.withColor(variable, color);
        WidgetThemeManager.setTheme(this.draftTheme);
    }

    void reset() {
        this.ensureOpen();
        this.draftTheme = WidgetThemes.DEFAULT;
        WidgetThemeManager.setTheme(this.draftTheme);
    }

    boolean isDirty() {
        return !this.originalTheme.equals(this.draftTheme);
    }

    void save() throws IOException {
        this.ensureOpen();
        WidgetThemeJson.save(this.themePath, this.draftTheme);
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
