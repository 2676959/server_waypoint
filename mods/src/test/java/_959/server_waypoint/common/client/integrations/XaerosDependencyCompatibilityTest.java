package _959.server_waypoint.common.client.integrations;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaerosDependencyCompatibilityTest {
    private static final List<String> MAP_IDS = List.of("xaerominimap", "xaeroworldmap", "forge");

    @Test
    void pinnedMinimapAndWorldMapSatisfyEachOthersDeclaredMinimumVersions() throws Exception {
        ModMetadata minimap = metadata(xaero.common.minimap.waypoints.Waypoint.class);
        ModMetadata worldMap = metadata(xaero.map.mods.gui.Waypoint.class);

        accepts(minimap, "xaeroworldmap", worldMap);
        accepts(worldMap, "xaerominimap", minimap);
    }

    //? if forge {
    /*    @Test
    void pinnedForgeSatisfiesMapModsDeclaredMinimumVersions() throws Exception {
        ModMetadata forge = new ModMetadata(System.getProperty("server_waypoint.test.forge_loader"), Map.of());
        assertNotNull(forge.version(), "Forge version must be supplied by the build");
        accepts(metadata(xaero.common.minimap.waypoints.Waypoint.class), "forge", forge);
        accepts(metadata(xaero.map.mods.gui.Waypoint.class), "forge", forge);
    }
    *///?}

    private static void accepts(ModMetadata owner, String dependencyId, ModMetadata dependency) {
        String minimum = owner.minimumVersions().get(dependencyId);
        if (minimum != null) {
            assertTrue(compare(dependency.version(), minimum) >= 0,
                    owner.version() + " requires " + dependencyId + " >= " + minimum
                            + ", but the pinned build is " + dependency.version());
        }
    }

    private static ModMetadata metadata(Class<?> dependencyClass) throws Exception {
        Path source = Path.of(dependencyClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile jar = new JarFile(source.toFile())) {
            Map<String, String> minimums = new HashMap<>();
            var fabric = jar.getJarEntry("fabric.mod.json");
            if (fabric != null) {
                JsonObject metadata;
                try (var reader = new InputStreamReader(jar.getInputStream(fabric), StandardCharsets.UTF_8)) {
                    metadata = JsonParser.parseReader(reader).getAsJsonObject();
                }
                for (String id : MAP_IDS) {
                    for (String section : List.of("depends", "breaks")) {
                        JsonObject declarations = metadata.getAsJsonObject(section);
                        if (declarations == null || !declarations.has(id)) {
                            continue;
                        }
                        String range = declarations.get(id).getAsString();
                        String operator = section.equals("breaks") ? "<" : ">=";
                        assertTrue(range.startsWith(operator), "Unsupported declared version range: " + range);
                        minimums.put(id, range.substring(operator.length()));
                    }
                }
                return new ModMetadata(metadata.get("version").getAsString(), minimums);
            }
            var toml = jar.getJarEntry("META-INF/neoforge.mods.toml");
            if (toml == null) {
                toml = jar.getJarEntry("META-INF/mods.toml");
            }
            assertNotNull(toml, "Missing loader metadata in " + source);
            String metadata;
            try (var input = jar.getInputStream(toml)) {
                metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String section : metadata.split("(?m)^\\[\\[dependencies\\.[^\\]]+\\]\\][^\\r\\n]*$")) {
                String id = field(section, "modId");
                if (id != null && MAP_IDS.contains(id)) {
                    String range = field(section, "versionRange");
                    if (range == null) {
                        continue;
                    }
                    var matcher = Pattern.compile("\\[([0-9.]+),[0-9.]*\\)").matcher(range.replace(" ", ""));
                    assertTrue(matcher.matches(), "Unsupported declared version range: " + range);
                    minimums.put(id, matcher.group(1));
                }
            }
            return new ModMetadata(field(metadata, "version"), minimums);
        }
    }

    private static String field(String section, String name) {
        var matcher = Pattern.compile("(?m)^\\s*" + name + "\\s*=\\s*\"([^\"]+)\"").matcher(section);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int compare(String actual, String minimum) {
        String[] actualParts = actual.split("\\.");
        String[] minimumParts = minimum.split("\\.");
        for (int i = 0; i < Math.max(actualParts.length, minimumParts.length); i++) {
            int result = Integer.compare(i < actualParts.length ? Integer.parseInt(actualParts[i]) : 0,
                    i < minimumParts.length ? Integer.parseInt(minimumParts[i]) : 0);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private record ModMetadata(String version, Map<String, String> minimumVersions) {
    }
}
