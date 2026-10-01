package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * What clicking a piece of chat does. The client refuses commands longer than 256 characters, so
 * such a click is dropped and its text stays plain.
 */
public record Click(ClickEvent.Action action, String command) {
    public static final int MAX_COMMAND_LENGTH = 256;

    public Click {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(command, "command");
    }

    public static Click run(String command) {
        return new Click(ClickEvent.Action.RUN_COMMAND, command);
    }

    public static Click suggest(String command) {
        return new Click(ClickEvent.Action.SUGGEST_COMMAND, command);
    }

    public boolean fits() {
        return this.command.length() <= MAX_COMMAND_LENGTH;
    }

    public @Nullable ClickEvent event() {
        return this.fits() ? ClickEvent.clickEvent(this.action, this.command) : null;
    }
}
