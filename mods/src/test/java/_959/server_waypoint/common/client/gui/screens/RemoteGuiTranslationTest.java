package _959.server_waypoint.common.client.gui.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoteGuiTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    private static final List<String> MANAGER_KEYS = List.of(
            "server_waypoint.manager.title",
            "server_waypoint.manager.loading",
            "waypoint.remote.gui.server_status",
            "waypoint.empty.no_matches",
            "waypoint.empty.all",
            "waypoint.empty.dimension",
            "waypoint.remote.empty.unauthorized",
            "waypoint.remote.empty.server_unavailable",
            "waypoint.remote.empty.server",
            "waypoint.remote.empty.dimension"
    );
    private static final List<String> RETIRED_KEYS = List.of("waypoint.remote.gui.selector", "waypoint.empty_mark");

    @Test void allSixLocalesCoverRemoteGuiKeysAndPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : english.keySet()) {
                if (!key.startsWith("waypoint.remote.")) continue;
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    @Test void allSixLocalesDefineTheManagerKeysWithMatchingPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : MANAGER_KEYS) {
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    @Test void retiredManagerKeysAreRemovedFromEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : RETIRED_KEYS) {
                assertFalse(translated.has(key), locale + ": " + key);
            }
        }
    }

    @Test void localToggleTooltipNamesTheView() throws Exception {
        assertEquals("Local waypoints", read("en_us").get("waypoint.remote.gui.local").getAsString());
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
