package _959.server_waypoint.crossserver;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete immutable catalog, keyed first by exact dimension identity, then exact list identity.
 * receivedAt is receiver-local metadata, not a revision, remote clock, or authorization proof.
 * Empty dimensions/lists are preserved. Wire budgets and atomic store replacement belong to later steps.
 */
public record RemoteCatalogSnapshot(
        RemoteServerId serverId,
        RemoteRevision catalogRevision,
        Map<String, Map<String, RemoteListSnapshot>> dimensions,
        Instant receivedAt
) {
    public RemoteCatalogSnapshot {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(catalogRevision, "catalogRevision");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Map<String, Map<String, RemoteListSnapshot>> copied = new HashMap<>();
        dimensions.forEach((dimension, lists) -> copied.put(dimension, Map.copyOf(lists)));
        dimensions = Map.copyOf(copied);
    }

    public Optional<RemoteWaypointSnapshot> find(RemoteWaypointKey key) {
        Objects.requireNonNull(key, "key");
        if (!serverId.equals(key.serverId())) {
            return Optional.empty();
        }
        Map<String, RemoteListSnapshot> lists = dimensions.get(key.dimensionName());
        RemoteListSnapshot list = lists == null ? null : lists.get(key.listName());
        return list == null ? Optional.empty() : Optional.ofNullable(list.waypoints().get(key.waypointName()));
    }

    /** Full snapshots may skip revisions; delta gap validation belongs to the synchronization layer. */
    public boolean isNewerThan(RemoteCatalogSnapshot previous) {
        if (!serverId.equals(previous.serverId)) {
            throw new IllegalArgumentException("Cannot compare catalogs from different servers");
        }
        return catalogRevision.compareTo(previous.catalogRevision) > 0;
    }
}
