package _959.server_waypoint.crossserver;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable reader-facing status and optional retained data, separate from the published snapshot.
 * UNAVAILABLE can retain advisory data; UNAUTHORIZED cannot expose it. Cache retention and expiry
 * policy live outside this value type. AVAILABLE with an empty snapshot is an explicit publication.
 */
public record RemoteCatalogView(
        RemoteServerId serverId,
        RemoteCatalogState state,
        Optional<RemoteCatalogSnapshot> snapshot
) {
    public RemoteCatalogView {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.isPresent() && !serverId.equals(snapshot.get().serverId())) {
            throw new IllegalArgumentException("Snapshot must belong to the view server");
        }
        if ((state == RemoteCatalogState.AVAILABLE || state == RemoteCatalogState.STALE) && snapshot.isEmpty()) {
            throw new IllegalArgumentException("Available and stale views require a snapshot");
        }
        if (state == RemoteCatalogState.UNAUTHORIZED && snapshot.isPresent()) {
            throw new IllegalArgumentException("Unauthorized views must not expose retained data");
        }
    }
}
