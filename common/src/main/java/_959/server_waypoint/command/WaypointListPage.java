package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;

import java.util.ArrayList;
import java.util.List;

final class WaypointListPage {
    private WaypointListPage() {
    }

    static Page paginate(WaypointListDisplayModel.Display display, int requestedPage, int limit) {
        if (!display.groupByLists()) {
            throw new IllegalArgumentException("Waypoint command pages require grouped display data");
        }
        if (requestedPage < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
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

        return new Page(pageNumber, totalPages, limit, totalWaypoints, pageLists);
    }

    record Page(
            int pageNumber,
            int totalPages,
            int limit,
            int totalWaypoints,
            List<WaypointListDisplayModel.DisplayList> lists
    ) {
        Page {
            lists = List.copyOf(lists);
        }

        boolean hasPrevious() {
            return this.pageNumber > 1;
        }

        boolean hasNext() {
            return this.pageNumber < this.totalPages;
        }

        WaypointListDisplayModel.Display display() {
            return new WaypointListDisplayModel.Display(true, this.lists, List.of());
        }
    }
}
