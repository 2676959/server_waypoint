package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.util.ColorUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WaypointListDisplayModel {
    private WaypointListDisplayModel() {
    }

    public static Display build(WaypointQueryEngine.QueryResult result, boolean requestedGroupByLists) {
        WaypointSorting.SortMode sortMode = result.query().sortMode();
        boolean groupByLists = sortMode == WaypointSorting.SortMode.DEFAULT || requestedGroupByLists;
        List<DisplayList> lists = createDisplayLists(result, sortMode);
        if (groupByLists) {
            return new Display(true, Collections.unmodifiableList(lists), List.of());
        }

        List<DisplayWaypoint> flatWaypoints = createFlatWaypoints(lists);
        sortDisplayWaypoints(flatWaypoints, sortMode, result.query().origin());
        if (result.query().reversed()) {
            Collections.reverse(flatWaypoints);
        }
        return new Display(false, List.of(), Collections.unmodifiableList(flatWaypoints));
    }

    private static List<DisplayList> createDisplayLists(
            WaypointQueryEngine.QueryResult result,
            WaypointSorting.SortMode sortMode
    ) {
        List<DisplayList> lists = new ArrayList<>();
        for (WaypointQueryEngine.DimensionResult dimension : result.dimensions()) {
            List<DisplayList> dimensionLists = new ArrayList<>();
            for (WaypointQueryEngine.ListResult listResult : dimension.lists()) {
                List<SimpleWaypoint> waypoints = new ArrayList<>(listResult.waypoints());
                WaypointSorting.sort(waypoints, sortMode, result.query().origin(), result.query().reversed());
                dimensionLists.add(new DisplayList(
                        dimension.dimensionName(),
                        listResult.sourceList(),
                        Collections.unmodifiableList(waypoints)
                ));
            }
            if (sortMode == WaypointSorting.SortMode.NAME) {
                dimensionLists.sort(DisplayList.BY_LIST_NAME);
                if (result.query().reversed()) {
                    Collections.reverse(dimensionLists);
                }
            }
            lists.addAll(dimensionLists);
        }
        return lists;
    }

    private static List<DisplayWaypoint> createFlatWaypoints(List<DisplayList> lists) {
        List<DisplayWaypoint> flatWaypoints = new ArrayList<>();
        for (DisplayList list : lists) {
            for (SimpleWaypoint waypoint : list.waypoints()) {
                flatWaypoints.add(new DisplayWaypoint(
                        list.dimensionName(),
                        list.sourceList(),
                        waypoint
                ));
            }
        }
        return flatWaypoints;
    }

    private static void sortDisplayWaypoints(List<DisplayWaypoint> waypoints, WaypointSorting.SortMode sortMode, WaypointPos origin) {
        switch (sortMode) {
            case DEFAULT -> {
            }
            case NAME -> waypoints.sort(DisplayWaypoint.BY_WAYPOINT_NAME);
            case DISTANCE -> waypoints.sort(DisplayWaypoint.byDistanceFrom(origin));
            case COLOR -> ColorUtils.sortWaypointColors(
                    waypoints,
                    waypoint -> waypoint.waypoint().rgb(),
                    DisplayWaypoint.BY_WAYPOINT_NAME
            );
        }
    }

    public record Display(boolean groupByLists, List<DisplayList> lists, List<DisplayWaypoint> flatWaypoints) {
        public List<DisplayDimension> dimensions() {
            Map<String, List<DisplayList>> listsByDimension = new LinkedHashMap<>();
            for (DisplayList list : this.lists) {
                listsByDimension.computeIfAbsent(list.dimensionName(), key -> new ArrayList<>()).add(list);
            }
            List<DisplayDimension> dimensions = new ArrayList<>();
            for (Map.Entry<String, List<DisplayList>> entry : listsByDimension.entrySet()) {
                dimensions.add(new DisplayDimension(
                        entry.getKey(),
                        Collections.unmodifiableList(entry.getValue())
                ));
            }
            return Collections.unmodifiableList(dimensions);
        }
    }

    public record DisplayDimension(String dimensionName, List<DisplayList> lists) {
    }

    public record DisplayList(String dimensionName, WaypointList sourceList, List<SimpleWaypoint> waypoints) {
        public DisplayList(WaypointList sourceList, List<SimpleWaypoint> waypoints) {
            this("", sourceList, waypoints);
        }

        private static final Comparator<DisplayList> BY_LIST_NAME = Comparator.comparing(
                        (DisplayList list) -> list.sourceList().name(),
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(list -> list.sourceList().name());
    }

    public record DisplayWaypoint(String dimensionName, WaypointList sourceList, SimpleWaypoint waypoint) {
        public DisplayWaypoint(WaypointList sourceList, SimpleWaypoint waypoint) {
            this("", sourceList, waypoint);
        }

        public static final Comparator<DisplayWaypoint> BY_WAYPOINT_NAME = Comparator.comparing(
                        (DisplayWaypoint row) -> row.waypoint().name(),
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(row -> row.waypoint().name())
                .thenComparing(row -> row.sourceList().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.sourceList().name());

        private static Comparator<DisplayWaypoint> byDistanceFrom(WaypointPos origin) {
            return Comparator.comparingLong((DisplayWaypoint row) -> WaypointSorting.distanceSquared(row.waypoint(), origin))
                    .thenComparing(BY_WAYPOINT_NAME);
        }
    }
}
