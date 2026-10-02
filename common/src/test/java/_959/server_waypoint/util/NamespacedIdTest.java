package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamespacedIdTest {
    @Test
    void parsesAndFormatsCanonicalIds() {
        assertEquals(new NamespacedId("minecraft", "diamond"), NamespacedId.parse("minecraft:diamond"));
        assertEquals("examplemod:blue_gem", NamespacedId.parse("examplemod:blue_gem").toString());
        assertEquals("other", NamespacedId.parse("example:other").path());
    }

    @Test
    void rejectsMalformedIdsAndOversizedWaypointSelection() {
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("Minecraft:Diamond"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("voxelmap:bad%icon"));
        assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> new NamespacedId("Minecraft", "diamond"));
        assertThrows(IllegalArgumentException.class, () -> WaypointIconPolicy.validate(NamespacedId.parse("a:" + "x".repeat(255))));
    }
}
