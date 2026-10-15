package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.util.NamespacedId;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Set;

public final class WaypointIconPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_core");
    public static final int MAX_LENGTH = 256;
    private static final Set<String> VOXELMAP_SUFFIXES = Set.of(
            "apple", "axe", "boat", "camera", "carrot", "chicken", "cloud", "diamond",
            "fire", "fish", "flower", "gear", "heart", "hoe", "house", "key", "like",
            "minecart", "mushroom", "person", "pickaxe", "point", "record", "science",
            "shovel", "skull", "star", "steak", "temple", "tree", "wheat", "world"
    );

    private WaypointIconPolicy() {
    }

    public static @Nullable NamespacedId validate(@Nullable NamespacedId id) {
        if (id != null && id.toString().length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Waypoint icon ID is too long");
        }
        return id;
    }

    public static Set<String> voxelMapSuffixes() {
        return VOXELMAP_SUFFIXES;
    }

    public static boolean isKnownVoxelMapIcon(NamespacedId id) {
        return id.namespace().equals("voxelmap")
                && (id.path().equals("waypoint") || VOXELMAP_SUFFIXES.contains(id.path()));
    }

    public static final class JsonAdapter implements JsonSerializer<NamespacedId>, JsonDeserializer<NamespacedId> {
        @Override
        public JsonElement serialize(NamespacedId icon, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(icon.toString());
        }

        @Override
        public NamespacedId deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            try {
                if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Waypoint icon must be a string");
                }
                return validate(NamespacedId.parse(json.getAsString()));
            } catch (IllegalArgumentException invalid) {
                LOGGER.warn("Ignoring invalid waypoint icon", invalid);
                return null;
            }
        }
    }
}
