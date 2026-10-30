package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.local.LocalTeleportCommand;
import _959.server_waypoint.core.edit.EditResultStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    // As Minecraft reads a translation: %% is a literal percent sign, %s takes the next argument and
    // %<n>$s takes argument n.
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%(?:(\\d+)\\$)?([s%])");
    private static final List<String> LABEL_KEYS = List.of(
            "waypoint.form.destination",
            "waypoint.form.dimension",
            "waypoint.form.list",
            "waypoint.form.name",
            "waypoint.form.initials",
            "waypoint.form.display_name",
            "waypoint.form.color",
            "waypoint.form.visibility",
            "waypoint.form.position",
            "waypoint.form.yaw",
            "waypoint.form.keywords",
            "waypoint.form.description",
            "waypoint.form.optional",
            "waypoint.form.no_icon",
            "waypoint.form.empty_display_name",
            "waypoint.profile.name",
            "waypoint.profile.rename.body",
            "waypoint.profile.list_section",
            "waypoint.profile.command_section",
            "waypoint.profile.in_use.automatic",
            "waypoint.profile.in_use.manual",
            "waypoint.profile.list_section.plain",
            "waypoint.profile.command_section.plain"
    );
    private static final List<String> TOOLTIP_KEYS = List.of(
            "waypoint.form.destination.tooltip",
            "waypoint.form.dimension.tooltip",
            "waypoint.form.list.tooltip",
            "waypoint.form.name.tooltip",
            "waypoint.form.initials.tooltip",
            "waypoint.form.display_name.tooltip",
            "waypoint.form.icon.tooltip",
            "waypoint.form.color.tooltip",
            "waypoint.form.visibility.tooltip",
            "waypoint.form.position.tooltip",
            "waypoint.form.yaw.tooltip",
            "waypoint.form.keywords.tooltip",
            "waypoint.form.description.tooltip"
    );
    private static final List<String> STATUS_KEYS = List.of(
            "waypoint.form.status.adding",
            "waypoint.form.status.saving",
            "waypoint.form.status.add_timeout",
            "waypoint.form.status.send_failed",
            "waypoint.form.status.obsolete",
            "waypoint.form.status.rejected",
            "waypoint.form.status.denied",
            "waypoint.form.status.disconnected",
            "waypoint.form.status.persistence_failed",
            "waypoint.form.status.invalid_template",
            "waypoint.form.status.dimension_required",
            "waypoint.form.status.command_too_long"
    );
    // The keys the screens use that other screens define too.
    private static final List<String> SHARED_KEYS = List.of(
            "server_waypoint.map.add_to_server",
            "server_waypoint.map.edit_on_server",
            "server_waypoint.map.share.add_prompt",
            "waypoint.add.screen.title",
            "waypoint.edit.screen.title",
            "waypoint.edit.screen.location",
            "waypoint.edit.screen.previous_color.hover",
            "waypoint.edit.screen.current_color.hover",
            "waypoint.add.button",
            "waypoint.save.button",
            "waypoint.reset.button",
            "waypoint.global",
            "waypoint.local",
            "waypoint.icon.label",
            "waypoint.icon.clear",
            "server_waypoint.cancel.button"
    );
    private static final List<String> RETIRED_KEYS = List.of(
            "waypoint.edit.screen.name.entry",
            "waypoint.edit.screen.identifier.entry",
            "waypoint.edit.screen.display_name.entry",
            "waypoint.edit.screen.initials.entry",
            "waypoint.edit.screen.color",
            "waypoint.edit.screen.coords_yaw",
            "waypoint.edit.screen.visibility",
            "waypoint.display_name.clear.button",
            "waypoint.display_name.keep.button",
            "waypoint.update.button",
            "waypoint.dimension.info",
            "waypoint.list_name.info",
            "waypoint.profile.select",
            "waypoint.profile.create",
            "waypoint.profile.rename",
            "waypoint.profile.delete_confirm",
            "waypoint.profile.invalid",
            "waypoint.profile.switch",
            "waypoint.profile.search",
            "waypoint.profile.search_hint",
            "waypoint.profile.switch_hint",
            "waypoint.profile.already_active",
            "waypoint.profile.introduction",
            "waypoint.profile.create_section",
            "waypoint.profile.create_hint",
            "waypoint.profile.current_section",
            "waypoint.profile.editing",
            "waypoint.profile.teleport_section",
            "waypoint.profile.teleport_hint",
            "waypoint.profile.tokens",
            "waypoint.profile.command_hint",
            "waypoint.profile.remove_section",
            "waypoint.profile.remove_hint",
            "waypoint.profile.delete_named",
            "server_waypoint.done.button"
    );

    @Test
    void allSixLocalesDefineTheFormKeysWithTheArgumentsOfEnglish() throws Exception {
        List<String> keys = new ArrayList<>();
        keys.addAll(LABEL_KEYS);
        keys.addAll(TOOLTIP_KEYS);
        keys.addAll(STATUS_KEYS);
        keys.addAll(SHARED_KEYS);
        assertSameKeysAndArguments(keys);
    }

    @Test
    void collectionAndProfileControlsAreTranslatedInEveryLocale() throws Exception {
        var english = read("en_us");
        var keys = english.keySet().stream().filter(key -> key.startsWith("waypoint.collection.")
                || key.startsWith("waypoint.profile.") || key.equals("waypoint.copy")
                || key.equals("waypoint.form.status.saved") || key.equals("waypoint.form.status.awaiting_server")
                || key.equals("waypoint.form.status.command_submitted")).toList();
        assertFalse(keys.isEmpty());
        assertSameKeysAndArguments(keys);
    }

    @Test
    void everyCheckMessageIsTranslatedInEveryLocale() throws Exception {
        List<String> keys = new ArrayList<>();
        for (WaypointFormCheck.Message message : WaypointFormCheck.Message.values()) {
            keys.add(message.translationKey());
        }
        assertSameKeysAndArguments(keys);
    }

    @Test
    void everyTemplateProblemIsTranslatedInEveryLocale() throws Exception {
        List<String> keys = new ArrayList<>();
        for (LocalTeleportCommand.ProblemKind kind : LocalTeleportCommand.ProblemKind.values()) {
            keys.add("waypoint.profile.problem." + kind.name().toLowerCase(Locale.ROOT));
        }
        assertSameKeysAndArguments(keys);
    }

    @Test
    void everyEditResultHasAMessageInEveryLocale() throws Exception {
        List<String> keys = new ArrayList<>();
        for (EditResultStatus status : EditResultStatus.values()) {
            if (status != EditResultStatus.SUCCESS) {
                keys.add("waypoint.edit.error." + status.name().toLowerCase(Locale.ROOT));
            }
        }
        keys.add("waypoint.edit.error.response_timeout");
        assertTrue(keys.contains("waypoint.edit.error.upload_busy"));
        assertSameKeysAndArguments(keys);
    }

    @Test
    void theMessagesTakeTheArgumentsTheScreensPass() throws Exception {
        JsonObject english = read("en_us");

        assertEquals(List.of(1, 2), arguments(english.get("waypoint.form.status.name_taken").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.duplicate_keyword").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.too_many_keywords").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.keyword_too_long").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.new_list").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.visibility.tooltip").getAsString()));
        assertEquals(List.of(1, 2), arguments(english.get("waypoint.edit.screen.location").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.edit.screen.title").getAsString()));
        assertEquals(List.of(1, 2), arguments(english.get("waypoint.profile.status.renamed").getAsString()));
        assertEquals(List.of(1, 2), arguments(english.get("waypoint.profile.status.deleted_switched").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.list_section").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.command_section").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.use.tooltip").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.name.taken").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.problem.unknown").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.reset_command").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.profile.discard.body").getAsString()));
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
    void labelsAreWordsWithoutTrailingColons() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : LABEL_KEYS) {
                String label = translated.get(key).getAsString();
                assertFalse(label.trim().endsWith(":") || label.trim().endsWith("："), locale + ": " + key);
            }
        }
    }

    private void assertSameKeysAndArguments(List<String> keys) throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : keys) {
                assertTrue(english.has(key), "en_us: " + key);
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(arguments(english.get(key).getAsString()),
                        arguments(translated.get(key).getAsString()), locale + ": " + key);
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
