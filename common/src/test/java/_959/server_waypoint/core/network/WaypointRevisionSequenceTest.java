package _959.server_waypoint.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WaypointRevisionSequenceTest {
    @Test
    void appliesOnlyTheExpectedNextRevision() {
        assertEquals(
                WaypointRevisionSequence.Decision.APPLY,
                WaypointRevisionSequence.classify(7, 8)
        );
    }

    @Test
    void ignoresStaleRevisions() {
        assertEquals(
                WaypointRevisionSequence.Decision.STALE,
                WaypointRevisionSequence.classify(7, 7)
        );
        assertEquals(
                WaypointRevisionSequence.Decision.STALE,
                WaypointRevisionSequence.classify(7, 5)
        );
    }

    @Test
    void detectsMissingStateAndRevisionGaps() {
        assertEquals(
                WaypointRevisionSequence.Decision.GAP,
                WaypointRevisionSequence.classify(null, 1)
        );
        assertEquals(
                WaypointRevisionSequence.Decision.GAP,
                WaypointRevisionSequence.classify(7, 9)
        );
    }
}
