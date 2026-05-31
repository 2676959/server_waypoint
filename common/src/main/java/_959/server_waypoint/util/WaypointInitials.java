package _959.server_waypoint.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static _959.server_waypoint.util.ListMapUtils.getLastElement;

public final class WaypointInitials {
    public static final String SINGLE_WORD_REGEX = "^[a-zA-Z0-9+._-]+$";

    private WaypointInitials() {
    }

    public static List<String> getInitialsCandidatesFromName(String name) {
        if (name.isBlank()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(name.substring(0, 1).toUpperCase());
        if (name.length() >= 2) {
            char c = name.charAt(1);
            if (!(c == '-' || c == '_' || c == '.' || c == ' ')) {
                candidates.add(name.substring(0, 2).toUpperCase());
            }
        }
        if (name.matches(SINGLE_WORD_REGEX)) {
            candidates.add(getInitialsBySplitting(name.replace('_', '-'), '-', '.'));
            candidates.add(getInitialsFromCapitals(name));
        } else {
            candidates.add(getInitialsBySplitting(name, ' '));
        }
        candidates.sort(Comparator.comparingInt(String::length));
        return candidates;
    }

    public static String getDefaultInitials(String name) {
        List<String> candidates = getInitialsCandidatesFromName(name);
        return candidates.isEmpty() ? "" : getLastElement(candidates);
    }

    private static @NotNull String getInitialsFromCapitals(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isVariationSelector(int codePoint) {
        return (codePoint >= 0xFE00 && codePoint <= 0xFE0F) || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }

    private static boolean isEmojiModifier(int codePoint) {
        return codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    private static boolean isGraphemeExtender(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || isVariationSelector(codePoint)
                || isEmojiModifier(codePoint);
    }

    private static void appendFirstGraphemeCluster(String string, int start, StringBuilder sb) {
        if (start >= string.length()) {
            return;
        }
        int pos = start;
        int firstCodePoint = string.codePointAt(pos);
        boolean needsRegionalIndicatorPair = isRegionalIndicator(firstCodePoint);
        sb.appendCodePoint(firstCodePoint);
        pos += Character.charCount(firstCodePoint);
        while (pos < string.length()) {
            int codePoint = string.codePointAt(pos);
            if (isGraphemeExtender(codePoint)) {
                sb.appendCodePoint(codePoint);
                pos += Character.charCount(codePoint);
                continue;
            }
            if (needsRegionalIndicatorPair && isRegionalIndicator(codePoint)) {
                sb.appendCodePoint(codePoint);
                pos += Character.charCount(codePoint);
                needsRegionalIndicatorPair = false;
                continue;
            }
            if (codePoint == 0x200D) {
                sb.appendCodePoint(codePoint);
                pos += Character.charCount(codePoint);
                if (pos < string.length()) {
                    int joinedCodePoint = string.codePointAt(pos);
                    sb.appendCodePoint(joinedCodePoint);
                    pos += Character.charCount(joinedCodePoint);
                }
                needsRegionalIndicatorPair = false;
                continue;
            }
            break;
        }
    }

    private static @NotNull String getInitialsBySplitting(String name, char separator, char connector) {
        int length = name.length();
        StringBuilder sb = new StringBuilder(length);
        int lastPos = 0;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c == separator) {
                appendFirstGraphemeCluster(name, lastPos, sb);
                lastPos = i + 1;
                continue;
            }
            if (c == connector) {
                sb.append(connector);
            }
        }
        appendFirstGraphemeCluster(name, lastPos, sb);
        return sb.toString();
    }

    private static @NotNull String getInitialsBySplitting(String name, char separator) {
        int length = name.length();
        StringBuilder sb = new StringBuilder(length);
        int lastPos = 0;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c == separator) {
                appendFirstGraphemeCluster(name, lastPos, sb);
                lastPos = i + 1;
            }
        }
        appendFirstGraphemeCluster(name, lastPos, sb);
        return sb.toString();
    }
}
