//? if voxelmap {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.mixin.MixinTargetAssertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static _959.server_waypoint.mixin.MixinTargetAssertions.findAnnotation;
import static _959.server_waypoint.mixin.MixinTargetAssertions.methods;
import static _959.server_waypoint.mixin.MixinTargetAssertions.readClass;
import static _959.server_waypoint.mixin.MixinTargetAssertions.values;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every VoxelMap mixin against the VoxelMap build on the test classpath, from the mixin side, so a
 * VoxelMap bump that breaks an injection fails the build instead of crashing the game. It reads class bytes
 * only and never bootstraps Minecraft.
 */
class VoxelMapMixinTargetsTest {
    private static final String MIXIN_CONFIG = "server_waypoint-voxelmap.mixins.json";
    private static final String ADD_WAYPOINT_SCREEN = "com/mamiyaotaru/voxelmap/gui/GuiAddWaypoint";
    private static final String EDIT_BOX = "net/minecraft/client/gui/components/EditBox";

    @TestFactory
    List<DynamicTest> everyMixinMatchesTheInstalledVoxelMap() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String mixinClass : MixinTargetAssertions.configuredMixins(MIXIN_CONFIG)) {
            tests.add(DynamicTest.dynamicTest(mixinClass,
                    () -> MixinTargetAssertions.assertInjectionPointsExist(mixinClass)));
        }
        assertFalse(tests.isEmpty(), MIXIN_CONFIG + " lists no mixins");
        return tests;
    }

    @Test
    void worldAccessorMatchesTheInstalledVoxelMapField() throws IOException {
        ClassNode target = readClass("com/mamiyaotaru/voxelmap/VoxelMap");
        ClassNode accessor = readClass("_959/server_waypoint/mixin/voxelmap/VoxelMapWorldAccessor");
        MethodNode getter = methods(accessor, "sw$getWorld", null).get(0);
        AnnotationNode annotation = findAnnotation(getter.invisibleAnnotations, getter.visibleAnnotations,
                "Lorg/spongepowered/asm/mixin/gen/Accessor;");
        assertNotNull(annotation);
        assertEquals("world", values(annotation).get("value"));
        String descriptor = Type.getReturnType(getter.desc).getDescriptor();
        assertTrue(target.fields.stream().anyMatch(field -> field.name.equals("world")
                && field.desc.equals(descriptor)), "VoxelMap's loaded world field no longer matches the accessor");
    }

    @Test
    void addWaypointNameRedirectReadsTheNameField() throws IOException {
        MethodNode acceptWaypoint = methods(readClass(ADD_WAYPOINT_SCREEN), "acceptWaypoint", null).stream()
                .findFirst().orElseThrow(() -> new AssertionError("GuiAddWaypoint.acceptWaypoint is missing"));
        MethodInsnNode firstGetValue = null;
        for (AbstractInsnNode instruction : acceptWaypoint.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(EDIT_BOX)
                    && call.name.equals("getValue") && call.desc.equals("()Ljava/lang/String;")) {
                firstGetValue = call;
                break;
            }
        }
        assertNotNull(firstGetValue, "GuiAddWaypoint.acceptWaypoint no longer calls EditBox.getValue()");
        AbstractInsnNode receiver = previousInstruction(firstGetValue);
        FieldInsnNode field = assertInstanceOf(FieldInsnNode.class, receiver,
                "The first EditBox.getValue() call in acceptWaypoint does not read an edit box field");
        assertEquals(Opcodes.GETFIELD, field.getOpcode());
        assertEquals("waypointName", field.name,
                "The first EditBox.getValue() call in acceptWaypoint no longer reads the waypoint name");
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }
}
//?}
