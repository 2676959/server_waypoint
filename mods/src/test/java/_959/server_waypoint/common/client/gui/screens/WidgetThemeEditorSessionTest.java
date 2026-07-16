package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetTheme;
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
    void resetPreviewsDefaultsAndCancelRestoresTheOpeningTheme() {
        WidgetTheme original = WidgetThemes.MODERN_DARK.withColor(
                WidgetThemeVariable.SCREEN_BACKGROUND,
                0xFF010203
        );
        WidgetThemeManager.setTheme(original);
        WidgetThemeEditorSession session = new WidgetThemeEditorSession(
                original,
                this.tempDirectory.resolve("widget-theme.json")
        );

        session.reset();
        assertEquals(WidgetThemes.DEFAULT, WidgetThemeManager.getTheme());

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
}
