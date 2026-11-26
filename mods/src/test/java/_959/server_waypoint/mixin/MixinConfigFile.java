package _959.server_waypoint.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A mixin config as this target's build processed it, read from the test classpath.
 *
 * @param plugin       the config plugin class name, or {@code null} when the config names none
 * @param mixinClasses every listed mixin's fully qualified name, once, across all sides
 */
public record MixinConfigFile(String name, String plugin, List<String> mixinClasses) {
    public static final String COMMON = "server_waypoint-common.mixins.json";
    public static final String VOXELMAP = "server_waypoint-voxelmap.mixins.json";

    public static MixinConfigFile load(String name) throws IOException {
        JsonObject config;
        try (InputStream stream = MixinConfigFile.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException(name + " is not on the test classpath");
            }
            config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String mixinPackage = config.get("package").getAsString();
        Set<String> mixinClasses = new LinkedHashSet<>();
        for (String side : List.of("mixins", "client", "server")) {
            JsonArray names = config.getAsJsonArray(side);
            if (names == null) {
                continue;
            }
            for (JsonElement mixin : names) {
                mixinClasses.add(mixinPackage + "." + mixin.getAsString());
            }
        }
        JsonElement plugin = config.get("plugin");
        return new MixinConfigFile(name, plugin == null ? null : plugin.getAsString(), List.copyOf(mixinClasses));
    }
}
