package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.crossserver.*;
import java.util.*;

/** Captures immutable export data. Throw on unavailable data; never substitute an empty map for failure. */
@FunctionalInterface
public interface CatalogSource {
    Map<String, Map<String, RemoteListSnapshot>> capture() throws Exception;

    static CatalogSource fromManager(WaypointFilesManagerCore manager, CatalogSelection selection, int maximumObjects) {
        return () -> {
            var detached = manager.snapshotWaypointData(maximumObjects);
            Map<String, Map<String, RemoteListSnapshot>> dimensions = new HashMap<>();
            detached.forEach((dimension, lists) -> {
                if (!selection.includesDimension(dimension)) return;
                Map<String, RemoteListSnapshot> exported = new HashMap<>();
                lists.forEach(list -> {
                    if (!selection.includes(dimension, list.name())) return;
                    Map<String, RemoteWaypointSnapshot> waypoints = new HashMap<>();
                    list.simpleWaypoints().forEach(waypoint -> waypoints.put(waypoint.name(), new RemoteWaypointSnapshot(
                            waypoint.displayName(), waypoint.initials(), waypoint.pos(), waypoint.rgb(), waypoint.yaw(),
                            waypoint.global(), waypoint.keywords(), waypoint.description())));
                    // Publication assigns independent durable revisions, not the local int sync counter.
                    exported.put(list.name(), new RemoteListSnapshot(list.displayName(), new RemoteRevision(0), waypoints));
                });
                dimensions.put(dimension, Map.copyOf(exported));
            });
            return Map.copyOf(dimensions);
        };
    }
}
