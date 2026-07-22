package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationMathTest {
    @Test
    void wrapsDegreesAcrossTheNegativePositiveBoundary() {
        assertEquals(-179.0D, NavigationMath.wrapDegrees(181.0D));
        assertEquals(179.0D, NavigationMath.wrapDegrees(-181.0D));
        assertEquals(-180.0D, NavigationMath.wrapDegrees(180.0D));
        assertEquals(-180.0D, NavigationMath.wrapDegrees(540.0D));
        assertEquals(0.0D, NavigationMath.wrapDegrees(720.0D));
    }

    @Test
    void signedTurnAngleUsesNegativeForLeftAndPositiveForRight() {
        NavigationSnapshot left = NavigationMath.snapshot(
                "minecraft:overworld",
                0.0D,
                64.0D,
                0.0D,
                0.0D,
                target("minecraft:overworld", 10, 64, 0)
        );
        NavigationSnapshot right = NavigationMath.snapshot(
                "minecraft:overworld",
                0.0D,
                64.0D,
                0.0D,
                0.0D,
                target("minecraft:overworld", -10, 64, 0)
        );

        assertEquals(-90.0D, left.targetYaw());
        assertEquals(-90.0D, left.signedTurnAngle());
        assertEquals(90.0D, right.targetYaw());
        assertEquals(90.0D, right.signedTurnAngle());
    }

    @Test
    void facingProgressApproachesOneAsTurnAngleApproachesZero() {
        assertEquals(1.0F, NavigationMath.facingProgress(0.0D));
        assertEquals(0.5F, NavigationMath.facingProgress(-90.0D));
        assertEquals(0.5F, NavigationMath.facingProgress(90.0D));
        assertEquals(0.0F, NavigationMath.facingProgress(180.0D));
    }

    @Test
    void snapshotIncludesHorizontalAndSignedVerticalDistance() {
        NavigationSnapshot snapshot = NavigationMath.snapshot(
                "minecraft:overworld",
                0.0D,
                1.0D,
                0.0D,
                0.0D,
                target("minecraft:overworld", 3, 5, 4)
        );

        assertTrue(snapshot.inTargetDimension());
        assertEquals(5.0D, snapshot.horizontalDistance());
        assertEquals(4.0D, snapshot.verticalDifference());
    }

    @Test
    void wrongDimensionSnapshotDoesNotExposeMisleadingDirectionOrDistance() {
        NavigationSnapshot snapshot = NavigationMath.snapshot(
                "minecraft:the_nether",
                0.0D,
                64.0D,
                0.0D,
                0.0D,
                target("minecraft:overworld", 100, 80, 100)
        );

        assertFalse(snapshot.inTargetDimension());
        assertTrue(Double.isNaN(snapshot.targetYaw()));
        assertTrue(Double.isNaN(snapshot.signedTurnAngle()));
        assertTrue(Double.isNaN(snapshot.horizontalDistance()));
        assertTrue(Double.isNaN(snapshot.verticalDifference()));
        assertEquals(0.0F, snapshot.facingProgress());
    }

    private static NavigationTarget target(String dimension, int x, int y, int z) {
        return new NavigationTarget(
                dimension,
                "test-list",
                "test-waypoint",
                new WaypointPos(x, y, z),
                0x39C5BB
        );
    }
}
