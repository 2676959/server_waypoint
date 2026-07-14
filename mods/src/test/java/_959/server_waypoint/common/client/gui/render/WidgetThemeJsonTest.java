package _959.server_waypoint.common.client.gui.render;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeJsonTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void themeRoundTripsThroughJson() {
        WidgetTheme theme = WidgetTheme.builder(WidgetThemes.MODERN_DARK)
                .setColor(WidgetThemeVariable.ACCENT, 0x7F123456)
                .setColor(WidgetThemeVariable.SCREEN_BACKGROUND, 0xCC010203)
                .build();

        String json = WidgetThemeJson.toJson(theme);

        assertTrue(json.contains("\"formatVersion\": 1"));
        assertTrue(json.contains("\"accent.default\": \"#7F123456\""));
        assertEquals(theme, WidgetThemeJson.fromJson(json));
    }

    @Test
    void omittedAndUnknownVariablesRemainForwardCompatible() {
        String json = """
                {
                  "formatVersion": 1,
                  "colors": {
                    "accent.default": "#FF123456",
                    "futureVariable": "#FFFFFFFF"
                  }
                }
                """;

        WidgetTheme theme = WidgetThemeJson.fromJson(json);

        assertEquals(0xFF123456, theme.getColor(WidgetThemeVariable.ACCENT));
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.TEXT_PRIMARY),
                theme.getColor(WidgetThemeVariable.TEXT_PRIMARY));
    }

    @Test
    void phaseOneStatusColorsRemainBackwardCompatible() {
        String json = """
                {
                  "formatVersion": 1,
                  "colors": {
                    "status.success": "#FF010203",
                    "status.warning": "#FF040506",
                    "status.danger": "#FF070809"
                  }
                }
                """;

        WidgetTheme theme = WidgetThemeJson.fromJson(json);

        assertEquals(0xFF010203, theme.getColor(WidgetThemeVariable.SUCCESS));
        assertEquals(0xFF040506, theme.getColor(WidgetThemeVariable.WARNING));
        assertEquals(0xFF070809, theme.getColor(WidgetThemeVariable.DANGER));
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.SUCCESS_BACKGROUND),
                theme.getColor(WidgetThemeVariable.SUCCESS_BACKGROUND));
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.WARNING_BACKGROUND),
                theme.getColor(WidgetThemeVariable.WARNING_BACKGROUND));
        assertEquals(WidgetThemes.MODERN_DARK.getColor(WidgetThemeVariable.DANGER_BACKGROUND),
                theme.getColor(WidgetThemeVariable.DANGER_BACKGROUND));
    }

    @Test
    void rejectsInvalidColorSyntax() {
        String json = """
                {
                  "formatVersion": 1,
                  "colors": {
                    "accent.default": "123456"
                  }
                }
                """;

        assertThrows(JsonParseException.class, () -> WidgetThemeJson.fromJson(json));
    }

    @Test
    void rejectsUnsupportedFormatVersions() {
        String json = """
                {
                  "formatVersion": 2,
                  "colors": {}
                }
                """;

        assertThrows(JsonParseException.class, () -> WidgetThemeJson.fromJson(json));
    }

    @Test
    void rejectsFractionalFormatVersions() {
        String json = """
                {
                  "formatVersion": 1.5,
                  "colors": {}
                }
                """;

        assertThrows(JsonParseException.class, () -> WidgetThemeJson.fromJson(json));
    }

    @Test
    void acceptsOpaqueRgbColors() {
        String json = """
                {
                  "formatVersion": 1,
                  "colors": {
                    "accent.default": "#123456"
                  }
                }
                """;

        WidgetTheme theme = WidgetThemeJson.fromJson(json);

        assertEquals(0xFF123456, theme.getColor(WidgetThemeVariable.ACCENT));
    }

    @Test
    void savesLoadsAndAppliesThemeFiles() throws IOException {
        Path themeFile = this.tempDirectory.resolve("themes/custom.json");
        WidgetTheme theme = WidgetTheme.builder(WidgetThemes.MODERN_DARK)
                .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0xFF010203)
                .build();

        WidgetThemeJson.save(themeFile, theme);
        WidgetTheme loadedTheme = WidgetThemeJson.loadAndApply(themeFile);

        assertEquals(theme, loadedTheme);
        assertEquals(theme, WidgetThemeManager.getTheme());
    }
}
