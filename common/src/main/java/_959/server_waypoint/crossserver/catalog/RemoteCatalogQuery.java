package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.util.ColorUtils;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import java.util.*;

/** Pure presentation adapter over one immutable local cache capture. Never resolves local game worlds. */
public final class RemoteCatalogQuery {
    public record Scope(String server, String dimension, String list) {
        public Scope {
            if (server == null && dimension != null || dimension == null && list != null) throw new IllegalArgumentException("Nonhierarchical scope");
        }
    }
    public record Row(RemoteServerId server, String serverLabel, RemoteCatalogState state, String dimension,
                      String list, String listLabel, String waypointName, RemoteWaypointSnapshot waypoint) { }
    public enum Problem { NONE, UNKNOWN_SERVER, UNKNOWN_DIMENSION, UNKNOWN_LIST, DISTANCE_UNAVAILABLE }
    public record Result(List<Row> rows, int totalRows, int totalPages, Problem problem) {
        public Result { rows = List.copyOf(rows); }
    }
    private static final Comparator<Row> BY_NAME = WaypointSorting.<Row>byName(row -> row.waypointName() == null ? "" : row.waypointName())
            .thenComparing(row -> row.server().value()).thenComparing(row -> Objects.toString(row.dimension(), ""))
            .thenComparing(row -> Objects.toString(row.list(), ""));

    public Result query(Map<RemoteServerId, CatalogReceiver.View> catalogs, Scope scope, ListOptions options) {
        if (options.pageNumber() < 1 || options.pageLimit() < 1 || options.pageLimit() > _959.server_waypoint.config.Config.MAX_PAGE_LIMIT) {
            throw new IllegalArgumentException("Invalid remote page");
        }
        if (options.sortMode() == WaypointSorting.SortMode.DISTANCE) return failure(Problem.DISTANCE_UNAVAILABLE);
        var selected = catalogs.entrySet().stream().filter(entry -> scope.server() == null || entry.getKey().value().equals(scope.server()))
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value))).toList();
        if (scope.server() != null && selected.isEmpty()) return failure(Problem.UNKNOWN_SERVER);
        List<Row> rows = new ArrayList<>();
        String filter = options.filterText() == null ? "" : options.filterText().trim();
        for (var entry : selected) {
            RemoteServerId id = entry.getKey(); CatalogReceiver.View view = entry.getValue();
            if (view.snapshot() == null || view.state() == RemoteCatalogState.UNAUTHORIZED || view.state() == RemoteCatalogState.UNAVAILABLE) {
                rows.add(new Row(id, view.displayName(), view.state(), null, null, null, null, null)); continue;
            }
            var dimensions = view.snapshot().dimensions();
            if (scope.dimension() != null && !dimensions.containsKey(scope.dimension())) return failure(Problem.UNKNOWN_DIMENSION);
            if (dimensions.isEmpty() && filter.isEmpty()) rows.add(new Row(id, view.displayName(), view.state(), null, null, null, null, null));
            for (String dimension : new TreeSet<>(dimensions.keySet())) {
                if (scope.dimension() != null && !scope.dimension().equals(dimension)) continue;
                var lists = dimensions.get(dimension);
                if (scope.list() != null && !lists.containsKey(scope.list())) return failure(Problem.UNKNOWN_LIST);
                if (lists.isEmpty() && filter.isEmpty()) rows.add(new Row(id, view.displayName(), view.state(), dimension, null, null, null, null));
                List<String> names = new ArrayList<>(new TreeSet<>(lists.keySet()));
                if (options.sortMode() == WaypointSorting.SortMode.NAME) {
                    names.sort(WaypointSorting.byName(value -> value));
                    if (options.reversed()) Collections.reverse(names);
                }
                for (String name : names) {
                    if (scope.list() != null && !scope.list().equals(name)) continue;
                    RemoteListSnapshot list = lists.get(name);
                    boolean matchedList = filter.isEmpty() || WaypointQueryEngine.matchesFilter(name, filter);
                    List<Row> group = new ArrayList<>();
                    for (String waypoint : new TreeSet<>(list.waypoints().keySet())) {
                        RemoteWaypointSnapshot value = list.waypoints().get(waypoint);
                        if (matchedList || WaypointQueryEngine.matchesFilter(waypoint, filter)
                                || value.keywords().stream().anyMatch(keyword -> WaypointQueryEngine.matchesFilter(keyword, filter))) {
                            group.add(new Row(id, view.displayName(), view.state(), dimension, name, list.displayName(), waypoint, value));
                        }
                    }
                    if (group.isEmpty() && matchedList) group.add(new Row(id, view.displayName(), view.state(), dimension, name, list.displayName(), null, null));
                    if (options.groupByLists()) sort(group, options);
                    rows.addAll(group);
                }
            }
        }
        if (!options.groupByLists()) sort(rows, options);
        int totalPages = Math.max(1, (rows.size() + options.pageLimit() - 1) / options.pageLimit());
        long start = (long) (options.pageNumber() - 1) * options.pageLimit();
        List<Row> page = start >= rows.size() ? List.of() : rows.subList((int) start, (int) Math.min(rows.size(), start + options.pageLimit()));
        return new Result(page, rows.size(), totalPages, Problem.NONE);
    }
    private static Result failure(Problem problem) { return new Result(List.of(), 0, 1, problem); }
    private static void sort(List<Row> rows, ListOptions options) {
        WaypointSorting.SortMode mode = options.sortMode();
        if (!options.groupByLists() && mode == WaypointSorting.SortMode.DEFAULT) mode = WaypointSorting.SortMode.NAME;
        if (mode == WaypointSorting.SortMode.NAME) rows.sort(BY_NAME);
        else if (mode == WaypointSorting.SortMode.COLOR) {
            ColorUtils.sortWaypointColors(rows, row -> row.waypoint() == null ? 0 : row.waypoint().rgb(), BY_NAME);
        }
        if (mode != WaypointSorting.SortMode.DEFAULT && options.reversed()) Collections.reverse(rows);
    }
}
