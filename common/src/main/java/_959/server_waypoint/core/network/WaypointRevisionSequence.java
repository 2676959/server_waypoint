package _959.server_waypoint.core.network;

import org.jetbrains.annotations.Nullable;

/**
 * Classifies an incremental waypoint-list revision received by a client.
 */
public final class WaypointRevisionSequence {
    private WaypointRevisionSequence() {
    }

    public static Decision classify(@Nullable Integer currentRevision, int incomingRevision) {
        if (currentRevision == null) {
            return Decision.GAP;
        }
        if (incomingRevision <= currentRevision) {
            return Decision.STALE;
        }
        return (long) incomingRevision == (long) currentRevision + 1L
                ? Decision.APPLY
                : Decision.GAP;
    }

    public enum Decision {
        APPLY,
        STALE,
        GAP
    }
}
