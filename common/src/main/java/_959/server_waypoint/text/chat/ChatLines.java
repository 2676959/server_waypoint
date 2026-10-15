package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Collects a message line by line. The built message never ends with a newline. */
public final class ChatLines {
    private final List<Component> lines = new ArrayList<>();

    public ChatLines add(@Nullable Component line) {
        if (line != null) {
            this.lines.add(line);
        }
        return this;
    }

    /** Adds one line made of these pieces, leaving null pieces out. */
    public ChatLines line(@Nullable Component... pieces) {
        return this.add(Chat.concat(pieces));
    }

    public ChatLines addAll(ChatLines other) {
        this.lines.addAll(other.lines);
        return this;
    }

    public int size() {
        return this.lines.size();
    }

    public boolean isEmpty() {
        return this.lines.isEmpty();
    }

    public Component build() {
        Component message = Component.empty();
        for (int index = 0; index < this.lines.size(); index++) {
            if (index > 0) {
                message = message.appendNewline();
            }
            message = message.append(this.lines.get(index));
        }
        return message;
    }

    /** The message as a screen, which players read apart from the next message ({@link Chat#screen}). */
    public Component buildScreen() {
        return Chat.screen(this.build());
    }
}
