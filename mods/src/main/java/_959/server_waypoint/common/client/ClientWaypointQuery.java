package _959.server_waypoint.common.client;

import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.util.ColorUtils;
import _959.server_waypoint.util.VanillaDimensionNames;
import java.util.*;

/** Composes read-only query rows; the underlying writable collections are never merged. */
public final class ClientWaypointQuery {
    public enum View {
        SHARED, PRIVATE, LOCAL, COMBINED;
        public List<ClientWaypointSource> sources() {
            return this == COMBINED ? List.of(ClientWaypointSource.values()) : List.of(ClientWaypointSource.valueOf(name()));
        }
        public ClientWaypointSource destination() {
            return this == COMBINED ? ClientWaypointSource.LOCAL : ClientWaypointSource.valueOf(name());
        }
        public String translationKey() { return "waypoint.collection." + name().toLowerCase(Locale.ROOT); }
    }
    public record Row(ClientWaypointSelection selection, WaypointList list, SimpleWaypoint waypoint) { }
    public record ListGroup(ClientWaypointSelection selection, WaypointList list, List<Row> rows) {
        public ListGroup { rows = List.copyOf(rows); }
    }
    public record Result(List<ListGroup> lists, List<Row> rows) {
        public Result { lists = List.copyOf(lists); rows = List.copyOf(rows); }
    }
    public record Suggestion(ClientWaypointSource source, String text) { }
    private final ClientWaypointCollections collections;
    public ClientWaypointQuery(ClientWaypointCollections collections) { this.collections = Objects.requireNonNull(collections); }

    public List<String> dimensions(View view) {
        var names = new TreeSet<String>(VanillaDimensionNames::dimensionNameComparator);
        for (var source : view.sources()) if (collections.ready(source)) names.addAll(collections.files(source).getFileManagerMap().keySet());
        return List.copyOf(names);
    }
    public List<Suggestion> suggestions(View view, String dimension) {
        var result = new ArrayList<Suggestion>();
        for (var source : view.sources()) if (collections.ready(source)) {
            var engine = new WaypointQueryEngine(collections.files(source));
            var dimensions = dimension == null ? dimensions(view) : List.of(dimension);
            var texts = new LinkedHashSet<String>();
            for (String name : dimensions) texts.addAll(engine.getSearchSuggestions(name));
            for (String text : texts) result.add(new Suggestion(source, text));
        }
        return List.copyOf(result);
    }
    public Result query(View view, String dimension, WaypointQueryEngine.Query options) {
        var lists = new ArrayList<ListGroup>();
        var rows = new ArrayList<Row>();
        for (var source : view.sources()) {
            if (!collections.ready(source)) continue;
            var engine = new WaypointQueryEngine(collections.files(source));
            var result = dimension == null ? engine.queryAll(options) : engine.queryDimension(dimension, options);
            var display = WaypointListDisplayModel.build(result, true);
            for (var list : display.lists()) {
                var selection = collections.selection(source, list.dimensionName(), list.sourceList().name(), null);
                var entries = list.waypoints().stream().map(waypoint -> new Row(
                        selection.at(selection.dimensionName(), selection.listIdentifier(), waypoint.name()), list.sourceList(), waypoint)).toList();
                lists.add(new ListGroup(selection, list.sourceList(), entries));
                rows.addAll(entries);
            }
        }
        lists.sort((left, right) -> {
            int order = VanillaDimensionNames.dimensionNameComparator(left.selection().dimensionName(), right.selection().dimensionName());
            if (order == 0) order = left.selection().source().compareTo(right.selection().source());
            if (order == 0 && options.sortMode() == WaypointSorting.SortMode.NAME) {
                order = WaypointSorting.byName((ListGroup group) -> group.list().name()).compare(left, right);
                if (options.reversed()) order = -order;
            }
            return order;
        });
        sortRows(rows, options);
        return new Result(lists, rows);
    }
    private static void sortRows(List<Row> rows, WaypointQueryEngine.Query options) {
        Comparator<Row> byName = WaypointSorting.byName((Row row) -> row.waypoint().name())
                .thenComparing(WaypointSorting.byName(row -> row.list().name()));
        Comparator<Row> order = byName;
        if (options.sortMode() == WaypointSorting.SortMode.COLOR) {
            ColorUtils.sortWaypointColors(rows, row -> row.waypoint().rgb(), byName);
            // Preserve the existing perceptual color ordering, reversing only the primary sort.
            if (options.reversed()) Collections.reverse(rows);
            // Equal full sort keys must keep Shared, Private, Local even in reverse order.
            for (int start = 0; start < rows.size();) {
                int end = start + 1;
                Row first = rows.get(start);
                while (end < rows.size() && first.waypoint().rgb() == rows.get(end).waypoint().rgb()
                        && byName.compare(first, rows.get(end)) == 0) end++;
                rows.subList(start, end).sort(Comparator.comparing(row -> row.selection().source()));
                start = end;
            }
            return;
        }
        if (options.sortMode() == WaypointSorting.SortMode.DEFAULT) return;
        if (options.sortMode() == WaypointSorting.SortMode.DISTANCE) {
            order = Comparator.comparingDouble((Row row) -> WaypointSorting.distanceSquared(row.waypoint(), options.origin(),
                    options.originDimension(), row.selection().dimensionName())).thenComparing(byName);
        }
        if (options.reversed()) order = order.reversed();
        order = order.thenComparing(row -> row.selection().source());
        if (options.sortMode() == WaypointSorting.SortMode.DISTANCE) {
            var comparable = new ArrayList<Row>();
            var other = new ArrayList<Row>();
            for (Row row : rows) (WaypointSorting.canCompareDistance(options.originDimension(), row.selection().dimensionName()) ? comparable : other).add(row);
            comparable.sort(order);
            other.sort((left, right) -> VanillaDimensionNames.dimensionNameComparator(left.selection().dimensionName(), right.selection().dimensionName()));
            rows.clear(); rows.addAll(comparable); rows.addAll(other);
        } else rows.sort(order);
    }
}
