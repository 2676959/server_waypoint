package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAddTest {
    @Test
    void nothingIsPendingUntilAnAddBegins() {
        PendingAdd add = new PendingAdd();

        assertFalse(add.pending());
        assertFalse(add.expire(Long.MAX_VALUE));
        assertThrows(IllegalStateException.class, add::name);
    }

    @Test
    void itRemembersWhatIsBeingAdded() {
        PendingAdd add = new PendingAdd();

        add.begin("minecraft:overworld", "Base", "Home", 100);

        assertTrue(add.pending());
        assertEquals("minecraft:overworld", add.dimension());
        assertEquals("Base", add.list());
        assertEquals("Home", add.name());
    }

    @Test
    void itExpiresAtFiveSecondsAndNotBefore() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 100);

        assertEquals(5_000_000_000L, PendingAdd.TIMEOUT_NANOS);
        assertFalse(add.expire(100 + PendingAdd.TIMEOUT_NANOS - 1));
        assertTrue(add.pending());
        assertTrue(add.expire(100 + PendingAdd.TIMEOUT_NANOS));
        assertFalse(add.pending());
    }

    @Test
    void itExpiresOnlyOnce() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);

        assertTrue(add.expire(PendingAdd.TIMEOUT_NANOS));
        assertFalse(add.expire(PendingAdd.TIMEOUT_NANOS * 2));
    }

    @Test
    void theClockMayWrapAround() {
        PendingAdd add = new PendingAdd();
        long start = Long.MAX_VALUE - 1_000;
        add.begin("minecraft:overworld", "Base", "Home", start);

        assertFalse(add.expire(Long.MAX_VALUE));
        assertTrue(add.expire(start + PendingAdd.TIMEOUT_NANOS));
    }

    @Test
    void nothingIsPendingAfterClearing() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);

        add.clear();

        assertFalse(add.pending());
        assertFalse(add.expire(PendingAdd.TIMEOUT_NANOS * 10));
        assertThrows(IllegalStateException.class, add::list);
    }

    @Test
    void anotherAddCannotBeginWhileOneIsPending() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);

        assertThrows(IllegalStateException.class, () -> add.begin("minecraft:the_nether", "Base", "Fort", 1));
    }

    @Test
    void anAddCanBeginAgainAfterItEnds() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);
        add.clear();

        add.begin("minecraft:the_nether", "Base", "Fort", 10);

        assertEquals("Fort", add.name());
    }
}
