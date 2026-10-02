package _959.server_waypoint.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TranslationFilesTest {
    public static final List<String> LOCALES = List.of("en_us", "zh_cn", "es_es", "he_il", "zh_hk", "zh_tw");
    private static final Pattern PRINTF = Pattern.compile("%(\\d+\\$)?[sdf]");
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    public static Map<String, String> load(String locale) {
        String path = "lang/" + locale + ".json";
        try (InputStream input = TranslationFilesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            JsonObject object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return values;
        } catch (IOException exception) {
            throw new AssertionError(path, exception);
        }
    }

    @Test
    void everyLocaleOnlyHasKeysThatEnglishHas() {
        Map<String, String> english = load("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (String key : load(locale).keySet()) {
                if (!english.containsKey(key)) {
                    problems.add(locale + ": " + key);
                }
            }
        }
        assertTrue(problems.isEmpty(), "Keys missing from en_us:\n" + String.join("\n", problems));
    }

    @Test
    void valuesAreMessageFormatPatterns() {
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> entry : load(locale).entrySet()) {
                String where = locale + ": " + entry.getKey() + " = " + entry.getValue();
                if (PRINTF.matcher(entry.getValue()).find()) {
                    problems.add("printf placeholder in " + where);
                }
                if (LONE_APOSTROPHE.matcher(entry.getValue()).find()) {
                    problems.add("unescaped apostrophe in " + where);
                }
                try {
                    new MessageFormat(entry.getValue());
                } catch (IllegalArgumentException invalid) {
                    problems.add("invalid pattern in " + where);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void placeholdersMatchEnglish() {
        Map<String, String> english = load("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> entry : load(locale).entrySet()) {
                String expected = english.get(entry.getKey());
                if (expected != null && !placeholders(expected).equals(placeholders(entry.getValue()))) {
                    problems.add(locale + ": " + entry.getKey());
                }
            }
        }
        assertTrue(problems.isEmpty(), "Placeholders differ from en_us:\n" + String.join("\n", problems));
    }

    @Test
    void everyWpKeyIsInAllSixLocales() {
        Map<String, String> english = load("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            Set<String> keys = load(locale).keySet();
            for (String key : english.keySet()) {
                if (key.startsWith("wp.") && !keys.contains(key)) {
                    problems.add(locale + ": " + key);
                }
            }
        }
        assertTrue(problems.isEmpty(), "wp. keys missing from a locale:\n" + String.join("\n", problems));
    }

    private static Set<String> placeholders(String value) {
        Set<String> indices = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            indices.add(matcher.group(1));
        }
        return indices;
    }
}
