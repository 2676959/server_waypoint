package _959.server_waypoint.core.restore;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointRestoreRegistryTest {
    @Test
    void lookupIsOwnerScopedDetachedAndSingleUse() {
        MutableClock clock = new MutableClock();
        WaypointRestoreRegistry<String> registry = registry(clock, 2, 4);
        SimpleWaypoint original = waypoint("id");

        String token = registry.register("owner", "dimension", "list", original);

        WaypointRestoreRegistry.Entry entry = registry.lookup("owner", token).orElseThrow();
        assertEquals("id", entry.waypoint().name());
        assertTrue(entry.waypoint().hasDisplayNameOverride());
        assertEquals(List.of("keyword"), entry.waypoint().keywords());
        assertFalse(entry.waypoint() == registry.lookup("owner", token).orElseThrow().waypoint());
        assertFalse(registry.lookup("other", token).isPresent());
        assertTrue(registry.consume("owner", token));
        assertFalse(registry.consume("owner", token));
        assertFalse(registry.lookup("owner", token).isPresent());
    }

    @Test
    void expiryAndCapacityEvictOldestEntries() {
        MutableClock clock = new MutableClock();
        WaypointRestoreRegistry<String> registry = registry(clock, 2, 3);

        String first = registry.register("one", "d", "l", waypoint("first"));
        String second = registry.register("one", "d", "l", waypoint("second"));
        String third = registry.register("one", "d", "l", waypoint("third"));
        assertFalse(registry.lookup("one", first).isPresent());
        assertTrue(registry.lookup("one", second).isPresent());
        assertTrue(registry.lookup("one", third).isPresent());

        registry.register("two", "d", "l", waypoint("fourth"));
        registry.register("three", "d", "l", waypoint("fifth"));
        assertEquals(3, registry.size());
        assertFalse(registry.lookup("one", second).isPresent());

        clock.advance(Duration.ofMinutes(6));
        assertEquals(0, registry.size());
    }

    @Test
    void failedRestoreCanLeaveTokenAvailable() {
        MutableClock clock = new MutableClock();
        WaypointRestoreRegistry<String> registry = registry(clock, 2, 3);
        String token = registry.register("owner", "d", "missing-list", waypoint("occupied"));

        registry.lookup("owner", token).orElseThrow();

        assertTrue(registry.lookup("owner", token).isPresent());
    }

    private static WaypointRestoreRegistry<String> registry(
            Clock clock,
            int ownerCapacity,
            int globalCapacity
    ) {
        return new WaypointRestoreRegistry<>(
                clock,
                Duration.ofMinutes(5),
                ownerCapacity,
                globalCapacity,
                new SecureRandom()
        );
    }

    private static SimpleWaypoint waypoint(String identifier) {
        return new SimpleWaypoint(
                identifier,
                "{\"text\":\"Display\"}",
                "D",
                new WaypointPos(1, 2, 3),
                0x123456,
                90,
                false,
                List.of("keyword"),
                "{\"text\":\"Description\"}"
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-04T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }
    }
}
