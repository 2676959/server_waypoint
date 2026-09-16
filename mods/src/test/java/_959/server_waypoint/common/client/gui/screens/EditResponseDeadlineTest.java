package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditResponseDeadlineTest {
    @Test
    void expiresOnlyTheActiveRequestAtItsDeadline() {
        EditResponseDeadline deadline = new EditResponseDeadline();

        deadline.begin(7, 100);

        assertTrue(deadline.pending());
        assertFalse(deadline.expire(100 + EditResponseDeadline.TIMEOUT_NANOS - 1));
        assertTrue(deadline.expire(100 + EditResponseDeadline.TIMEOUT_NANOS));
        assertFalse(deadline.pending());
    }

    @Test
    void staleResultCannotClearReplacementRequest() {
        EditResponseDeadline deadline = new EditResponseDeadline();
        deadline.begin(7, 100);
        assertTrue(deadline.clearIfMatches(7));
        deadline.begin(8, 200);

        assertFalse(deadline.clearIfMatches(7));
        assertTrue(deadline.pending());
        assertTrue(deadline.clearIfMatches(8));
        assertFalse(deadline.pending());
    }
}
