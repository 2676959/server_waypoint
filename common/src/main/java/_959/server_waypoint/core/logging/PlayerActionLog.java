package _959.server_waypoint.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Server-side action audit shared by commands, packets and platform adapters. */
public final class PlayerActionLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint.actions");

    private PlayerActionLog() { }

    /** Capture identity on the player's owning thread before asynchronous work. Null UUID denotes a non-player. */
    public record Actor(UUID playerId, String name) {
        public Actor {
            Objects.requireNonNull(name, "name");
        }
    }

    /** Snapshot of the executor and original command sender, which /execute as can separate. */
    public record Context(Actor executor, Actor sender) {
        public Context {
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(sender, "sender");
        }
    }

    /** Direct player actions such as GUI edits have the player as both executor and sender. */
    public static void log(Actor actor, String action, String outcome, Object... fields) {
        log(new Context(actor, actor), action, outcome, fields);
    }

    public static void log(Context context, String action, String outcome, Object... fields) {
        Actor actor = context.executor();
        Actor sender = context.sender();
        if (fields.length % 2 != 0) throw new IllegalArgumentException("Expected key/value pairs");
        StringBuilder message = new StringBuilder("action=").append(safe(action))
                .append(" player=").append(safe(actor.name()))
                .append(" player_id=").append(actor.playerId() == null ? "non-player" : actor.playerId())
                .append(" sender=").append(safe(sender.name()))
                .append(" sender_id=").append(sender.playerId() == null ? "non-player" : sender.playerId())
                .append(" outcome=").append(safe(outcome));
        for (int i = 0; i < fields.length; i += 2) {
            message.append(' ').append(safe(fields[i])).append('=').append(safe(fields[i + 1]));
        }
        LOGGER.info("{}", message);
    }

    private static String safe(Object value) {
        if (value == null) return "-";
        StringBuilder text = new StringBuilder();
        String.valueOf(value).codePoints().limit(256).forEach(c -> text.appendCodePoint(
                Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                        || c == 0x2028 || c == 0x2029 ? '?' : c));
        String result = text.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return result.codePoints().anyMatch(Character::isWhitespace) || result.contains("=") || result.contains("\"")
                ? "\"" + result + "\"" : result;
    }
}
