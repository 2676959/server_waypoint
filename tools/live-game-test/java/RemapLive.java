import net.fabricmc.tinyremapper.*;

import java.nio.file.*;

public class RemapLive {
    static IMappingProvider provider(String path, String dst) throws Exception {
        if (path.endsWith(".tsrg")) {
            net.fabricmc.mappingio.tree.MemoryMappingTree tree =
                    new net.fabricmc.mappingio.tree.MemoryMappingTree();
            net.fabricmc.mappingio.MappingReader.read(Path.of(path), tree);
            fill(tree);
            return out -> {
                for (var c : tree.getClasses()) {
                    out.acceptClass(c.getSrcName(), c.getDstName(0));
                    for (var field : new java.util.ArrayList<>(c.getFields()))
                        if (field.getSrcDesc() != null)
                            out.acceptField(
                                    new IMappingProvider.Member(
                                            c.getSrcName(), field.getSrcName(), field.getSrcDesc()),
                                    field.getDstName(0));
                    for (var m : c.getMethods())
                        if (m.getSrcDesc() != null)
                            out.acceptMethod(
                                    new IMappingProvider.Member(
                                            c.getSrcName(), m.getSrcName(), m.getSrcDesc()),
                                    m.getDstName(0));
                }
            };
        }
        return TinyUtils.createTinyMappingProvider(Path.of(path), "named", dst);
    }

    static String classpath;

    static void fill(net.fabricmc.mappingio.tree.MemoryMappingTree tree) throws Exception {
        for (String entry : Files.readString(Path.of(classpath)).trim().split(":")) {
            Path p = Path.of(entry);
            if (!entry.endsWith(".jar")) continue;
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(p.toFile())) {
                var en = z.entries();
                while (en.hasMoreElements()) {
                    var e = en.nextElement();
                    if (!e.getName().endsWith(".class")) continue;
                    byte[] bytes = z.getInputStream(e).readAllBytes();
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    var c = tree.getClass(cr.getClassName());
                    if (c == null) continue;
                    cr.accept(
                            new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                                public org.objectweb.asm.FieldVisitor visitField(
                                        int access,
                                        String name,
                                        String desc,
                                        String signature,
                                        Object value) {
                                    for (var field : new java.util.ArrayList<>(c.getFields()))
                                        if (field.getSrcName().equals(name)
                                                && field.getSrcDesc() == null)
                                            field.setSrcDesc(desc);
                                    return null;
                                }
                            },
                            org.objectweb.asm.ClassReader.SKIP_CODE
                                    | org.objectweb.asm.ClassReader.SKIP_DEBUG
                                    | org.objectweb.asm.ClassReader.SKIP_FRAMES);
                }
            }
        }
    }

    public static void main(String[] a) throws Exception {
        classpath = a[4];
        TinyRemapper r =
                TinyRemapper.newRemapper()
                        .withMappings(provider(a[0], a[1]))
                        .threads(2)
                        .ignoreConflicts(true)
                        .build();
        try (OutputConsumerPath o = new OutputConsumerPath(Path.of(a[3]))) {
            r.readClassPath(
                    java.util.Arrays.stream(Files.readString(Path.of(a[4])).trim().split(":"))
                            .map(Path::of)
                            .filter(Files::exists)
                            .toArray(Path[]::new));
            r.readInputs(Path.of(a[2]));
            r.apply(o);
        } finally {
            r.finish();
        }
    }
}
