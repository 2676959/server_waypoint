package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteListSnapshot;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.RemoteWaypointSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One capture of the local remote-catalog cache as servers holding lists in the shape local
 * screens use (spec 14). Pure: it never resolves local game worlds. Catalog snapshots don't keep
 * the published order, so lists and waypoints follow their identifiers.
 */
public final class RemoteCatalogQuery {
    private RemoteCatalogQuery() {
    }

    /** A server as a reader may see it. Unreachable and no-access servers never show their last copy. */
    public record Server(RemoteServerId id, String displayName, RemoteCatalogState state,
                         Map<String, List<WaypointList>> dimensions) {
        public Server {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(state, "state");
            Map<String, List<WaypointList>> copied = new TreeMap<>();
            dimensions.forEach((dimension, lists) -> copied.put(dimension, List.copyOf(lists)));
            dimensions = Collections.unmodifiableMap(copied);
        }

        /** Available and stale servers show their catalog. */
        public boolean readable() {
            return this.state == RemoteCatalogState.AVAILABLE || this.state == RemoteCatalogState.STALE;
        }

        /** Only an available server takes teleports. */
        public boolean available() {
            return this.state == RemoteCatalogState.AVAILABLE;
        }

        public @Nullable List<WaypointList> lists(String dimension) {
            return this.dimensions.get(dimension);
        }

        public @Nullable WaypointList list(String dimension, String list) {
            List<WaypointList> lists = this.lists(dimension);
            return lists == null ? null
                    : lists.stream().filter(candidate -> candidate.name().equals(list)).findFirst().orElse(null);
        }

        public int listCount() {
            return this.dimensions.values().stream().mapToInt(List::size).sum();
        }

        public int waypointCount() {
            return this.dimensions.values().stream().flatMap(List::stream).mapToInt(WaypointList::size).sum();
        }
    }

    /** Every cached server, by identity. */
    public static List<Server> servers(Map<RemoteServerId, CatalogReceiver.View> catalogs) {
        return catalogs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value)))
                .map(entry -> server(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** The cached server with exactly this identity. */
    public static Optional<Server> server(Map<RemoteServerId, CatalogReceiver.View> catalogs, String id) {
        return catalogs.entrySet().stream()
                .filter(entry -> entry.getKey().value().equals(id))
                .findFirst()
                .map(entry -> server(entry.getKey(), entry.getValue()));
    }

    private static Server server(RemoteServerId id, CatalogReceiver.View view) {
        Map<String, List<WaypointList>> dimensions = new TreeMap<>();
        boolean readable = view.state() == RemoteCatalogState.AVAILABLE || view.state() == RemoteCatalogState.STALE;
        if (readable && view.snapshot() != null) {
            view.snapshot().dimensions().forEach((dimension, lists) -> dimensions.put(dimension, lists(lists)));
        }
        return new Server(id, view.displayName(), view.state(), dimensions);
    }

    private static List<WaypointList> lists(Map<String, RemoteListSnapshot> lists) {
        List<WaypointList> converted = new ArrayList<>();
        new TreeMap<>(lists).forEach((name, list) -> {
            List<SimpleWaypoint> waypoints = new ArrayList<>();
            new TreeMap<>(list.waypoints()).forEach((waypoint, value) -> waypoints.add(waypoint(waypoint, value)));
            converted.add(new WaypointList(name, list.displayName(), 0, waypoints));
        });
        return converted;
    }

    private static SimpleWaypoint waypoint(String name, RemoteWaypointSnapshot value) {
        return new SimpleWaypoint(name, value.displayName(), value.initials(), value.position(), value.rgb(),
                value.yaw(), value.global(), value.keywords(), value.description(), value.icon());
    }
}
