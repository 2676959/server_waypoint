package _959.server_waypoint.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks mixins against the classes on the test classpath, from the mixin side, so a game, loader or mod update
 * that moves a required injection point fails the build instead of crashing the game. It reads class bytes only
 * and never bootstraps Minecraft.
 */
public final class MixinTargetAssertions {
    private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final List<String> INJECTOR_PACKAGES = List.of(
            "Lorg/spongepowered/asm/mixin/injection/",
            "Lcom/llamalad7/mixinextras/injector/");

    private MixinTargetAssertions() {
    }

    /** Lists the mixins a mixin config on the test classpath declares, as binary class names. */
    public static List<String> configuredMixins(String config) throws IOException {
        JsonObject json;
        try (InputStream stream = resource(config)) {
            assertNotNull(stream, config + " is not on the test classpath");
            json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String mixinPackage = json.get("package").getAsString();
        List<String> mixins = new ArrayList<>();
        for (String side : List.of("mixins", "client", "server")) {
            JsonArray names = json.getAsJsonArray(side);
            if (names == null) {
                continue;
            }
            for (JsonElement name : names) {
                mixins.add(mixinPackage + "." + name.getAsString());
            }
        }
        return mixins;
    }

    /**
     * Asserts that every required injector of the mixin finds its method, and each of its INVOKE and FIELD points
     * there, in every target class.
     *
     * @return how many injection points were checked, so a caller can reject a mixin that checks nothing
     */
    public static int assertInjectionPointsExist(String mixinClass) throws IOException {
        ClassNode mixin = readClass(mixinClass.replace('.', '/'));
        AnnotationNode mixinAnnotation = findAnnotation(mixin.invisibleAnnotations, mixin.visibleAnnotations,
                MIXIN_DESCRIPTOR);
        assertNotNull(mixinAnnotation, mixinClass + " has no @Mixin annotation");
        List<ClassNode> targets = new ArrayList<>();
        for (String target : mixinTargets(mixinAnnotation)) {
            targets.add(readClass(target));
        }
        assertFalse(targets.isEmpty(), mixinClass + " names no target class");

        int checked = 0;
        for (MethodNode handler : mixin.methods) {
            for (AnnotationNode injector : injectors(handler)) {
                Map<String, Object> values = values(injector);
                boolean required = !Integer.valueOf(0).equals(values.get("require"));
                List<AnnotationNode> ats = atAnnotations(values.get("at"));
                for (Object reference : list(values.get("method"))) {
                    String method = (String) reference;
                    String where = mixinClass + "." + handler.name + " -> " + method;
                    for (ClassNode target : targets) {
                        List<MethodNode> matches = methodsForReference(target, method);
                        if (matches.isEmpty()) {
                            assertFalse(required, where + ": " + target.name + " has no such method");
                            continue;
                        }
                        for (AnnotationNode at : ats) {
                            if (checkAt(where, target, matches, values(at), required)) {
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        return checked;
    }

    public static ClassNode readClass(String internalName) throws IOException {
        ClassNode node = new ClassNode();
        try (InputStream bytecode = resource(internalName + ".class")) {
            if (bytecode == null) {
                fail(internalName.replace('/', '.') + " is not on the test classpath");
            }
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }

    /** Lists the target's methods with this name, and with this descriptor unless it is {@code null}. */
    public static List<MethodNode> methods(ClassNode target, String name, String descriptor) {
        return target.methods.stream()
                .filter(method -> method.name.equals(name))
                .filter(method -> descriptor == null || method.desc.equals(descriptor))
                .collect(Collectors.toList());
    }

    public static AnnotationNode findAnnotation(List<AnnotationNode> first, List<AnnotationNode> second,
                                                String descriptor) {
        for (List<AnnotationNode> annotations : List.of(nullToEmpty(first), nullToEmpty(second))) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(descriptor)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    public static Map<String, Object> values(AnnotationNode annotation) {
        Map<String, Object> values = new HashMap<>();
        if (annotation.values != null) {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
            }
        }
        return values;
    }

    /** Checks one {@code @At}; returns whether it named an INVOKE or FIELD point that was checked. */
    private static boolean checkAt(String where, ClassNode target, List<MethodNode> methods,
                                   Map<String, Object> at, boolean required) {
        String kind = (String) at.get("value");
        String reference = (String) at.get("target");
        if (!required || reference == null) {
            return false;
        }
        // A bare name also matches synthetic bridge overloads, so one overload per reference must match.
        if ("FIELD".equals(kind)) {
            FieldReference field = FieldReference.parse(reference);
            Integer opcode = (Integer) at.get("opcode");
            assertTrue(methods.stream().anyMatch(method -> containsField(method, field, opcode)),
                    where + ": " + target.name + " no longer accesses " + reference + " there");
            return true;
        }
        if ("INVOKE".equals(kind)) {
            MethodReference call = MethodReference.parse(reference);
            assertTrue(methods.stream().anyMatch(method -> containsCall(method, call)),
                    where + ": " + target.name + " no longer calls " + reference + " there");
            return true;
        }
        return false;
    }

    private static boolean containsField(MethodNode method, FieldReference field, Integer opcode) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode access
                    && (access.getOpcode() == Opcodes.GETFIELD || access.getOpcode() == Opcodes.PUTFIELD)
                    && (opcode == null || access.getOpcode() == opcode)
                    && access.owner.equals(field.owner())
                    && access.name.equals(field.name())
                    && access.desc.equals(field.desc())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCall(MethodNode method, MethodReference call) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invoke
                    && invoke.owner.equals(call.owner())
                    && invoke.name.equals(call.name())
                    && invoke.desc.equals(call.desc())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> mixinTargets(AnnotationNode mixin) {
        Map<String, Object> values = values(mixin);
        List<String> targets = new ArrayList<>();
        for (Object type : list(values.get("value"))) {
            targets.add(((Type) type).getInternalName());
        }
        for (Object name : list(values.get("targets"))) {
            targets.add(((String) name).replace('.', '/'));
        }
        return targets;
    }

    private static List<AnnotationNode> injectors(MethodNode handler) {
        List<AnnotationNode> injectors = new ArrayList<>();
        for (List<AnnotationNode> annotations : List.of(nullToEmpty(handler.visibleAnnotations),
                nullToEmpty(handler.invisibleAnnotations))) {
            for (AnnotationNode annotation : annotations) {
                boolean injectorPackage = INJECTOR_PACKAGES.stream().anyMatch(annotation.desc::startsWith);
                if (injectorPackage && values(annotation).containsKey("method")) {
                    injectors.add(annotation);
                }
            }
        }
        return injectors;
    }

    private static List<AnnotationNode> atAnnotations(Object at) {
        return list(at).stream().map(AnnotationNode.class::cast).collect(Collectors.toList());
    }

    /** A mixin method reference is either a bare name or a name followed by its descriptor. */
    private static List<MethodNode> methodsForReference(ClassNode target, String reference) {
        int descriptorStart = reference.indexOf('(');
        if (descriptorStart < 0) {
            return methods(target, reference, null);
        }
        return methods(target, reference.substring(0, descriptorStart), reference.substring(descriptorStart));
    }

    private static InputStream resource(String path) {
        return MixinTargetAssertions.class.getClassLoader().getResourceAsStream(path);
    }

    private static List<?> list(Object value) {
        if (value == null) {
            return List.of();
        }
        return value instanceof List<?> values ? values : List.of(value);
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record FieldReference(String owner, String name, String desc) {
        /** Parses {@code Lowner;name:desc}. */
        static FieldReference parse(String reference) {
            int ownerEnd = reference.indexOf(';');
            int nameEnd = reference.indexOf(':', ownerEnd);
            return new FieldReference(reference.substring(1, ownerEnd),
                    reference.substring(ownerEnd + 1, nameEnd), reference.substring(nameEnd + 1));
        }
    }

    private record MethodReference(String owner, String name, String desc) {
        /** Parses {@code Lowner;name(args)ret}. */
        static MethodReference parse(String reference) {
            int ownerEnd = reference.indexOf(';');
            int descriptorStart = reference.indexOf('(', ownerEnd);
            return new MethodReference(reference.substring(1, ownerEnd),
                    reference.substring(ownerEnd + 1, descriptorStart), reference.substring(descriptorStart));
        }
    }
}
