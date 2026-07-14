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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeTranslationTest {
    @Test
    void everyThemeVariableIsTranslatedInSupportedLanguages() throws IOException {
        this.assertEveryVariableTranslated("en_us");
        this.assertEveryVariableTranslated("zh_cn");
    }

    private void assertEveryVariableTranslated(String language) throws IOException {
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
        }
    }
}
