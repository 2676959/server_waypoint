package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeTranslationTest {
    private static final List<String> PREVIEW_TRANSLATION_KEYS = List.of(
            "server_waypoint.theme.preview.title",
            "server_waypoint.theme.preview.primary",
            "server_waypoint.theme.preview.muted",
            "server_waypoint.theme.preview.placeholder",
            "server_waypoint.theme.preview.button",
            "server_waypoint.theme.preview.disabled",
            "server_waypoint.theme.preview.normal",
            "server_waypoint.theme.preview.selected",
            "server_waypoint.theme.preview.popup",
            "server_waypoint.theme.preview.dialog",
            "server_waypoint.theme.preview.accent",
            "server_waypoint.theme.preview.hover",
            "server_waypoint.theme.preview.success",
            "server_waypoint.theme.preview.warning",
            "server_waypoint.theme.preview.danger"
    );

    @Test
    void themeEditorIsTranslatedInSupportedLanguages() throws IOException {
        this.assertThemeEditorTranslated("en_us");
        this.assertThemeEditorTranslated("zh_cn");
    }

    private void assertThemeEditorTranslated(String language) throws IOException {
        String resource = "/assets/server_waypoint/lang/" + language + ".json";
        InputStream stream = WidgetThemeTranslationTest.class.getResourceAsStream(resource);
        assertNotNull(stream, () -> "Missing language resource: " + resource);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject translations = JsonParser.parseReader(reader).getAsJsonObject();
            for (WidgetThemeVariable variable : WidgetThemeVariable.values()) {
                String key = "server_waypoint.theme.variable." + variable.getJsonName();
                assertTrue(translations.has(key),
                        () -> "Missing " + language + " translation for " + key);
            }
            for (String key : PREVIEW_TRANSLATION_KEYS) {
                assertTrue(translations.has(key),
                        () -> "Missing " + language + " translation for " + key);
            }
        }
    }
}
