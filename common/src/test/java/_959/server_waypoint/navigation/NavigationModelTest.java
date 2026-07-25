package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationModelTest {
    @Test
    void targetCopiesValuesOutOfMutableWaypoint() {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Village",
                "V",
                new WaypointPos(1, 2, 3),
                0x123456,
                0,
                false
        );
        NavigationTarget target = new NavigationTarget("minecraft:overworld", "towns", waypoint);

        waypoint.setName("Changed");
        waypoint.setPos(new WaypointPos(9, 9, 9));
        waypoint.setRgb(0xFFFFFF);

        assertEquals("Village", target.waypointName());
        assertEquals(new WaypointPos(1, 2, 3), target.position());
        assertEquals(0x123456, target.rgb());
    }

    @Test
    void sessionDefensivelyCopiesItsEnabledMethods() {
        EnumSet<NavigationMethod> methods = EnumSet.of(NavigationMethod.ACTIONBAR);
        NavigationSession session = new NavigationSession(
                UUID.randomUUID(),
                target(),
                methods,
                TextDisplayTransformation.defaultValue()
        );

        methods.add(NavigationMethod.BOSSBAR);

        assertEquals(Set.of(NavigationMethod.ACTIONBAR), session.enabledMethods());
        assertThrows(
                UnsupportedOperationException.class,
                () -> session.enabledMethods().add(NavigationMethod.MAP)
        );
    }

    @Test
    void textDisplayTransformationResolvesOffsetsAndScaleAgainstBaseValues() {
        TextDisplayTransformation transformation = new TextDisplayTransformation(
                new Vector3f(1.0F, 0.2F, -0.3F),
                new Vector3f(5.0F, 10.0F, 0.0F),
                new Vector3f(1.0F, 2.0F, 1.0F)
        );

        assertVectorEquals(
                new Vector3f(1.0F, -0.25F, -1.5F),
                transformation.resolvedTranslation()
        );
        assertVectorEquals(
                new Vector3f(-43.0F, 10.0F, 0.0F),
                transformation.resolvedRotation()
        );
        assertVectorEquals(
                new Vector3f(0.22F, 0.44F, 0.22F),
                transformation.resolvedScale()
        );
    }

    @Test
    void textDisplayEulerRotationConvertsResolvedDegreesToQuaternion() {
        TextDisplayTransformation transformation = TextDisplayTransformation.defaultValue()
                .withRotation(new Vector3f(138.0F, 0.0F, 0.0F));
        Quaternionf quaternion = transformation.rotationQuaternion();
        float halfSqrtTwo = (float) Math.sqrt(0.5D);

        assertEquals(halfSqrtTwo, quaternion.x(), 0.000001F);
        assertEquals(0.0F, quaternion.y(), 0.000001F);
        assertEquals(0.0F, quaternion.z(), 0.000001F);
        assertEquals(halfSqrtTwo, quaternion.w(), 0.000001F);
    }

    @Test
    void textDisplayTransformationDefensivelyCopiesJomlVectors() {
        Vector3f translation = new Vector3f(1.0F, 2.0F, 3.0F);
        TextDisplayTransformation transformation = new TextDisplayTransformation(
                translation,
                new Vector3f(),
                new Vector3f(1.0F)
        );

        translation.set(0.0F);
        transformation.translation().set(0.0F);

        assertEquals(new Vector3f(1.0F, 2.0F, 3.0F), transformation.translation());
    }

    @Test
    void textDisplayTransformationRejectsNonFiniteAndOutOfRangeValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextDisplayTransformation(
                        new Vector3f(Float.NaN, 0.0F, 0.0F),
                        new Vector3f(),
                        new Vector3f(1.0F)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextDisplayTransformation(
                        new Vector3f(17.0F, 0.0F, 0.0F),
                        new Vector3f(),
                        new Vector3f(1.0F)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextDisplayTransformation(
                        new Vector3f(),
                        new Vector3f(),
                        new Vector3f(5.0F, 1.0F, 1.0F)
                )
        );
    }

    @Test
    void methodIdsAndKindsRemainPlatformNeutral() {
        assertEquals(NavigationMethod.COMPASS, NavigationMethod.fromId("COMPASS").orElseThrow());
        assertTrue(NavigationMethod.COMPASS.ownsItem());
        assertFalse(NavigationMethod.COMPASS.isLiveDisplay());
        assertTrue(NavigationMethod.ACTIONBAR.isLiveDisplay());
        assertTrue(NavigationMethod.TEXT_DISPLAY.isLiveDisplay());
        assertEquals(
                Set.of(NavigationMethod.ACTIONBAR),
                NavigationMethod.builtInDefaultMethods()
        );
        assertEquals(
                EnumSet.of(
                        NavigationMethod.COMPASS,
                        NavigationMethod.MAP,
                        NavigationMethod.BOSSBAR,
                        NavigationMethod.ACTIONBAR,
                        NavigationMethod.TEXT_DISPLAY
                ),
                NavigationMethod.definedMethods()
        );
        assertTrue(NavigationMethod.definedMethods().contains(NavigationMethod.TEXT_DISPLAY));
    }

    private static NavigationTarget target() {
        return new NavigationTarget(
                "minecraft:overworld",
                "towns",
                "Village",
                new WaypointPos(1, 2, 3),
                0x123456
        );
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x(), actual.x(), 0.000001F);
        assertEquals(expected.y(), actual.y(), 0.000001F);
        assertEquals(expected.z(), actual.z(), 0.000001F);
    }
}
