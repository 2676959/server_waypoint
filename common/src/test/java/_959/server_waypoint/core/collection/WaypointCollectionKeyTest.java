package _959.server_waypoint.core.collection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaypointCollectionKeyTest {
    @Test
    void scopeAndOwnerCannotBeMixedOrOmitted() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        assertEquals(new WaypointCollectionKey(WaypointScope.SHARED, null), WaypointCollectionKey.shared());
        assertEquals(new WaypointCollectionKey(WaypointScope.PRIVATE, alice), WaypointCollectionKey.privateFor(alice));
        assertNotEquals(WaypointCollectionKey.privateFor(alice), WaypointCollectionKey.privateFor(bob));
        assertThrows(IllegalArgumentException.class, () -> new WaypointCollectionKey(WaypointScope.SHARED, alice));
        assertThrows(IllegalArgumentException.class, () -> new WaypointCollectionKey(WaypointScope.PRIVATE, null));
        assertThrows(NullPointerException.class, () -> new WaypointCollectionKey(null, null));
    }
}
