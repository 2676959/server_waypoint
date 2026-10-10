package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeSelection;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeEditorSessionTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void colorChangesUpdateTheDraftAndLivePreview() {
        WidgetTheme original = WidgetThemes.MODERN_DARK;
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(
                original,
                this.tempDirectory.resolve("widget-theme.json")
        );

        session.setColor(WidgetThemeVariable.ACCENT, 0x7F123456);

        assertEquals(0x7F123456, session.getDraftTheme().getColor(WidgetThemeVariable.ACCENT));
        assertEquals(session.getDraftTheme(), WidgetThemeManager.getTheme());
        assertTrue(session.isDirty());
    }

    @Test
    void cancelRestoresTheOpeningThemeAndIsIdempotent() {
        WidgetTheme original = WidgetThemes.MODERN_DARK.withColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xFF010203);
        WidgetThemeManager.setTheme(original);
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(
                original, this.tempDirectory.resolve("widget-theme.json"));
        session.select(WidgetThemeSelection.HIGH_CONTRAST);
        session.cancel();
        session.cancel();
        assertEquals(original, WidgetThemeManager.getTheme());
    }

    @Test
    void savePersistsTheDraftAndMakesLaterCancelANoOp() throws IOException {
        Path path = this.tempDirectory.resolve("themes/widget-theme.json");
        WidgetTheme original = WidgetThemes.MODERN_DARK;
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(original, path);
        session.setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xCC112233);

        session.save();
        session.cancel();

        assertEquals(session.getDraftTheme(), WidgetThemeJson.load(path));
        assertEquals(session.getDraftTheme(), WidgetThemeManager.getTheme());
        assertThrows(IllegalStateException.class,
                () -> session.setColor(WidgetThemeVariable.ACCENT, 0xFF000000));
    }

    @Test
    void presetSwitchingRetainsCustomEditsAcrossSaveAndReopen() throws IOException {
        Path path = this.tempDirectory.resolve("widget-theme.json");
        WidgetTheme original = WidgetThemes.DEFAULT;
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(original, path);
        session.setColor(WidgetThemeVariable.ACCENT, 0x7F123456);
        WidgetTheme custom = session.getDraftTheme();
        session.select(WidgetThemeSelection.MODERN_DARK);
        assertEquals(WidgetThemes.MODERN_DARK, WidgetThemeManager.getTheme());
        session.select(WidgetThemeSelection.HIGH_CONTRAST);
        session.select(WidgetThemeSelection.CUSTOM);
        assertEquals(custom, session.getDraftTheme());
        session.select(WidgetThemeSelection.HIGH_CONTRAST);
        session.save();
        session.cancel();
        assertEquals(WidgetThemes.HIGH_CONTRAST, WidgetThemeJson.loadAndApply(path));

        WidgetThemeEditorSession reopened = new WidgetThemeEditorSession(
                WidgetThemeManager.getTheme(), path, WidgetThemeJson.loadSettings(path));
        assertEquals(WidgetThemeSelection.HIGH_CONTRAST, reopened.getSelection());
        assertFalse(reopened.isDirty());
        reopened.select(WidgetThemeSelection.CUSTOM);
        assertEquals(custom, reopened.getDraftTheme());
        reopened.cancel();
        assertEquals(WidgetThemes.HIGH_CONTRAST, WidgetThemeManager.getTheme());
        assertEquals(WidgetThemeSelection.HIGH_CONTRAST, WidgetThemeJson.loadSettings(path).selection());
    }

    @Test
    void classicPresetPreviewsAtOnceAndSurvivesSaveAndReload() throws IOException {
        Path path = this.tempDirectory.resolve("widget-theme.json");
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(WidgetThemes.DEFAULT, path);

        session.select(WidgetThemeSelection.CLASSIC);
        assertEquals(WidgetThemes.CLASSIC, WidgetThemeManager.getTheme());
        session.save();
        session.cancel();

        assertEquals(WidgetThemeSelection.CLASSIC, WidgetThemeJson.loadSettings(path).selection());
        assertEquals(WidgetThemes.CLASSIC, WidgetThemeJson.loadAndApply(path));
    }

    @Test
    void editingPresetCreatesCustomPaletteAndSelectingAnotherThemeKeepsIt() {
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(
                WidgetThemes.DEFAULT, this.tempDirectory.resolve("widget-theme.json"));
        session.select(WidgetThemeSelection.MODERN_DARK);
        session.setColor(WidgetThemeVariable.ACCENT, 0x7F123456);
        WidgetTheme custom = WidgetThemes.MODERN_DARK.withColor(WidgetThemeVariable.ACCENT, 0x7F123456);
        assertEquals(WidgetThemeSelection.CUSTOM, session.getSelection());
        assertEquals(custom, session.getDraftTheme());
        session.select(WidgetThemeSelection.TRANSLUCENT_DARK);
        assertEquals(WidgetThemes.TRANSLUCENT_DARK, session.getDraftTheme());
        session.select(WidgetThemeSelection.CUSTOM);
        assertEquals(custom, session.getDraftTheme());
    }

    @Test
    void failedSaveLeavesTheSessionCancellable() throws IOException {
        Path directoryAsFile = this.tempDirectory.resolve("not-a-file");
        Files.createDirectory(directoryAsFile);
        WidgetTheme original = WidgetThemes.MODERN_DARK;
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(original, directoryAsFile);
        session.setColor(WidgetThemeVariable.ACCENT, 0xFFABCDEF);

        assertThrows(IOException.class, session::save);
        assertTrue(session.isDirty());

        session.cancel();
        assertEquals(original, WidgetThemeManager.getTheme());
        assertFalse(Files.isRegularFile(directoryAsFile));
    }

    @Test
    void aFailedSaveLeavesTheSessionOpen() throws IOException {
        Path blocker = this.tempDirectory.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(
                WidgetThemes.MODERN_DARK, blocker.resolve("widget-theme.json"));
        session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456);
        assertThrows(IOException.class, session::save);
        session.setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xCC112233);
        assertTrue(session.isDirty());
    }

    @Test
    void settingTheColorTheDraftAlreadyHasChangesNothing() {
        WidgetThemeEditorSession session = session(WidgetThemeSelection.MODERN_DARK, WidgetThemes.TRANSLUCENT_DARK);
        int accent = WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.ACCENT);
        assertEquals(Optional.empty(), session.setColor(WidgetThemeVariable.ACCENT, accent));
        assertEquals(WidgetThemeSelection.MODERN_DARK, session.getSelection());
        assertFalse(session.isDirty());
    }

    @Test
    void editingABuiltInThemeReportsItWhenTheCustomColorsDiffer() {
        WidgetThemeEditorSession session = session(WidgetThemeSelection.MODERN_DARK, WidgetThemes.TRANSLUCENT_DARK);
        assertEquals(Optional.of(WidgetThemeSelection.MODERN_DARK),
                session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456));
        assertEquals(WidgetThemeSelection.CUSTOM, session.getSelection());
        assertEquals(0xFF123456, session.getDraftTheme().getColor(WidgetThemeVariable.ACCENT));
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.PANEL_BACKGROUND),
                session.getDraftTheme().getColor(WidgetThemeVariable.PANEL_BACKGROUND));
    }

    @Test
    void editingABuiltInThemeWhoseColorsMatchCustomReportsNothing() {
        WidgetThemeEditorSession session = session(WidgetThemeSelection.MODERN_DARK, WidgetThemes.MODERN_DARK);
        assertEquals(Optional.empty(), session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456));
    }

    @Test
    void editingCustomReportsNothing() {
        WidgetThemeEditorSession session = session(WidgetThemeSelection.CUSTOM, WidgetThemes.MODERN_DARK);
        assertEquals(Optional.empty(), session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456));
    }

    @Test
    void revertRestoresOneKeyFromTheOpeningTheme() {
        WidgetThemeEditorSession session = session(WidgetThemeSelection.CUSTOM, WidgetThemes.MODERN_DARK);
        session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456);
        session.setColor(WidgetThemeVariable.PANEL_BACKGROUND, 0xCC112233);
        session.revert(WidgetThemeVariable.ACCENT);
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.ACCENT),
                session.getDraftTheme().getColor(WidgetThemeVariable.ACCENT));
        assertFalse(session.isChanged(WidgetThemeVariable.ACCENT));
        assertTrue(session.isChanged(WidgetThemeVariable.PANEL_BACKGROUND));
    }

    @Test
    void revertAllRestoresTheSelectionAndTheCustomColors() {
        WidgetThemeEditorSession session = session(WidgetThemeSelection.CUSTOM, WidgetThemes.MODERN_DARK);
        session.select(WidgetThemeSelection.HIGH_CONTRAST);
        session.setColor(WidgetThemeVariable.ACCENT, 0xFF123456);
        session.revertAll();
        assertEquals(WidgetThemeSelection.CUSTOM, session.getSelection());
        assertEquals(WidgetThemes.MODERN_DARK, session.getDraftTheme());
        assertEquals(WidgetThemes.MODERN_DARK, WidgetThemeManager.getTheme());
        assertFalse(session.isDirty());
    }

    private WidgetThemeEditorSession session(WidgetThemeSelection selection, WidgetTheme custom) {
        WidgetThemeJson.Settings settings = new WidgetThemeJson.Settings(selection, custom);
        return new WidgetThemeEditorSession(
                settings.theme(), this.tempDirectory.resolve("widget-theme.json"), settings);
    }
}
