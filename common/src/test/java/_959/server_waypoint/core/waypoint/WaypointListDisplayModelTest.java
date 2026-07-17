package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.util.ColorUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointListDisplayModelTest {
    @Test
    void defaultSortForcesGroupedLists() {
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.DEFAULT,
                null,
                listResult(list("zeta", waypoint("b", 0, 0, 0)))
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertTrue(display.groupByLists());
        assertEquals(List.of("zeta"), listNames(display));
    }

    @Test
    void nameSortGroupedSortsListsAndWaypointsByName() {
        WaypointList alpha = list("alpha", waypoint("zeta", 0, 0, 0), waypoint("Beta", 0, 0, 0));
        WaypointList gamma = list("gamma", waypoint("delta", 0, 0, 0));
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.NAME,
                null,
                listResult(gamma),
                listResult(alpha)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, true);

        assertTrue(display.groupByLists());
        assertEquals(List.of("alpha", "gamma"), listNames(display));
        assertEquals(List.of("Beta", "zeta"), waypointNames(display.lists().get(0)));
    }

    @Test
    void nameSortFlatSortsAllWaypointsAndKeepsSourceListNames() {
        WaypointList mines = list("mines", waypoint("zeta", 0, 0, 0), waypoint("Alpha", 0, 0, 0));
        WaypointList bases = list("bases", waypoint("mid", 0, 0, 0));
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.NAME,
                null,
                listResult(mines),
                listResult(bases)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(List.of("Alpha", "mid", "zeta"), flatWaypointNames(display));
        assertEquals(List.of("mines", "bases", "mines"), flatListNames(display));
    }

    @Test
    void nameSortFlatCanReverseAllWaypoints() {
        WaypointList mines = list("mines", waypoint("zeta", 0, 0, 0), waypoint("Alpha", 0, 0, 0));
        WaypointList bases = list("bases", waypoint("mid", 0, 0, 0));
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.NAME,
                null,
                true,
                listResult(mines),
                listResult(bases)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(List.of("zeta", "mid", "Alpha"), flatWaypointNames(display));
        assertEquals(List.of("mines", "bases", "mines"), flatListNames(display));
    }

    @Test
    void distanceSortGroupedPreservesListOrderAndSortsWaypointsInsideEachList() {
        WaypointList first = list("first", waypoint("far", 10, 0, 0), waypoint("near", 1, 0, 0));
        WaypointList second = list("second", waypoint("middle", 5, 0, 0));
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.DISTANCE,
                new WaypointPos(0, 0, 0),
                listResult(first),
                listResult(second)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, true);

        assertEquals(List.of("first", "second"), listNames(display));
        assertEquals(List.of("near", "far"), waypointNames(display.lists().get(0)));
    }

    @Test
    void distanceSortFlatSortsAllWaypointsByDistance() {
        WaypointList first = list("first", waypoint("far", 10, 0, 0));
        WaypointList second = list("second", waypoint("near", 1, 0, 0));
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.DISTANCE,
                new WaypointPos(0, 0, 0),
                listResult(first),
                listResult(second)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(List.of("near", "far"), flatWaypointNames(display));
        assertEquals(List.of("second", "first"), flatListNames(display));
    }

    @Test
    void colorSortFlatUsesOneGlobalColorOrder() {
        WaypointList first = list("first", waypoint("white", 0xFFFFFF), waypoint("red", 0xFF0000));
        WaypointList second = list("second", waypoint("blue", 0x0000FF));
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.COLOR,
                null,
                listResult(first),
                listResult(second)
        );
        List<WaypointListDisplayModel.DisplayWaypoint> expected = new ArrayList<>();
        expected.add(new WaypointListDisplayModel.DisplayWaypoint(first, first.simpleWaypoints().get(0)));
        expected.add(new WaypointListDisplayModel.DisplayWaypoint(first, first.simpleWaypoints().get(1)));
        expected.add(new WaypointListDisplayModel.DisplayWaypoint(second, second.simpleWaypoints().get(0)));
        ColorUtils.sortWaypointColors(expected, row -> row.waypoint().rgb(), WaypointListDisplayModel.DisplayWaypoint.BY_WAYPOINT_NAME);

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(expected.stream().map(row -> row.waypoint().name()).toList(), flatWaypointNames(display));
        assertEquals(expected.stream().map(row -> row.sourceList().name()).toList(), flatListNames(display));
    }

    @Test
    void groupedAllDimensionsRetainsDimensionRootsAndTheirLists() {
        WaypointList overworldZeta = list("zeta", waypoint("overworld", 0, 0, 0));
        WaypointList overworldAlpha = list("alpha", waypoint("home", 0, 0, 0));
        WaypointList netherBases = list("bases", waypoint("portal", 0, 0, 0));
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(
                List.of(
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:overworld",
                                List.of(listResult(overworldZeta), listResult(overworldAlpha))
                        ),
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:the_nether",
                                List.of(listResult(netherBases))
                        )
                ),
                new WaypointQueryEngine.Query("", WaypointSorting.SortMode.NAME, null)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, true);

        assertEquals(
                List.of("minecraft:overworld", "minecraft:the_nether"),
                display.dimensions().stream()
                        .map(WaypointListDisplayModel.DisplayDimension::dimensionName)
                        .toList()
        );
        assertEquals(
                List.of("alpha", "zeta"),
                display.dimensions().get(0).lists().stream()
                        .map(row -> row.sourceList().name())
                        .toList()
        );
        assertEquals(
                List.of("bases"),
                display.dimensions().get(1).lists().stream()
                        .map(row -> row.sourceList().name())
                        .toList()
        );
    }

    @Test
    void flatAllDimensionsRetainsDimensionAndListNames() {
        WaypointList overworldBases = list("bases", waypoint("zeta", 0, 0, 0));
        WaypointList netherBases = list("bases", waypoint("Alpha", 0, 0, 0));
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(
                List.of(
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:overworld",
                                List.of(listResult(overworldBases))
                        ),
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:the_nether",
                                List.of(listResult(netherBases))
                        )
                ),
                new WaypointQueryEngine.Query("", WaypointSorting.SortMode.NAME, null)
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(List.of("Alpha", "zeta"), flatWaypointNames(display));
        assertEquals(List.of("bases", "bases"), flatListNames(display));
        assertEquals(
                List.of("minecraft:the_nether", "minecraft:overworld"),
                display.flatWaypoints().stream()
                        .map(WaypointListDisplayModel.DisplayWaypoint::dimensionName)
                        .toList()
        );
    }

    @Test
    void flatAllDimensionsDistanceSortConvertsWaypointsToTheCurrentDimension() {
        WaypointList overworldBases = list("overworld", waypoint("overworld", 10, 0, 0));
        WaypointList netherBases = list("nether", waypoint("nether", 2, 0, 0));
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(
                List.of(
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:overworld",
                                List.of(listResult(overworldBases))
                        ),
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:the_nether",
                                List.of(listResult(netherBases))
                        )
                ),
                new WaypointQueryEngine.Query(
                        "",
                        WaypointSorting.SortMode.DISTANCE,
                        new WaypointPos(0, 0, 0),
                        "minecraft:overworld",
                        false
                )
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(List.of("overworld", "nether"), flatWaypointNames(display));
    }

    @Test
    void groupedAllDimensionsDistanceSortConvertsWaypointCoordinates() {
        WaypointList netherBases = list(
                "nether",
                waypoint("horizontal", 10, 100, 0),
                waypoint("vertical", 1, 80, 0)
        );
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(
                List.of(new WaypointQueryEngine.DimensionResult(
                        "minecraft:the_nether",
                        List.of(listResult(netherBases))
                )),
                new WaypointQueryEngine.Query(
                        "",
                        WaypointSorting.SortMode.DISTANCE,
                        new WaypointPos(0, 100, 0),
                        "minecraft:overworld",
                        false
                )
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, true);

        assertEquals(List.of("vertical", "horizontal"), waypointNames(display.lists().get(0)));
    }

    @Test
    void flatAllDimensionsDistanceSortConvertsOverworldWaypointsToTheNether() {
        WaypointList overworldBases = list("overworld", waypoint("overworld", 16, 0, 0));
        WaypointList netherBases = list("nether", waypoint("nether", 10, 0, 0));
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(
                List.of(
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:overworld",
                                List.of(listResult(overworldBases))
                        ),
                        new WaypointQueryEngine.DimensionResult(
                                "minecraft:the_nether",
                                List.of(listResult(netherBases))
                        )
                ),
                new WaypointQueryEngine.Query(
                        "",
                        WaypointSorting.SortMode.DISTANCE,
                        new WaypointPos(0, 0, 0),
                        "minecraft:the_nether",
                        false
                )
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertEquals(List.of("overworld", "nether"), flatWaypointNames(display));
    }

    private static WaypointQueryEngine.QueryResult result(
            WaypointSorting.SortMode sortMode,
            WaypointPos origin,
            WaypointQueryEngine.ListResult... lists
    ) {
        return result(sortMode, origin, false, lists);
    }

    private static WaypointQueryEngine.QueryResult result(
            WaypointSorting.SortMode sortMode,
            WaypointPos origin,
            boolean reversed,
            WaypointQueryEngine.ListResult... lists
    ) {
        return new WaypointQueryEngine.QueryResult(
                List.of(new WaypointQueryEngine.DimensionResult("minecraft:overworld", List.of(lists))),
                new WaypointQueryEngine.Query("", sortMode, origin, reversed)
        );
    }

    private static WaypointQueryEngine.ListResult listResult(WaypointList list) {
        return new WaypointQueryEngine.ListResult(list, list.simpleWaypoints(), true);
    }

    private static WaypointList list(String name, SimpleWaypoint... waypoints) {
        return new WaypointList(name, 1, List.of(waypoints));
    }

    private static SimpleWaypoint waypoint(String name, int x, int y, int z) {
        return new SimpleWaypoint(name, name.substring(0, 1), x, y, z, 0xFFFFFF, 0, false);
    }

    private static SimpleWaypoint waypoint(String name, int rgb) {
        return new SimpleWaypoint(name, name.substring(0, 1), 0, 0, 0, rgb, 0, false);
    }

    private static List<String> listNames(WaypointListDisplayModel.Display display) {
        return display.lists().stream()
                .map(row -> row.sourceList().name())
                .toList();
    }

    private static List<String> waypointNames(WaypointListDisplayModel.DisplayList list) {
        return list.waypoints().stream()
                .map(SimpleWaypoint::name)
                .toList();
    }

    private static List<String> flatWaypointNames(WaypointListDisplayModel.Display display) {
        return display.flatWaypoints().stream()
                .map(row -> row.waypoint().name())
                .toList();
    }

    private static List<String> flatListNames(WaypointListDisplayModel.Display display) {
        return display.flatWaypoints().stream()
                .map(row -> row.sourceList().name())
                .toList();
    }
}
