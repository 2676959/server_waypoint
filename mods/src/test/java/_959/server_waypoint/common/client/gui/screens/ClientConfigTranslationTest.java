package _959.server_waypoint.common.client.gui.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");
    private static final List<String> SCREEN_KEYS = List.of(
            "server_waypoint.config.on",
            "server_waypoint.config.off"
    );
    private static final List<String> RETIRED_KEYS = List.of(
            "server_waypoint.config.true",
            "server_waypoint.config.false"
    );

    @Test
    void allSixLocalesDefineTheScreenKeysWithMatchingPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : SCREEN_KEYS) {
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    @Test
    void retiredKeysAreRemovedFromEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : RETIRED_KEYS) {
                assertFalse(translated.has(key), locale + ": " + key);
            }
        }
    }

    private JsonObject read(String locale) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/server_waypoint/lang/" + locale + ".json");
        assertNotNull(stream, locale);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static long placeholders(String value) {
        return PLACEHOLDER.matcher(value).results().count();
    }
}
