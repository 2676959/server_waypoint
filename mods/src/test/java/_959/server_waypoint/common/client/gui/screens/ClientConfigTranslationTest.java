package _959.server_waypoint.common.client.gui.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    // As Minecraft reads a translation: %% is a literal percent sign, %s takes the next argument and
    // %<n>$s takes argument n.
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%(?:(\\d+)\\$)?([s%])");
    private static final List<String> SCREEN_KEYS = List.of(
            "server_waypoint.config.screen.title",
            "server_waypoint.config.on",
            "server_waypoint.config.off",
            "server_waypoint.config.enable_waypoint_render",
            "server_waypoint.config.enable_waypoint_render.tooltip",
            "server_waypoint.config.waypoint_scale_factor",
            "server_waypoint.config.waypoint_scale_factor.tooltip",
            "server_waypoint.config.waypoint_vertical_offset",
            "server_waypoint.config.waypoint_vertical_offset.tooltip",
            "server_waypoint.config.waypoint_bg_opacity",
            "server_waypoint.config.waypoint_bg_opacity.tooltip",
            "server_waypoint.config.local_waypoint_view_distance",
            "server_waypoint.config.local_waypoint_view_distance.tooltip",
            "server_waypoint.config.theme",
            "server_waypoint.config.theme.open",
            "server_waypoint.config.theme.tooltip",
            "server_waypoint.config.confirm_sync",
            "server_waypoint.config.section.rendering",
            "server_waypoint.config.section.map_mods",
            "server_waypoint.config.section.appearance",
            "server_waypoint.map_mod.xaeros_minimap",
            "server_waypoint.map_mod.voxelmap",
            "server_waypoint.config.map_mod.auto_sync",
            "server_waypoint.config.map_mod.auto_sync.tooltip",
            "server_waypoint.config.map_mod.sync_now",
            "server_waypoint.config.map_mod.sync_button",
            "server_waypoint.config.map_mod.not_installed",
            "server_waypoint.config.map_mod.not_installed.tooltip",
            "server_waypoint.config.unit.percent",
            "server_waypoint.config.unit.chunks",
            "server_waypoint.config.value.percent",
            "server_waypoint.config.value.chunks",
            "server_waypoint.config.default",
            "server_waypoint.config.reset",
            "server_waypoint.config.reset_all",
            "server_waypoint.config.reset_all.title",
            "server_waypoint.config.reset_all.body",
            "server_waypoint.config.reset_all.confirm",
            "server_waypoint.config.reset_all.done",
            "server_waypoint.config.sync.title",
            "server_waypoint.config.sync.body",
            "server_waypoint.config.sync.stays",
            "server_waypoint.config.sync.stays.detail",
            "server_waypoint.config.sync.lost",
            "server_waypoint.config.sync.lost.detail",
            "server_waypoint.config.sync.done",
            "server_waypoint.config.sync.failed",
            "server_waypoint.config.sync.no_world",
            "server_waypoint.config.sync.map_mod_loading",
            "server_waypoint.manager.loading",
            "server_waypoint.no_serverside_support",
            "server_waypoint.incompatible_protocol_version",
            "server_waypoint.cancel.button",
            "server_waypoint.confirm.button"
    );
    private static final List<String> LABELS_WITHOUT_UNITS = List.of(
            "server_waypoint.config.waypoint_scale_factor",
            "server_waypoint.config.waypoint_vertical_offset",
            "server_waypoint.config.local_waypoint_view_distance"
    );
    private static final List<String> RETIRED_KEYS = List.of(
            "server_waypoint.config.true",
            "server_waypoint.config.false",
            "server_waypoint.config.waypoint_bg_alpha",
            "server_waypoint.config.auto_sync_to_xaeros",
            "server_waypoint.config.sync_to_xaeros",
            "server_waypoint.config.sync_to_xaeros.warn.1",
            "server_waypoint.config.sync_to_xaeros.warn.2",
            "server_waypoint.config.sync_to_xaeros.warn.3",
            "server_waypoint.config.sync_to_xaeros.warn.4",
            "server_waypoint.config.sync_to_xaeros.warn.5"
    );

    @Test
    void allSixLocalesDefineTheScreenKeysWithTheArgumentsOfEnglish() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : SCREEN_KEYS) {
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(arguments(english.get(key).getAsString()),
                        arguments(translated.get(key).getAsString()), locale + ": " + key);
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

    @Test
    void percentSignsAreEscapedInEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            assertEquals("%%", translated.get("server_waypoint.config.unit.percent").getAsString(), locale);
            assertTrue(translated.get("server_waypoint.config.value.percent").getAsString().contains("%s%%"), locale);
        }
    }

    @Test
    void labelsLeaveTheirUnitsToTheUnitColumn() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : LABELS_WITHOUT_UNITS) {
                String label = translated.get(key).getAsString();
                assertFalse(label.contains("(") || label.contains("（"), locale + ": " + key);
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

    /**
     * The positions of the arguments a translation shows, sorted, so {@code %s} and {@code %1$s}
     * compare equal. A literal {@code %%} shows none.
     */
    private static List<Integer> arguments(String value) {
        List<Integer> arguments = new ArrayList<>();
        int next = 1;
        Matcher matcher = FORMAT_SPECIFIER.matcher(value);
        while (matcher.find()) {
            if (matcher.group(2).equals("%")) {
                continue;
            }
            arguments.add(matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : next++);
        }
        arguments.sort(null);
        return arguments;
    }
}
