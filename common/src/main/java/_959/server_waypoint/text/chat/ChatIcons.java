package _959.server_waypoint.text.chat;

import _959.server_waypoint.util.NamespacedId;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Platform-supplied chat objects; common code stays usable with older Adventure runtimes. */
public interface ChatIcons {
    ChatIcons NONE = new ChatIcons() { };

    default @Nullable Component item(NamespacedId id) {
        return null;
    }

    default @Nullable Component playerHead(UUID id) {
        return null;
    }

    /**
     * The message without the objects this platform draws, for the console, RCON and command blocks
     * reading a copy of a player's view: they would print each object as its description.
     */
    default Component withoutIcons(Component message) {
        return message;
    }
}
