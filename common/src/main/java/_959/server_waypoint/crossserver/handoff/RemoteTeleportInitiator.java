package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.RemoteRevision;
import _959.server_waypoint.crossserver.RemoteWaypointKey;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import java.util.Objects;
import java.util.function.Consumer;

/** Backend command boundary; implementations must never block or perform optimistic transfers. */
@FunctionalInterface
public interface RemoteTeleportInitiator<S> {
    record Selection(RemoteWaypointKey key, RemoteRevision catalogRevision, RemoteRevision listRevision) {
        public Selection {
            Objects.requireNonNull(key); Objects.requireNonNull(catalogRevision); Objects.requireNonNull(listRevision);
        }
    }
    /** Called on the source owner. Deliver feedback at most once, on that same live player's owner. */
    void initiate(S source, Selection selection, Consumer<Result> feedback);
}
