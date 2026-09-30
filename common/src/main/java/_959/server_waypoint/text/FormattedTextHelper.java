package _959.server_waypoint.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FormattedTextHelper {
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_DESCRIPTION_LENGTH = 2048;
    public static final int MAX_KEYWORD_LENGTH = 64;
    public static final int MAX_KEYWORDS = 32;

    private FormattedTextHelper() {
    }

    public static Component parse(String rawText) {
        String resolvedText = rawText == null ? "" : rawText;
        if (!looksLikeJson(resolvedText)) {
            return Component.text(resolvedText);
        }
        try {
            return GsonComponentSerializer.gson().deserialize(resolvedText);
        } catch (RuntimeException ignored) {
            return Component.text(resolvedText);
        }
    }

    public static boolean isValidInput(String rawText) {
        if (!looksLikeJson(rawText)) {
            return true;
        }
        try {
            GsonComponentSerializer.gson().deserialize(rawText);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String plainText(String rawText) {
        StringBuilder result = new StringBuilder();
        ComponentFlattener.basic().flatten(parse(rawText), new FlattenerListener() {
            @Override
            public void component(String text) {
                result.append(text);
            }
        });
        return result.toString();
    }

    public static boolean hasDuplicateKeywords(List<String> keywords) {
        Set<String> normalizedKeywords = new HashSet<>();
        for (String keyword : keywords) {
            if (!normalizedKeywords.add(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits comma-separated keywords, trimming each and dropping empty entries, the way {@code /wp add}
     * reads its keywords argument.
     */
    public static List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.trim().isEmpty()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String keyword : rawKeywords.split(",", -1)) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return List.copyOf(keywords);
    }

    private static boolean looksLikeJson(String rawText) {
        if (rawText == null) {
            return false;
        }
        String trimmed = rawText.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"");
    }
}
