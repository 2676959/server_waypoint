package _959.server_waypoint.common.server.command;

import _959.server_waypoint.util.NamespacedId;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Finds representative flat chat sprites in installed mods, without loading client classes. */
final class ModdedChatSprites {
    private final Function<String, InputStream> resources;
    private final Map<NamespacedId, Optional<String>> cache = new ConcurrentHashMap<>();

    ModdedChatSprites(Function<String, InputStream> resources) {
        this.resources = resources;
    }

    @Nullable String sprite(NamespacedId item) {
        // Preserve the verified vanilla mapping's exclusions, such as tinted and dynamic items.
        if (item.namespace().equals("minecraft")) return null;
        return cache.computeIfAbsent(item, this::find).orElse(null);
    }

    private Optional<String> find(NamespacedId item) {
        try {
            JsonObject definition = json(resource("items", item, ".json"));
            if (definition != null) {
                return Optional.ofNullable(fromNode(definition.getAsJsonObject("model"), 0));
            }
            for (String kind : List.of("item/", "block/")) {
                NamespacedId model = new NamespacedId(item.namespace(), kind + item.path());
                String sprite = fromModel(model);
                if (sprite != null) return Optional.of(sprite);
            }
            for (String kind : List.of("item/", "block/")) {
                String sprite = verifiedTexture(item.namespace() + ":" + kind + item.path());
                if (sprite != null) return Optional.of(sprite);
            }
        } catch (IOException | RuntimeException invalidAsset) {
            // Optional assets must never prevent the waypoint message from being sent.
        }
        return Optional.empty();
    }

    private @Nullable String fromNode(@Nullable JsonObject node, int depth) throws IOException {
        if (node == null || depth >= 32) return null;
        String type = node.get("type").getAsString();
        if (type.startsWith("minecraft:")) type = type.substring("minecraft:".length());
        switch (type) {
            case "model":
                return fromModel(identifier(node.get("model").getAsString()));
            case "condition":
                return fromNode(node.getAsJsonObject("on_false"), depth + 1);
            case "select":
                if (node.has("property") && identifier(node.get("property").getAsString()).toString().equals("minecraft:display_context")) {
                    for (JsonElement entry : node.getAsJsonArray("cases")) {
                        JsonObject choice = entry.getAsJsonObject();
                        JsonElement when = choice.get("when");
                        boolean gui = when.isJsonArray()
                                ? when.getAsJsonArray().asList().stream().anyMatch(value -> value.getAsString().equals("gui"))
                                : when.getAsString().equals("gui");
                        if (gui) return fromNode(choice.getAsJsonObject("model"), depth + 1);
                    }
                }
                // Other selectors and ranges use a representative default state.
            case "range_dispatch":
                if (node.has("fallback")) return fromNode(node.getAsJsonObject("fallback"), depth + 1);
                String entries = type.equals("select") ? "cases" : "entries";
                if (node.has(entries) && !node.getAsJsonArray(entries).isEmpty()) {
                    return fromNode(node.getAsJsonArray(entries).get(0).getAsJsonObject().getAsJsonObject("model"), depth + 1);
                }
                return null;
            case "composite":
                for (JsonElement child : node.getAsJsonArray("models")) {
                    String sprite = fromNode(child.getAsJsonObject(), depth + 1);
                    if (sprite != null) return sprite;
                }
                return null;
            default:
                // Custom renderers and special models have no generally valid flat sprite.
                return null;
        }
    }

    private @Nullable String fromModel(NamespacedId model) throws IOException {
        Map<String, String> textures = textures(model, new HashSet<>());
        for (String key : List.of("layer0", "side", "front", "all", "top", "particle")) {
            String sprite = resolveTexture(textures, "#" + key);
            if (sprite != null) return sprite;
        }
        for (String value : textures.values()) {
            String sprite = resolveTexture(textures, value);
            if (sprite != null) return sprite;
        }
        return null;
    }

    private Map<String, String> textures(NamespacedId model, Set<NamespacedId> seen) throws IOException {
        Map<String, String> textures = new LinkedHashMap<>();
        if (seen.size() >= 32 || !seen.add(model)) throw new IllegalArgumentException("Cyclic or excessively deep model parents");
        JsonObject data = json(resource("models", model, ".json"));
        if (data == null) return textures;
        if (data.has("parent")) {
            textures.putAll(textures(identifier(data.get("parent").getAsString()), seen));
        }
        if (data.has("textures")) {
            data.getAsJsonObject("textures").entrySet().forEach(entry -> {
                JsonElement value = entry.getValue();
                textures.put(entry.getKey(), value.isJsonObject()
                        ? value.getAsJsonObject().get("sprite").getAsString() : value.getAsString());
            });
        }
        return textures;
    }

    private @Nullable String resolveTexture(Map<String, String> textures, String value) throws IOException {
        Set<String> seen = new HashSet<>();
        while (value != null && value.startsWith("#")) {
            if (!seen.add(value)) return null;
            value = textures.get(value.substring(1));
        }
        return value == null ? null : verifiedTexture(value);
    }

    private @Nullable String verifiedTexture(String value) throws IOException {
        NamespacedId texture = identifier(value);
        // These directories are stitched by vanilla for every namespace, including on modded clients.
        if (!texture.path().startsWith("item/") && !texture.path().startsWith("block/")) return null;
        try (InputStream input = resources.apply(resource("textures", texture, ".png"))) {
            return input == null ? null : texture.toString();
        }
    }

    private @Nullable JsonObject json(String path) throws IOException {
        InputStream input = resources.apply(path);
        if (input == null) return null;
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static NamespacedId identifier(String value) {
        NamespacedId id = NamespacedId.parse(value.contains(":") ? value : "minecraft:" + value);
        for (String part : id.path().split("/")) {
            if (part.equals("..") || part.equals(".")) throw new IllegalArgumentException("Invalid asset path");
        }
        return id;
    }

    private static String resource(String kind, NamespacedId id, String extension) {
        return "assets/" + id.namespace() + "/" + kind + "/" + id.path() + extension;
    }
}
