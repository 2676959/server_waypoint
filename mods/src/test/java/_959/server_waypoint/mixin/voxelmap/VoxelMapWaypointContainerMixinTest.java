//? if voxelmap {
package _959.server_waypoint.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mamiyaotaru.voxelmap.util.WaypointContainer;
import java.io.IOException;
import java.util.Arrays;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelMapWaypointContainerMixinTest {
    @Test
    void labelHookCoversTheNameReadInTheInstalledVoxelMapRenderer() throws IOException {
        ClassNode renderer = new ClassNode();
        try (var bytecode = WaypointContainer.class.getResourceAsStream("WaypointContainer.class")) {
            assertNotNull(bytecode);
            new ClassReader(bytecode).accept(renderer, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var hook = Arrays.stream(VoxelMapWaypointContainerMixin.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Redirect.class))
                .findFirst().orElseThrow().getAnnotation(Redirect.class);
        int reads = 0;
        for (var method : renderer.methods) {
            if (!method.name.equals("renderWaypoints") && !method.name.equals("renderSign")) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETFIELD
                        && field.owner.equals("com/mamiyaotaru/voxelmap/util/Waypoint")
                        && field.name.equals("name") && field.desc.equals("Ljava/lang/String;")) {
                    reads++;
                    assertTrue(Arrays.asList(hook.method()).contains(method.name),
                            "In-world waypoint names are read in " + method.name + ", outside the label hook");
                    assertEquals("FIELD", hook.at().value());
                    assertEquals("L" + field.owner + ";" + field.name + ":" + field.desc, hook.at().target());
                    assertEquals(Opcodes.GETFIELD, hook.at().opcode());
                }
            }
        }
        assertTrue(reads > 0, "Expected an in-world waypoint name read in VoxelMap");
    }

    @Test
    void serverLabelDecodesTheNameWithoutChangingTheStoredIdentity() throws ReflectiveOperationException {
        Waypoint waypoint = waypoint("sw\u241FBases\u241FHome");

        assertEquals("Home", label(waypoint));
        assertEquals("sw\u241FBases\u241FHome", waypoint.name);
    }

    @Test
    void localLabelKeepsItsOriginalName() throws ReflectiveOperationException {
        assertEquals("Local home", label(waypoint("Local home")));
    }

    private static String label(Waypoint waypoint) throws ReflectiveOperationException {
        var hook = Arrays.stream(VoxelMapWaypointContainerMixin.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Redirect.class))
                .findFirst().orElseThrow();
        hook.setAccessible(true);
        return (String) hook.invoke(new VoxelMapWaypointContainerMixin(), waypoint);
    }

    private static Waypoint waypoint(String name) {
        return new Waypoint(name, 10, 20, 64, true, 1.0F, 0.5F, 0.25F,
                "", "", new TreeSet<>());
    }
}
//?}
