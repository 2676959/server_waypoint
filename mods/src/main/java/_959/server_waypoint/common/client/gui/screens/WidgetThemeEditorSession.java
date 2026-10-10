package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemeSelection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * Opens a session on {@code settings}, the selected theme and Custom colors read from
     * {@code widget-theme.json}, which every edit, revert and Reset starts from. When their theme isn't
     * {@code originalTheme}, as after the file was edited by hand while the game ran, it previews them at
     * once, so the screen shows the theme it edits from the first frame; {@link #cancel()} still restores
     * {@code originalTheme}.
     *
     * @param originalTheme the theme that is live when the editor opens
     */
    WidgetThemeEditorSession(WidgetTheme originalTheme, Path themePath, WidgetThemeJson.Settings settings) {
        this.originalSettings = Objects.requireNonNull(settings, "settings");
        this.settings = settings;
        this.originalTheme = Objects.requireNonNull(originalTheme, "originalTheme");
        this.themePath = Objects.requireNonNull(themePath, "themePath");
        if (!settings.theme().equals(originalTheme)) {
            WidgetThemeManager.setTheme(settings.theme());
        }
    }

    WidgetTheme getDraftTheme() {
        return this.settings.theme();
    }

    /**
     * Sets one color of the draft and previews it. Editing a key while a built-in theme is selected copies
     * that theme into the Custom colors with the edit. Setting the color the draft already has changes nothing,
     * so committing an unchanged value never turns a built-in theme into Custom.
     *
     * @return the built-in theme that was selected before the edit when the Custom colors the copy replaced
     *         differ from it in any key; empty otherwise
     */
    Optional<WidgetThemeSelection> setColor(WidgetThemeVariable variable, int color) {
        this.ensureOpen();
        WidgetTheme draft = this.getDraftTheme();
        if (draft.getColor(variable) == color) {
            return Optional.empty();
        }
        WidgetThemeSelection previous = this.settings.selection();
        boolean replacesDifferingCustom = previous != WidgetThemeSelection.CUSTOM
                && !draft.equals(this.settings.customTheme());
        this.settings = new WidgetThemeJson.Settings(WidgetThemeSelection.CUSTOM, draft.withColor(variable, color));
        WidgetThemeManager.setTheme(this.getDraftTheme());
        return replacesDifferingCustom ? Optional.of(previous) : Optional.empty();
    }

    /**
     * Puts one key back to its color in the theme in effect when the editor opened.
     *
     * @return what {@link #setColor} returns for that change
     */
    Optional<WidgetThemeSelection> revert(WidgetThemeVariable variable) {
        return this.setColor(variable, this.originalSettings.theme().getColor(variable));
    }

    /**
     * Restores the selection and the Custom colors from when the editor opened, and previews them.
     */
    void revertAll() {
        this.ensureOpen();
        this.settings = this.originalSettings;
        WidgetThemeManager.setTheme(this.getDraftTheme());
    }

    /**
     * Whether the draft's color for the key differs from the theme in effect when the editor opened.
     */
    boolean isChanged(WidgetThemeVariable variable) {
        return this.getDraftTheme().getColor(variable) != this.originalSettings.theme().getColor(variable);
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
