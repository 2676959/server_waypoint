import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Regression for slash-form remapped targets and intentionally absent @Pseudo targets. */
public class TargetExtractorTest {
    public static void main(String[] args) throws Exception {
        Path fixture = Path.of(args[0]);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fixture))) {
            zip.putNextEntry(new ZipEntry("server_waypoint-test.mixins.json"));
            zip.write(
                    "{\"package\":\"test\",\"mixins\":[\"Required\",\"Optional\",\"Map\"]}"
                            .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            addMixin(zip, "Required", "com/example/Required", false);
            addMixin(zip, "Optional", "net.minecraft.OptionalAbsent", true);
            addMixin(zip, "Map", "xaero.common.gui.GuiWaypoints", false);
        }
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            ListMixinTargets.main(new String[] {fixture.toString()});
        } finally {
            System.setOut(previous);
        }
        String targets = captured.toString(StandardCharsets.UTF_8);
        if (!targets.lines().anyMatch(line -> line.equals("com.example.Required"))
                || !targets.lines().anyMatch(line -> line.equals("OPTIONAL net.minecraft.OptionalAbsent"))
                || !targets.lines().anyMatch(line -> line.equals("xaero.common.gui.GuiWaypoints"))) {
            throw new AssertionError("Incorrect runtime target extraction: " + targets);
        }
        captured.reset();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            ListMixinTargets.main(new String[] {fixture.toString(), "--optional-map-mods"});
        } finally {
            System.setOut(previous);
        }
        String coreTargets = captured.toString(StandardCharsets.UTF_8);
        if (!coreTargets.lines().anyMatch(line -> line.equals("com.example.Required"))
                || !coreTargets.lines().anyMatch(line -> line.equals("OPTIONAL xaero.common.gui.GuiWaypoints"))
                || coreTargets.lines().anyMatch(line -> line.equals("xaero.common.gui.GuiWaypoints"))) {
            throw new AssertionError("Incorrect core-only map classification: " + coreTargets);
        }
        System.out.println("PASS target normalization, @Pseudo and core-only map classification");
    }

    private static void addMixin(ZipOutputStream zip, String name, String target, boolean optional)
            throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17, Opcodes.ACC_PUBLIC, "test/" + name, null, "java/lang/Object", null);
        var mixin = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        var targets = mixin.visitArray("targets");
        targets.visit(null, target);
        targets.visitEnd();
        mixin.visitEnd();
        if (optional) {
            writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Pseudo;", false).visitEnd();
        }
        writer.visitEnd();
        zip.putNextEntry(new ZipEntry("test/" + name + ".class"));
        zip.write(writer.toByteArray());
        zip.closeEntry();
    }
}
