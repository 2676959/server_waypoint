package _959.server_waypoint.common.client;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import java.util.*;

/** Source-owned marker references. Capacity is a renderer concern and never removes stored data. */
public final class ClientWaypointScene {
    public interface Markers {
        void add(SimpleWaypoint waypoint);
        void remove(SimpleWaypoint waypoint);
        void update(SimpleWaypoint waypoint);
    }
    private record Key(ClientWaypointSource source, UUID profile, String dimension, String list, String waypoint) {
        static Key of(ClientWaypointSelection selection) {
            return new Key(selection.source(), selection.profileId(), selection.dimensionName(), selection.listIdentifier(), selection.waypointIdentifier());
        }
    }
    private final ClientWaypointCollections collections;
    private final Markers markers;
    private final EnumMap<ClientWaypointSource, Map<Key, SimpleWaypoint>> visible = new EnumMap<>(ClientWaypointSource.class);
    private final Set<Key> hidden = new HashSet<>();
    private final Map<SimpleWaypoint, Presentation> presentations = new IdentityHashMap<>();
    private record Presentation(String name, String initials, int x, int y, int z, int rgb, boolean global,
                                _959.server_waypoint.util.NamespacedId icon) {
        static Presentation of(SimpleWaypoint waypoint) {
            return new Presentation(waypoint.displayName(), waypoint.initials(), waypoint.x(), waypoint.y(), waypoint.z(),
                    waypoint.rgb(), waypoint.global(), waypoint.icon());
        }
    }
    private final ClientWaypointQuery query;
    private String dimension;

    public ClientWaypointScene(ClientWaypointCollections collections, Markers markers) {
        this.collections = collections;
        this.markers = markers;
        this.query = new ClientWaypointQuery(collections);
        for (var source : ClientWaypointSource.values()) visible.put(source, new LinkedHashMap<>());
    }
    public void refresh(ClientWaypointSource source, String dimension) {
        this.dimension = dimension;
        var next = new LinkedHashMap<Key, SimpleWaypoint>();
        if (dimension != null) {
            var options = new _959.server_waypoint.core.waypoint.WaypointQueryEngine.Query("",
                    _959.server_waypoint.core.waypoint.WaypointSorting.SortMode.DEFAULT, null, dimension, false);
            for (var row : query.query(ClientWaypointQuery.View.valueOf(source.name()), dimension, options).rows()) {
                Key key = Key.of(row.selection());
                if (row.list().isShow() && !hidden.contains(key)) next.put(key, row.waypoint());
            }
        }
        Map<Key, SimpleWaypoint> previous = visible.get(source);
        previous.forEach((key, waypoint) -> {
            if (next.get(key) != waypoint) {
                markers.remove(waypoint);
                presentations.remove(waypoint);
            }
        });
        next.forEach((key, waypoint) -> {
            Presentation presentation = Presentation.of(waypoint);
            if (previous.get(key) != waypoint) markers.add(waypoint);
            else if (!presentation.equals(presentations.get(waypoint))) markers.update(waypoint);
            presentations.put(waypoint, presentation);
        });
        visible.put(source, next);
    }
    public void setVisible(ClientWaypointSelection selection, boolean show) {
        if (!collections.valid(selection)) return;
        Key key = Key.of(selection);
        if (show) hidden.remove(key); else hidden.add(key);
        refresh(selection.source(), dimension);
    }
    public void clear() {
        for (var entries : visible.values()) {
            entries.values().forEach(markers::remove);
            entries.clear();
        }
        hidden.clear();
        presentations.clear();
        dimension = null;
    }
}
