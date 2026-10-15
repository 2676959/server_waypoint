package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * What clicking a piece of chat does. The client refuses commands longer than 256 characters, so
 * such a click is dropped and its text stays plain. Events come from the ClickEvent factories:
 * Paper's newer Adventure no longer has the ClickEvent.Action constants this code compiles against.
 */
public record Click(Kind kind, String command) {
    public static final int MAX_COMMAND_LENGTH = 256;

    public enum Kind {
        RUN,
        SUGGEST
    }

    public Click {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(command, "command");
    }

    public static Click run(String command) {
        return new Click(Kind.RUN, command);
    }

    public static Click suggest(String command) {
        return new Click(Kind.SUGGEST, command);
    }

    public boolean fits() {
        return this.command.length() <= MAX_COMMAND_LENGTH;
    }

    public @Nullable ClickEvent event() {
        if (!this.fits()) {
            return null;
        }
        return this.kind == Kind.RUN ? ClickEvent.runCommand(this.command) : ClickEvent.suggestCommand(this.command);
    }
}
