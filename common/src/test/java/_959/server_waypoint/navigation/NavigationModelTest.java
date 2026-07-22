package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
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
        NavigationSession session = new NavigationSession(UUID.randomUUID(), target(), methods);

        methods.add(NavigationMethod.BOSSBAR);

        assertEquals(Set.of(NavigationMethod.ACTIONBAR), session.enabledMethods());
        assertThrows(
                UnsupportedOperationException.class,
                () -> session.enabledMethods().add(NavigationMethod.MAP)
        );
    }

    @Test
    void methodIdsAndKindsRemainPlatformNeutral() {
        assertEquals(NavigationMethod.COMPASS, NavigationMethod.fromId("COMPASS").orElseThrow());
        assertTrue(NavigationMethod.COMPASS.ownsItem());
        assertFalse(NavigationMethod.COMPASS.isLiveDisplay());
        assertTrue(NavigationMethod.ACTIONBAR.isLiveDisplay());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), NavigationMethod.defaultSelection());
        assertEquals(EnumSet.allOf(NavigationMethod.class), NavigationMethod.allMethods());
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
}
