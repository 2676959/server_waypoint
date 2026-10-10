package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeSelection;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    // The editor's own text. The theme keys themselves are shown raw, so none of them is here, and the
    // dropdown's preset names have a test of their own.
    private static final List<String> EDITOR_KEYS = List.of(
            "server_waypoint.theme.screen.title",
            "server_waypoint.theme.variables",
            "server_waypoint.theme.color_picker",
            "server_waypoint.theme.save",
            "server_waypoint.theme.save.failed",
            "server_waypoint.theme.alpha",
            "server_waypoint.theme.no_key",
            "server_waypoint.theme.no_key.hint",
            "server_waypoint.theme.reset_key",
            "server_waypoint.theme.reset.tooltip",
            "server_waypoint.theme.selector.tooltip",
            "server_waypoint.theme.custom_replaced",
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
            "server_waypoint.theme.preview.danger",
            "server_waypoint.theme.preview.tooltip",
            "server_waypoint.theme.preview.choice"
    );
    private static final List<String> REMOVED_KEYS = List.of(
            "server_waypoint.theme.rgb",
            "server_waypoint.theme.opacity",
            "server_waypoint.theme.reset.preview"
    );

    @Test
    void everyEditorKeyExistsInEveryLocaleWithTheArgumentsOfEnglish() throws IOException {
        JsonObject english = read("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : EDITOR_KEYS) {
                if (!translated.has(key)) {
                    problems.add(locale + ": missing " + key);
                } else if (english.has(key) && !argumentsOf(english, key).equals(argumentsOf(translated, key))) {
                    problems.add(locale + ": " + key + " takes other arguments than the English text");
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    @Test
    void noLocaleTranslatesTheRawThemeKeysOrTheRemovedKeys() throws IOException {
        List<String> untranslated = new ArrayList<>(REMOVED_KEYS);
        for (WidgetThemeVariable variable : WidgetThemeVariable.values()) {
            untranslated.add("server_waypoint.theme.variable." + variable.getJsonName());
        }
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : untranslated) {
                if (translated.has(key)) {
                    problems.add(locale + ": still has " + key);
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    @Test
    void everyPresetNameIsTranslatedInEveryLanguage() throws IOException {
        for (String language : LOCALES) {
            JsonObject translations = read(language);
            for (WidgetThemeSelection selection : WidgetThemeSelection.values()) {
                String key = "server_waypoint.theme.preset." + selection.getId();
                assertTrue(translations.has(key),
                        () -> "Missing " + language + " translation for " + key);
            }
        }
    }

    private static List<Integer> argumentsOf(JsonObject translations, String key) {
        return ClientConfigTranslationTest.arguments(translations.get(key).getAsString());
    }

    private static JsonObject read(String locale) throws IOException {
        String resource = "/assets/server_waypoint/lang/" + locale + ".json";
        InputStream stream = WidgetThemeTranslationTest.class.getResourceAsStream(resource);
        assertNotNull(stream, () -> "Missing language resource: " + resource);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
