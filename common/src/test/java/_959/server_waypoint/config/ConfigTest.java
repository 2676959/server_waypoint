package _959.server_waypoint.config;

import _959.server_waypoint.navigation.NavigationMethod;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTest {
    private static final Gson GSON = new Gson();

    @Test
    void missingPageLimitUsesAndSerializesDefault() {
        Config config = GSON.fromJson("{}", Config.class);

        assertEquals(Config.DEFAULT_PAGE_LIMIT, config.defaultPageLimit());
        assertEquals(
                Config.DEFAULT_PAGE_LIMIT,
                JsonParser.parseString(GSON.toJson(config))
                        .getAsJsonObject()
                        .get("defaultPageLimit")
                        .getAsInt()
        );
    }

    @Test
    void configuredPageLimitIsUsedWithinSupportedRange() {
        Config config = GSON.fromJson("{\"defaultPageLimit\": 25}", Config.class);

        assertEquals(25, config.defaultPageLimit());
    }

    @Test
    void configuredPageLimitIsConstrainedToCommandRange() {
        Config belowMinimum = GSON.fromJson("{\"defaultPageLimit\": 0}", Config.class);
        Config aboveMaximum = GSON.fromJson("{\"defaultPageLimit\": 101}", Config.class);

        assertEquals(Config.MIN_PAGE_LIMIT, belowMinimum.defaultPageLimit());
        assertEquals(Config.MAX_PAGE_LIMIT, aboveMaximum.defaultPageLimit());
    }

    @Test
    void missingNavigationMethodsUsesAndSerializesBuiltInDefault() {
        Config config = GSON.fromJson("{}", Config.class);

        assertEquals(
                NavigationMethod.builtInDefaultMethods(),
                config.defaultNavigationMethods()
        );
        assertEquals(
                List.of(NavigationMethod.ACTIONBAR.id()),
                JsonParser.parseString(GSON.toJson(config))
                        .getAsJsonObject()
                        .getAsJsonArray("defaultNavigationMethods")
                        .asList()
                        .stream()
                        .map(element -> element.getAsString())
                        .toList()
        );
    }

    @Test
    void navigationMethodsRoundTripAsCanonicalIds() {
        Config config = GSON.fromJson(
                "{\"defaultNavigationMethods\":[\"MAP\",\"bossbar\"]}",
                Config.class
        );

        assertEquals(
                Set.of(NavigationMethod.MAP, NavigationMethod.BOSSBAR),
                config.defaultNavigationMethods()
        );
        var serialized = JsonParser.parseString(GSON.toJson(config)).getAsJsonObject();
        assertEquals(
                List.of("map", "bossbar"),
                serialized.getAsJsonArray("defaultNavigationMethods")
                        .asList()
                        .stream()
                        .map(element -> element.getAsString())
                        .toList()
        );
        assertFalse(serialized.has("defaultNavigationSelection"));
    }

    @Test
    void invalidNavigationMethodValuesThrow() {
        List<String> invalidConfigs = List.of(
                "{\"defaultNavigationMethods\":null}",
                "{\"defaultNavigationMethods\":\"actionbar\"}",
                "{\"defaultNavigationMethods\":[]}",
                "{\"defaultNavigationMethods\":[null]}",
                "{\"defaultNavigationMethods\":[1]}",
                "{\"defaultNavigationMethods\":[\"unknown\"]}",
                "{\"defaultNavigationMethods\":[\"map\",\"MAP\"]}"
        );

        for (String json : invalidConfigs) {
            assertThrows(JsonParseException.class, () -> GSON.fromJson(json, Config.class), json);
        }
    }
}
