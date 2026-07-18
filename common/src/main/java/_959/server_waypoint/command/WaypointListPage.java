package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;

import java.util.ArrayList;
import java.util.List;

final class WaypointListPage {
    private WaypointListPage() {
    }

    static Page paginate(WaypointListDisplayModel.Display display, int requestedPage, int limit) {
        if (requestedPage < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        if (!display.groupByLists()) {
            return paginateFlat(display, requestedPage, limit);
        }

        int totalWaypoints = display.lists().stream()
                .mapToInt(list -> list.waypoints().size())
                .sum();
        int totalPages = totalWaypoints == 0 ? 1 : ((totalWaypoints - 1) / limit) + 1;
        int pageNumber = Math.min(requestedPage, totalPages);
        long start = (long) (pageNumber - 1) * limit;
        long end = Math.min(start + limit, totalWaypoints);
        long listStart = 0;
        List<WaypointListDisplayModel.DisplayList> pageLists = new ArrayList<>();

        for (WaypointListDisplayModel.DisplayList list : display.lists()) {
            if (list.waypoints().isEmpty()) {
                int emptyListPage = Math.min((int) (listStart / limit) + 1, totalPages);
                if (pageNumber == emptyListPage) {
                    pageLists.add(new WaypointListDisplayModel.DisplayList(
                            list.dimensionName(),
                            list.sourceList(),
                            List.of()
                    ));
                }
                continue;
            }
            long listEnd = listStart + list.waypoints().size();
            long sliceStart = Math.max(start, listStart);
            long sliceEnd = Math.min(end, listEnd);
            if (sliceStart < sliceEnd) {
                int fromIndex = (int) (sliceStart - listStart);
                int toIndex = (int) (sliceEnd - listStart);
                pageLists.add(new WaypointListDisplayModel.DisplayList(
                        list.dimensionName(),
                        list.sourceList(),
                        List.copyOf(list.waypoints().subList(fromIndex, toIndex))
                ));
            }
            listStart = listEnd;
        }

        return new Page(
                true,
                pageNumber,
                totalPages,
                limit,
                totalWaypoints,
                pageLists,
                List.of(),
                pageDimensions(display, pageLists)
        );
    }

    private static List<WaypointListDisplayModel.DisplayDimension> pageDimensions(
            WaypointListDisplayModel.Display display,
            List<WaypointListDisplayModel.DisplayList> pageLists
    ) {
        List<WaypointListDisplayModel.DisplayDimension> dimensions = new ArrayList<>();
        for (WaypointListDisplayModel.DisplayDimension dimension : display.dimensions()) {
            List<WaypointListDisplayModel.DisplayList> dimensionLists = pageLists.stream()
                    .filter(list -> list.dimensionName().equals(dimension.dimensionName()))
                    .toList();
            dimensions.add(new WaypointListDisplayModel.DisplayDimension(
                    dimension.dimensionName(),
                    dimensionLists
            ));
        }
        return dimensions;
    }

    private static Page paginateFlat(
            WaypointListDisplayModel.Display display,
            int requestedPage,
            int limit
    ) {
        int totalWaypoints = display.flatWaypoints().size();
        int totalPages = totalWaypoints == 0 ? 1 : ((totalWaypoints - 1) / limit) + 1;
        int pageNumber = Math.min(requestedPage, totalPages);
        int start = Math.min((pageNumber - 1) * limit, totalWaypoints);
        int end = Math.min(start + limit, totalWaypoints);
        return new Page(
                false,
                pageNumber,
                totalPages,
                limit,
                totalWaypoints,
                List.of(),
                List.copyOf(display.flatWaypoints().subList(start, end)),
                List.of()
        );
    }

    record Page(
            boolean groupByLists,
            int pageNumber,
            int totalPages,
            int limit,
            int totalWaypoints,
            List<WaypointListDisplayModel.DisplayList> lists,
            List<WaypointListDisplayModel.DisplayWaypoint> flatWaypoints,
            List<WaypointListDisplayModel.DisplayDimension> dimensions
    ) {
        Page {
            lists = List.copyOf(lists);
            flatWaypoints = List.copyOf(flatWaypoints);
            dimensions = dimensions.stream()
                    .map(dimension -> new WaypointListDisplayModel.DisplayDimension(
                            dimension.dimensionName(),
                            List.copyOf(dimension.lists())
                    ))
                    .toList();
        }

        boolean hasPrevious() {
            return this.pageNumber > 1;
        }

        boolean hasNext() {
            return this.pageNumber < this.totalPages;
        }

        WaypointListDisplayModel.Display display() {
            return new WaypointListDisplayModel.Display(
                    this.groupByLists,
                    this.lists,
                    this.flatWaypoints
            );
        }
    }
}
