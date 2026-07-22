package _959.server_waypoint.common.server.navigation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationMapLeasePoolTest {
    @Test
    void activeMapIsNeverSelectedForReuse() {
        NavigationMapLeasePool<String> pool = new NavigationMapLeasePool<>();
        UUID player = UUID.randomUUID();
        pool.remember("active", 1);
        pool.remember("inactive", 2);
        pool.activate(player, "active");

        NavigationMapLeasePool.ReusableEntry<String> reusable = pool.removeOldestInactive();

        assertEquals("inactive", reusable.key());
        assertEquals(2, reusable.mapId());
        assertEquals(1, pool.idFor("active"));
        assertTrue(pool.isActive("active"));
    }

    @Test
    void retargetMakesPreviousMapReusableAfterNewMapIsActivated() {
        NavigationMapLeasePool<String> pool = new NavigationMapLeasePool<>();
        UUID player = UUID.randomUUID();
        pool.remember("old", 1);
        pool.activate(player, "old");
        assertNull(pool.removeOldestInactive());

        pool.remember("new", 2);
        pool.activate(player, "new");

        NavigationMapLeasePool.ReusableEntry<String> reusable = pool.removeOldestInactive();
        assertEquals("old", reusable.key());
        assertEquals(1, reusable.mapId());
        assertTrue(pool.isActive("new"));
        assertFalse(pool.isActive("old"));
    }

    @Test
    void sharedMapRemainsActiveUntilEveryPlayerReleasesIt() {
        NavigationMapLeasePool<String> pool = new NavigationMapLeasePool<>();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        pool.remember("shared", 3);
        pool.activate(firstPlayer, "shared");
        pool.activate(secondPlayer, "shared");

        pool.release(firstPlayer);
        assertTrue(pool.isActive("shared"));

        pool.release(secondPlayer);
        assertFalse(pool.isActive("shared"));
        assertEquals("shared", pool.removeOldestInactive().key());
    }
}
