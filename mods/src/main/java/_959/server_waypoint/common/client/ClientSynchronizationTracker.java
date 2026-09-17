package _959.server_waypoint.common.client;

import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.WaypointRevisionSequence;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Tracks client-local uncertainty without changing the network handshake state. */
public final class ClientSynchronizationTracker {
    private boolean synchronizationUncertain;
    private final Set<WaypointListKey> outOfSyncLists = new HashSet<>();

    public void markUncertain() {
        this.synchronizationUncertain = true;
    }

    public boolean isSynchronizationUncertain() {
        return this.synchronizationUncertain;
    }

    public void markTransportFailure(int messageTypeId) {
        if (messageTypeId == ChunkedMessageRegistry.WAYPOINT_DATA.id()
                || messageTypeId == ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id()
                || messageTypeId == ChunkedMessageRegistry.WAYPOINT_LIST_UPDATE.id()) {
            this.markUncertain();
        }
    }

    public void markOutOfSync(String dimensionName, String listName) {
        this.outOfSyncLists.add(new WaypointListKey(dimensionName, listName));
    }

    public boolean isOutOfSync(String dimensionName, String listName) {
        return this.outOfSyncLists.contains(new WaypointListKey(dimensionName, listName));
    }

    public boolean shouldApplyIncremental(
            String dimensionName,
            String listName,
            @Nullable Integer currentRevision,
            int incomingRevision
    ) {
        if (this.isOutOfSync(dimensionName, listName)) {
            return false;
        }
        WaypointRevisionSequence.Decision decision = WaypointRevisionSequence.classify(
                currentRevision,
                incomingRevision
        );
        if (decision == WaypointRevisionSequence.Decision.GAP) {
            this.markOutOfSync(dimensionName, listName);
        }
        return decision == WaypointRevisionSequence.Decision.APPLY;
    }

    public void clearList(String dimensionName, String listName) {
        this.outOfSyncLists.remove(new WaypointListKey(dimensionName, listName));
    }

    public void clearDimension(String dimensionName) {
        Objects.requireNonNull(dimensionName, "dimensionName");
        this.outOfSyncLists.removeIf(key -> key.dimensionName().equals(dimensionName));
    }

    public void clearAll() {
        this.synchronizationUncertain = false;
        this.outOfSyncLists.clear();
    }

    record WaypointListKey(String dimensionName, String listName) {
        WaypointListKey {
            Objects.requireNonNull(dimensionName, "dimensionName");
            Objects.requireNonNull(listName, "listName");
        }
    }
}
