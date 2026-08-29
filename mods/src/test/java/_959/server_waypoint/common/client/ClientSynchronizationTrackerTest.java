package _959.server_waypoint.common.client;

import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientSynchronizationTrackerTest {
    @Test
    void stateAffectingTransportFailuresMarkTheSessionUncertain() {
        int[] stateAffectingTypes = {
                ChunkedMessageRegistry.WAYPOINT_DATA.id(),
                ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id(),
                ChunkedMessageRegistry.WAYPOINT_LIST_UPDATE.id()
        };

        for (int messageTypeId : stateAffectingTypes) {
            ClientSynchronizationTracker tracker = new ClientSynchronizationTracker();
            tracker.markTransportFailure(messageTypeId);
            assertTrue(tracker.isSynchronizationUncertain());
        }
    }

    @Test
    void editResultFailureDoesNotMarkWaypointStateUncertain() {
        ClientSynchronizationTracker tracker = new ClientSynchronizationTracker();

        tracker.markTransportFailure(ChunkedMessageRegistry.WAYPOINT_EDIT_RESULT.id());

        assertFalse(tracker.isSynchronizationUncertain());
    }

    @Test
    void healthyListsContinueWhileTheSessionIsUncertain() {
        ClientSynchronizationTracker tracker = new ClientSynchronizationTracker();
        tracker.markUncertain();

        assertTrue(tracker.shouldApplyIncremental("overworld", "healthy", 7, 8));
        assertTrue(tracker.isSynchronizationUncertain());
        assertFalse(tracker.isOutOfSync("overworld", "healthy"));
    }

    @Test
    void revisionGapMarksAListAndRejectsLaterIncrementals() {
        ClientSynchronizationTracker tracker = new ClientSynchronizationTracker();

        assertFalse(tracker.shouldApplyIncremental("overworld", "stale", 7, 9));
        assertTrue(tracker.isOutOfSync("overworld", "stale"));
        assertFalse(tracker.shouldApplyIncremental("overworld", "stale", 8, 9));
        assertTrue(tracker.shouldApplyIncremental("overworld", "healthy", 8, 9));
    }

    @Test
    void staleRevisionIsIgnoredWithoutPoisoningTheList() {
        ClientSynchronizationTracker tracker = new ClientSynchronizationTracker();

        assertFalse(tracker.shouldApplyIncremental("overworld", "list", 7, 7));
        assertFalse(tracker.isOutOfSync("overworld", "list"));
    }

    @Test
    void authoritativeScopesClearOnlyTheirMarkers() {
        ClientSynchronizationTracker tracker = new ClientSynchronizationTracker();
        tracker.markUncertain();
        tracker.markOutOfSync("overworld", "first");
        tracker.markOutOfSync("overworld", "second");
        tracker.markOutOfSync("the_nether", "third");

        tracker.clearList("overworld", "first");
        assertFalse(tracker.isOutOfSync("overworld", "first"));
        assertTrue(tracker.isOutOfSync("overworld", "second"));
        assertTrue(tracker.isSynchronizationUncertain());

        tracker.clearDimension("overworld");
        assertFalse(tracker.isOutOfSync("overworld", "second"));
        assertTrue(tracker.isOutOfSync("the_nether", "third"));
        assertTrue(tracker.isSynchronizationUncertain());

        tracker.clearAll();
        assertFalse(tracker.isOutOfSync("the_nether", "third"));
        assertFalse(tracker.isSynchronizationUncertain());
    }
}
