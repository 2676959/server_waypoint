import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Extracts runtime target names directly from the final production JAR, after remapping. */
public class ListMixinTargets {
    public static void main(String[] args) throws Exception {
        boolean optionalMapMods = args.length == 2 && args[1].equals("--optional-map-mods");
        if (args.length != 1 && !optionalMapMods) {
            throw new IllegalArgumentException("Use JAR [--optional-map-mods]");
        }
        Set<String> targets = new TreeSet<>();
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entry :
                    jar.stream()
                            .filter(
                                    value ->
                                            value.getName().startsWith("server_waypoint-")
                                                    && value.getName().endsWith(".mixins.json"))
                            .toList()) {
                JsonObject config;
                try (InputStream input = jar.getInputStream(entry)) {
                    config = JsonParser.parseReader(new InputStreamReader(input)).getAsJsonObject();
                }
                for (String side : List.of("mixins", "client")) {
                    if (!config.has(side)) {
                        continue;
                    }
                    for (JsonElement mixin : config.getAsJsonArray(side)) {
                        String name =
                                config.get("package").getAsString() + "." + mixin.getAsString();
                        var bytes = jar.getEntry(name.replace('.', '/') + ".class");
                        if (bytes == null) {
                            throw new IllegalStateException("Configured mixin is absent: " + name);
                        }
                        ClassNode node = new ClassNode();
                        try (InputStream input = jar.getInputStream(bytes)) {
                            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
                        }
                        if (node.invisibleAnnotations == null) {
                            throw new IllegalStateException("No Mixin annotation: " + name);
                        }
                        boolean optional =
                                node.invisibleAnnotations.stream()
                                        .anyMatch(
                                                annotation ->
                                                        annotation.desc.equals(
                                                                "Lorg/spongepowered/asm/mixin/Pseudo;"));
                        if (node.visibleAnnotations != null) {
                            optional |=
                                    node.visibleAnnotations.stream()
                                            .anyMatch(
                                                    annotation ->
                                                            annotation.desc.equals(
                                                                    "Lorg/spongepowered/asm/mixin/Pseudo;"));
                        }
                        for (AnnotationNode annotation : node.invisibleAnnotations) {
                            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                                continue;
                            }
                            for (int index = 0; index < annotation.values.size(); index += 2) {
                                String key = (String) annotation.values.get(index);
                                if (key.equals("value") || key.equals("targets")) {
                                    for (Object value :
                                            (List<?>) annotation.values.get(index + 1)) {
                                        String target =
                                                value instanceof Type type
                                                        ? type.getClassName()
                                                        : (String) value;
                                        target = target.replace('/', '.');
                                        boolean optionalTarget = optional || optionalMapMods
                                                && (target.startsWith("xaero.")
                                                        || target.startsWith("com.mamiyaotaru.voxelmap."));
                                        targets.add((optionalTarget ? "OPTIONAL " : "") + target);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            throw new IllegalStateException("Production JAR contains no configured mixin targets");
        }
        targets.forEach(System.out::println);
    }
}
