package _959.server_waypoint.server.command;

import _959.server_waypoint.text.chat.ChatIcons;
//? if >=1.21.9 {
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.VanillaChatSprites;
import _959.server_waypoint.util.NamespacedId;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.ObjectContents;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
//?}

/** Object API references are absent from builds before Minecraft 1.21.9. */
public final class CommandChatIcons {
    //? if >=1.21.9 {
    public static final ChatIcons INSTANCE = new ChatIcons() {
        @Override
        public @Nullable Component item(NamespacedId id) {
            String sprite = VanillaChatSprites.sprite(id);
            if (sprite == null) return null;
            String atlas = "minecraft:blocks";
            //? if >=1.21.11
            if (sprite.startsWith("minecraft:item/")) atlas = "minecraft:items";
            return Component.object(ObjectContents.sprite(Key.key(atlas), Key.key(sprite)));
        }

        @Override
        public Component playerHead(UUID id) {
            return Component.object(ObjectContents.playerHead(id));
        }

        @Override
        public Component withoutIcons(Component message) {
            return Chat.without(message, ObjectComponent.class::isInstance);
        }
    };
    //?} else {
    /*public static final ChatIcons INSTANCE = ChatIcons.NONE;
    *///?}

    private CommandChatIcons() { }
}
