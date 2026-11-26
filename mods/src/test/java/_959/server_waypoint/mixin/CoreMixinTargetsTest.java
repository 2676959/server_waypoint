package _959.server_waypoint.mixin;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks the common mixin config this target's build produced against this Minecraft version. Mixin logs a
 * warning for every target class it can't load, even for a {@code @Pseudo} mixin, so a mixin whose targets exist
 * only in some versions must be compiled out and left out of the config everywhere else (issue #38). Mixins in
 * the map-mod sub-packages target optional mods and are left to {@code MapModMixinPlugin}.
 */
class CoreMixinTargetsTest {
    private static final String CORE_PACKAGE = "_959.server_waypoint.mixin";
    private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";

    @TestFactory
    List<DynamicTest> everyListedCoreMixinTargetsClassesOfThisMinecraftVersion() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String mixin : listedCoreMixins()) {
            tests.add(DynamicTest.dynamicTest(simpleName(mixin), () -> assertTargetsExist(mixin)));
        }
        assertFalse(tests.isEmpty(), MixinConfigFile.COMMON + " lists no core mixins");
        return tests;
    }

    @Test
    void everyCompiledCoreMixinIsListed() throws IOException, URISyntaxException {
        Set<String> listed = new TreeSet<>(listedCoreMixins());
        Set<String> unlisted = new TreeSet<>();
        for (Path mixinPackage : compiledMixinPackages(listed)) {
            try (Stream<Path> files = Files.list(mixinPackage)) {
                files.map(file -> file.getFileName().toString())
                        .filter(file -> file.endsWith(".class") && !file.contains("$"))
                        .map(file -> CORE_PACKAGE + "." + file.substring(0, file.length() - ".class".length()))
                        .filter(mixin -> !listed.contains(mixin))
                        .forEach(unlisted::add);
            }
        }
        assertEquals(Set.of(), unlisted, "compiled for this target but missing from " + MixinConfigFile.COMMON);
    }

    private static void assertTargetsExist(String mixinClass) throws IOException {
        ClassNode mixin = readClass(mixinClass.replace('.', '/'));
        if (mixin == null) {
            fail(mixinClass + " is listed in the config, but this target doesn't compile it");
        }
        List<String> targets = targets(mixin);
        assertFalse(targets.isEmpty(), mixinClass + " names no target class");
        for (String target : targets) {
            assertNotNull(resource(target + ".class"), simpleName(mixinClass) + " targets "
                    + target.replace('/', '.') + ", which this Minecraft version doesn't have");
        }
    }

    private static List<String> listedCoreMixins() throws IOException {
        List<String> core = new ArrayList<>();
        for (String mixin : MixinConfigFile.load(MixinConfigFile.COMMON).mixinClasses()) {
            if (mixin.substring(0, mixin.lastIndexOf('.')).equals(CORE_PACKAGE)) {
                core.add(mixin);
            }
        }
        return core;
    }

    /** The main-output directories holding the listed mixins; test classes share the package name elsewhere. */
    private static Set<Path> compiledMixinPackages(Set<String> listed) throws URISyntaxException {
        Set<Path> packages = new LinkedHashSet<>();
        for (String mixin : listed) {
            URL classFile = resource(mixin.replace('.', '/') + ".class");
            if (classFile == null) {
                continue;
            }
            assertEquals("file", classFile.getProtocol(), "expected compiled classes in a directory: " + classFile);
            packages.add(Path.of(classFile.toURI()).getParent());
        }
        assertFalse(packages.isEmpty(), "none of the listed core mixins is compiled for this target");
        return packages;
    }

    private static List<String> targets(ClassNode mixin) {
        List<String> targets = new ArrayList<>();
        for (List<AnnotationNode> annotations : List.of(nullToEmpty(mixin.invisibleAnnotations),
                nullToEmpty(mixin.visibleAnnotations))) {
            for (AnnotationNode annotation : annotations) {
                if (!annotation.desc.equals(MIXIN_DESCRIPTOR) || annotation.values == null) {
                    continue;
                }
                for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                    Object name = annotation.values.get(i);
                    List<?> values = (List<?>) annotation.values.get(i + 1);
                    if ("value".equals(name)) {
                        values.forEach(type -> targets.add(((Type) type).getInternalName()));
                    } else if ("targets".equals(name)) {
                        values.forEach(target -> targets.add(((String) target).replace('.', '/')));
                    }
                }
            }
        }
        return targets;
    }

    private static ClassNode readClass(String internalName) throws IOException {
        try (InputStream bytecode = CoreMixinTargetsTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (bytecode == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                    | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static URL resource(String path) {
        return CoreMixinTargetsTest.class.getClassLoader().getResource(path);
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
