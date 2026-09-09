package _959.server_waypoint.common.client.gui.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoteGuiTranslationTest {
    @Test void allSixLocalesCoverRemoteGuiKeysAndPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw")) {
            JsonObject translated = read(locale);
            for (String key : english.keySet()) {
                if (!key.startsWith("waypoint.remote.")) continue;
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    private JsonObject read(String locale) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/server_waypoint/lang/" + locale + ".json");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private long placeholders(String value) {
        return java.util.regex.Pattern.compile("%s").matcher(value).results().count();
    }
}
