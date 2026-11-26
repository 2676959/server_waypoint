package _959.server_waypoint.common.client.local;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.util.NamespacedId;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The profile template is a single ordinary command, with named literal substitutions only. */
public final class LocalTeleportCommand {
    private LocalTeleportCommand() { }

    public enum Placeholder {
        DIMENSION, X, Y, Z, YAW;

        public String token() { return "{" + key() + "}"; }

        private String key() { return name().toLowerCase(Locale.ROOT); }

        private static @Nullable Placeholder named(String key) {
            for (Placeholder placeholder : values()) {
                if (placeholder.key().equals(key)) return placeholder;
            }
            return null;
        }
    }

    /** A {@code [start, end)} span of the raw template; plain text has no placeholder and is no mistake. */
    public record Token(int start, int end, @Nullable Placeholder placeholder, boolean mistake) { }

    public enum ProblemKind { CONTROL, EMPTY, EXTRA_SLASH, STRAY_CLOSE, UNCLOSED, UNKNOWN }

    /** {@code text} is the offending span for brace mistakes and empty for the other kinds. */
    public record Problem(ProblemKind kind, String text) { }

    public static String expand(String template, String dimensionName, SimpleWaypoint waypoint) {
        if (template == null || template.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Command contains control characters");
        }
        NamespacedId.parse(dimensionName);
        String command = withoutSlash(template);
        if (command.isBlank() || command.startsWith("/")) throw new IllegalArgumentException("Empty or invalid command");
        SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
        StringBuilder expanded = new StringBuilder();
        for (Token token : tokens(command)) {
            if (token.mistake()) {
                throw new IllegalArgumentException(mistake(command, token).kind() == ProblemKind.UNKNOWN
                        ? "Unknown command token" : "Unbalanced command token");
            }
            if (token.placeholder() == null) {
                expanded.append(command, token.start(), token.end());
                continue;
            }
            expanded.append(switch (token.placeholder()) {
                case DIMENSION -> dimensionName;
                case X -> Integer.toString(snapshot.x());
                case Y -> Integer.toString(snapshot.y());
                case Z -> Integer.toString(snapshot.z());
                case YAW -> Integer.toString(snapshot.yaw());
            });
        }
        return expanded.toString();
    }

    /**
     * Splits the template into contiguous spans in order. A lone closing brace, an opening brace
     * without a later closing one (spanning to the end) and a braced unknown name are mistakes.
     */
    public static List<Token> tokens(String template) {
        List<Token> tokens = new ArrayList<>();
        int textStart = 0;
        int i = 0;
        while (i < template.length()) {
            char character = template.charAt(i);
            if (character != '{' && character != '}') {
                i++;
                continue;
            }
            int end = i + 1;
            Placeholder placeholder = null;
            if (character == '{') {
                int close = template.indexOf('}', i + 1);
                end = close < 0 ? template.length() : close + 1;
                if (close >= 0) placeholder = Placeholder.named(template.substring(i + 1, close));
            }
            if (textStart < i) tokens.add(new Token(textStart, i, null, false));
            tokens.add(new Token(i, end, placeholder, placeholder == null));
            textStart = i = end;
        }
        if (textStart < template.length()) tokens.add(new Token(textStart, template.length(), null, false));
        return tokens;
    }

    /** The first problem {@link #expand} would reject the template for, checked in the same order. */
    public static Optional<Problem> problem(String template) {
        if (template.codePoints().anyMatch(Character::isISOControl)) return Optional.of(new Problem(ProblemKind.CONTROL, ""));
        String command = withoutSlash(template);
        if (command.isBlank()) return Optional.of(new Problem(ProblemKind.EMPTY, ""));
        if (command.startsWith("/")) return Optional.of(new Problem(ProblemKind.EXTRA_SLASH, ""));
        return tokens(template).stream().filter(Token::mistake).findFirst().map(token -> mistake(template, token));
    }

    private static String withoutSlash(String template) {
        String command = template.trim();
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static Problem mistake(String template, Token token) {
        String text = template.substring(token.start(), token.end());
        ProblemKind kind = text.equals("}") ? ProblemKind.STRAY_CLOSE
                : text.endsWith("}") ? ProblemKind.UNKNOWN : ProblemKind.UNCLOSED;
        return new Problem(kind, text);
    }
}
