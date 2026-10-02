package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogSelection;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import java.util.Objects;
import java.util.function.Supplier;

/** Current authoritative PUBLIC lookup; no remote cache, revision-based coordinates or offline player lookup. */
@FunctionalInterface
public interface DestinationResolver {
    record Target(RemoteWaypointKey key, WaypointPos position, int yaw) {
        public Target { Objects.requireNonNull(key); Objects.requireNonNull(position); }
    }
    record Resolution(Result result, Target target) {
        public Resolution {
            Objects.requireNonNull(result);
            if ((result == Result.SUCCESS) != (target != null)) throw new IllegalArgumentException("Invalid resolution");
        }
        public static Resolution denied(Result reason) { return new Resolution(reason, null); }
    }
    Resolution resolve(RemoteWaypointKey key) throws Exception;

    /** Uses the existing atomic detached capture; no external callbacks execute while model locks are held. */
    static DestinationResolver fromManager(RemoteServerId localId, WaypointFilesManagerCore manager,
                                           Supplier<CatalogSelection> selection, int maximumObjects) {
        Objects.requireNonNull(localId); Objects.requireNonNull(manager); Objects.requireNonNull(selection);
        if (maximumObjects < 1 || maximumObjects > 65_536) throw new IllegalArgumentException("Invalid snapshot budget");
        return key -> {
            if (!localId.equals(key.serverId())) return Resolution.denied(Result.WRONG_DESTINATION);
            if (!selection.get().includes(key.dimensionName(), key.listName())) return Resolution.denied(Result.UNAUTHORIZED);
            var data = manager.snapshotWaypointData(maximumObjects);
            var lists = data.get(key.dimensionName());
            if (lists == null) return Resolution.denied(Result.NOT_FOUND);
            for (var list : lists) {
                if (!list.name().equals(key.listName())) continue;
                var waypoint = list.getWaypointByName(key.waypointName());
                if (waypoint != null) return new Resolution(Result.SUCCESS, new Target(key, waypoint.pos(), waypoint.yaw()));
            }
            return Resolution.denied(Result.NOT_FOUND);
        };
    }
}
