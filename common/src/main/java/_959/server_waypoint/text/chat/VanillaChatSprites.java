package _959.server_waypoint.text.chat;

import _959.server_waypoint.util.NamespacedId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Verified vanilla atlas textures, rather than guesses based on item registry names. */
public final class VanillaChatSprites {
    private static final JsonObject SPRITES = load();

    private VanillaChatSprites() { }

    public static @Nullable String sprite(NamespacedId item) {
        var value = SPRITES.get(item.toString());
        return value == null ? null : value.getAsString();
    }

    private static JsonObject load() {
        try (var input = new InputStreamReader(Objects.requireNonNull(
                VanillaChatSprites.class.getResourceAsStream("/assets/server_waypoint/chat-sprites.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(input).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
