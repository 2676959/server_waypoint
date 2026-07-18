package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointListPageTest {
    private static final int PAGE_LIMIT = 10;

    @Test
    void exactLimitStaysOnOnePage() {
        WaypointListPage.Page page = WaypointListPage.paginate(
                display(displayList("minecraft:overworld", "bases", waypoints("base", 10))),
                1,
                PAGE_LIMIT
        );

        assertEquals(1, page.pageNumber());
        assertEquals(1, page.totalPages());
        assertEquals(10, page.totalWaypoints());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals(names("base", 10), waypointNames(page));
    }

    @Test
    void resultPastLimitIsSplitWithoutDuplicates() {
        WaypointListDisplayModel.Display display = display(
                displayList("minecraft:overworld", "bases", waypoints("base", 7)),
                displayList("minecraft:the_nether", "portals", waypoints("portal", 5))
        );

        WaypointListPage.Page first = WaypointListPage.paginate(display, 1, PAGE_LIMIT);
        WaypointListPage.Page second = WaypointListPage.paginate(display, 2, PAGE_LIMIT);

        assertEquals(2, first.totalPages());
        assertTrue(first.hasNext());
        assertEquals(names("base", 7), waypointNames(first).subList(0, 7));
        assertEquals(List.of("portal 1", "portal 2", "portal 3"), waypointNames(first).subList(7, 10));
        assertEquals(List.of("portal 4", "portal 5"), waypointNames(second));
        assertEquals(List.of("bases", "portals"), listNames(first));
        assertEquals(List.of("portals"), listNames(second));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), dimensionNames(first));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), dimensionNames(second));
        assertTrue(second.dimensions().get(0).lists().isEmpty());
        assertFalse(second.dimensions().get(1).lists().isEmpty());
    }

    @Test
    void customLimitAndOversizedPageUseTheLastAvailablePage() {
        WaypointListPage.Page page = WaypointListPage.paginate(
                display(displayList("minecraft:overworld", "bases", waypoints("base", 8))),
                99,
                3
        );

        assertEquals(3, page.pageNumber());
        assertEquals(3, page.totalPages());
        assertEquals(3, page.limit());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals(List.of("base 7", "base 8"), waypointNames(page));
    }

    @Test
    void emptyListsRemainVisibleWithoutConsumingWaypointRows() {
        WaypointListDisplayModel.Display display = display(
                displayList("minecraft:overworld", "leading empty", List.of()),
                displayList("minecraft:overworld", "bases", waypoints("base", 10)),
                displayList("minecraft:overworld", "trailing empty", List.of())
        );

        WaypointListPage.Page page = WaypointListPage.paginate(
                display,
                1,
                PAGE_LIMIT
        );

        assertEquals(10, page.totalWaypoints());
        assertEquals(List.of("leading empty", "bases", "trailing empty"), listNames(page));
        assertEquals(names("base", 10), waypointNames(page));
    }

    @Test
    void flatWaypointsArePaginatedWithoutLosingTheirSourceMetadata() {
        WaypointList sourceList = new WaypointList("bases", 1, waypoints("base", 12));
        List<WaypointListDisplayModel.DisplayWaypoint> flatWaypoints = sourceList.simpleWaypoints().stream()
                .map(waypoint -> new WaypointListDisplayModel.DisplayWaypoint(
                        "minecraft:overworld",
                        sourceList,
                        waypoint
                ))
                .toList();
        WaypointListDisplayModel.Display display = new WaypointListDisplayModel.Display(
                false,
                List.of(),
                flatWaypoints
        );

        WaypointListPage.Page page = WaypointListPage.paginate(display, 2, PAGE_LIMIT);

        assertFalse(page.groupByLists());
        assertEquals(2, page.pageNumber());
        assertEquals(2, page.totalPages());
        assertEquals(12, page.totalWaypoints());
        assertEquals(List.of("base 11", "base 12"), page.flatWaypoints().stream()
                .map(row -> row.waypoint().name())
                .toList());
        assertEquals(List.of("bases", "bases"), page.flatWaypoints().stream()
                .map(row -> row.sourceList().name())
                .toList());
        assertFalse(page.display().groupByLists());
    }

    @Test
    void treePagesKeepEveryDimensionInTheOriginalOrder() {
        WaypointListDisplayModel.Display display = display(
                displayList("dim0", "list0", waypoints("wp0", 1)),
                displayList("dim1", "list1", waypoints("wp1", 1)),
                displayList("dim2", "list2", waypoints("wp2", 1)),
                displayList("dim3", "list3", waypoints("wp3", 1))
        );

        WaypointListPage.Page page = WaypointListPage.paginate(display, 2, 1);

        assertEquals(List.of("dim0", "dim1", "dim2", "dim3"), dimensionNames(page));
        assertEquals(List.of(true, false, true, true), page.dimensions().stream()
                .map(dimension -> dimension.lists().isEmpty())
                .toList());
        assertEquals(List.of("wp1 1"), waypointNames(page));
    }

    @Test
    void rejectsInvalidPageAndLimit() {
        WaypointListDisplayModel.Display display = display();

        assertThrows(IllegalArgumentException.class, () -> WaypointListPage.paginate(display, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> WaypointListPage.paginate(display, 1, 0));
    }

    private static WaypointListDisplayModel.Display display(WaypointListDisplayModel.DisplayList... lists) {
        return new WaypointListDisplayModel.Display(true, List.of(lists), List.of());
    }

    private static WaypointListDisplayModel.DisplayList displayList(
            String dimensionName,
            String listName,
            List<SimpleWaypoint> waypoints
    ) {
        return new WaypointListDisplayModel.DisplayList(
                dimensionName,
                new WaypointList(listName, 1, new ArrayList<>(waypoints)),
                waypoints
        );
    }

    private static List<SimpleWaypoint> waypoints(String prefix, int count) {
        List<SimpleWaypoint> waypoints = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            waypoints.add(new SimpleWaypoint(
                    prefix + " " + index,
                    Integer.toString(index),
                    new WaypointPos(index, 64, 0),
                    0xFFFFFF,
                    0,
                    false
            ));
        }
        return waypoints;
    }

    private static List<String> names(String prefix, int count) {
        List<String> names = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            names.add(prefix + " " + index);
        }
        return names;
    }

    private static List<String> waypointNames(WaypointListPage.Page page) {
        return page.lists().stream()
                .flatMap(list -> list.waypoints().stream())
                .map(SimpleWaypoint::name)
                .toList();
    }

    private static List<String> listNames(WaypointListPage.Page page) {
        return page.lists().stream()
                .map(list -> list.sourceList().name())
                .toList();
    }

    private static List<String> dimensionNames(WaypointListPage.Page page) {
        return page.dimensions().stream()
                .map(WaypointListDisplayModel.DisplayDimension::dimensionName)
                .toList();
    }
}
