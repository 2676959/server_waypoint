package _959.server_waypoint.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

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

    private static boolean looksLikeJson(String rawText) {
        if (rawText == null) {
            return false;
        }
        String trimmed = rawText.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"");
    }
}
