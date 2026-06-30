package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.util.ColorUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class WaypointListDisplayModel {
    private WaypointListDisplayModel() {
    }

    static Display build(WaypointQueryEngine.QueryResult result, boolean requestedGroupByLists) {
        WaypointSorting.SortMode sortMode = result.query().sortMode();
        boolean groupByLists = sortMode == WaypointSorting.SortMode.DEFAULT || requestedGroupByLists;
        List<DisplayList> lists = createDisplayLists(result, sortMode);
        if (groupByLists) {
            if (sortMode == WaypointSorting.SortMode.NAME) {
                lists.sort(DisplayList.BY_LIST_NAME);
            }
            return new Display(true, Collections.unmodifiableList(lists), List.of());
        }

        List<DisplayWaypoint> flatWaypoints = createFlatWaypoints(lists);
        sortDisplayWaypoints(flatWaypoints, sortMode, result.query().origin());
        return new Display(false, List.of(), Collections.unmodifiableList(flatWaypoints));
    }

    private static List<DisplayList> createDisplayLists(WaypointQueryEngine.QueryResult result, WaypointSorting.SortMode sortMode) {
        List<DisplayList> lists = new ArrayList<>();
        for (WaypointQueryEngine.DimensionResult dimension : result.dimensions()) {
            for (WaypointQueryEngine.ListResult listResult : dimension.lists()) {
                List<SimpleWaypoint> waypoints = new ArrayList<>(listResult.waypoints());
                WaypointSorting.sort(waypoints, sortMode, result.query().origin());
                lists.add(new DisplayList(listResult.sourceList(), Collections.unmodifiableList(waypoints)));
            }
        }
        return lists;
    }

    private static List<DisplayWaypoint> createFlatWaypoints(List<DisplayList> lists) {
        List<DisplayWaypoint> flatWaypoints = new ArrayList<>();
        for (DisplayList list : lists) {
            for (SimpleWaypoint waypoint : list.waypoints()) {
                flatWaypoints.add(new DisplayWaypoint(list.sourceList(), waypoint));
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

    record Display(boolean groupByLists, List<DisplayList> lists, List<DisplayWaypoint> flatWaypoints) {
    }

    record DisplayList(WaypointList sourceList, List<SimpleWaypoint> waypoints) {
        private static final Comparator<DisplayList> BY_LIST_NAME = Comparator.comparing(
                        (DisplayList list) -> list.sourceList().name(),
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(list -> list.sourceList().name());
    }

    record DisplayWaypoint(WaypointList sourceList, SimpleWaypoint waypoint) {
        static final Comparator<DisplayWaypoint> BY_WAYPOINT_NAME = Comparator.comparing(
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
