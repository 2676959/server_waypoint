package _959.server_waypoint.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
