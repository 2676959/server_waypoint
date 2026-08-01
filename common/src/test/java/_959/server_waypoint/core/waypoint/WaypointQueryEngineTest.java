package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointQueryEngineTest {
    @Test
    void selectedListUsesTheSameNameFilterAndSortingPipeline() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld",
                list("bases", waypoint("far base", 10, 0, 0), waypoint("near base", 1, 0, 0)),
                list("villages", waypoint("desert village", 5, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryList(
                "minecraft:overworld",
                "bases",
                new WaypointQueryEngine.Query(
                        "base",
                        WaypointSorting.SortMode.DISTANCE,
                        new WaypointPos(0, 0, 0)
                )
        );

        assertEquals(1, result.listCount());
        assertEquals(List.of("bases"), listNames(result.dimensions().get(0)));
        assertEquals(List.of("near base", "far base"), waypointNames(result.dimensions().get(0).lists().get(0)));
    }

    @Test
    void selectedListReturnsNoResultWhenItsWaypointsDoNotMatch() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld",
                list("bases", waypoint("home", 0, 0, 0)),
                list("villages", waypoint("desert village", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryList(
                "minecraft:overworld",
                "bases",
                new WaypointQueryEngine.Query("village", WaypointSorting.SortMode.NAME, null)
        );

        assertTrue(result.isEmpty());
        assertEquals(0, result.waypointCount());
    }

    @TempDir
    private Path tempDir;

    @Test
    void selectedDimensionQueryFiltersAndSortsWaypointResults() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        WaypointList bases = list("bases", waypoint("far base", 10, 0, 0), waypoint("near base", 1, 0, 0));
        WaypointList villages = list("villages", waypoint("desert village", 5, 0, 0));
        addDimension(filesManager, "minecraft:overworld", bases, villages);

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("base", WaypointSorting.SortMode.DISTANCE, new WaypointPos(0, 0, 0))
        );

        assertEquals(List.of("minecraft:overworld"), dimensionNames(result));
        assertEquals(List.of("bases"), listNames(result.dimensions().get(0)));
        assertEquals(List.of("near base", "far base"), waypointNames(result.dimensions().get(0).lists().get(0)));
        assertSame(bases, result.dimensions().get(0).lists().get(0).sourceList());
    }

    @Test
    void allDimensionQueryPreservesDimensionGrouping() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:the_nether", list("portals", waypoint("nether base", 0, 0, 0)));
        addDimension(filesManager, "minecraft:overworld", list("homes", waypoint("overworld base", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryAll(
                new WaypointQueryEngine.Query("base", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), dimensionNames(result));
        assertEquals(List.of("homes"), listNames(result.dimensions().get(0)));
        assertEquals(List.of("portals"), listNames(result.dimensions().get(1)));
        assertEquals(2, result.waypointCount());
    }

    @Test
    void distanceQuerySortsOnlyCurrentAndConvertibleDimensions() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:the_nether", list(
                "nether",
                waypoint("nether far", 10, 0, 0),
                waypoint("nether near", 1, 0, 0)
        ));
        addDimension(filesManager, "minecraft:the_end", list(
                "end",
                waypoint("end default first", 100, 0, 0),
                waypoint("end default second", 1, 0, 0)
        ));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryAll(
                new WaypointQueryEngine.Query(
                        "",
                        WaypointSorting.SortMode.DISTANCE,
                        new WaypointPos(0, 0, 0),
                        "minecraft:overworld",
                        false
                )
        );

        assertEquals(
                List.of("nether near", "nether far"),
                waypointNames(result.dimensions().get(0).lists().get(0))
        );
        assertEquals(
                List.of("end default first", "end default second"),
                waypointNames(result.dimensions().get(1).lists().get(0))
        );
    }

    @Test
    void emptyFilterWithSortIncludesAllWaypoints() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", list("bases", waypoint("zeta", 0, 0, 0), waypoint("Alpha", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(List.of("Alpha", "zeta"), waypointNames(result.dimensions().get(0).lists().get(0)));
    }

    @Test
    void searchSuggestionsComeFromSelectedDimensionWithoutDuplicates() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", new WaypointList(
                "bases",
                "{\"text\":\"bases\",\"color\":\"gold\"}",
                1,
                List.of(
                new SimpleWaypoint(
                        "base",
                        "{\"text\":\"base\",\"bold\":true}",
                        "b",
                        new WaypointPos(0, 0, 0),
                        0xFFFFFF,
                        0,
                        false,
                        List.of(),
                        ""
                ),
                waypoint("base", 1, 0, 0)
                )
        ));
        addDimension(filesManager, "minecraft:the_nether", list("nether", waypoint("nether base", 0, 0, 0)));

        List<String> suggestions = new WaypointQueryEngine(filesManager).getSearchSuggestions("minecraft:overworld");

        assertEquals(List.of("bases", "base", "b"), suggestions);
    }

    @Test
    void fuzzyFilterMatchesWaypointNames() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", list("bases", waypoint("desert village", 0, 0, 0), waypoint("jungle temple", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("vilage", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(1, result.waypointCount());
        assertEquals(List.of("desert village"), waypointNames(result.dimensions().get(0).lists().get(0)));
    }

    @Test
    void filterMatchesListNames() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", list("homes", waypoint("storage", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("hoems", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(1, result.listCount());
        assertEquals(1, result.waypointCount());
    }

    @Test
    void filterMatchesKeywordsButNotFormattedDisplayNames() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Port",
                "{\"text\":\"Hidden raw\",\"extra\":[{\"text\":\" Harbor\"}]}",
                "H",
                new WaypointPos(0, 0, 0),
                0xFFFFFF,
                0,
                false,
                List.of("ships", "trading post"),
                ""
        );
        addDimension(filesManager, "minecraft:overworld", list("ports", waypoint));

        WaypointQueryEngine queryEngine = new WaypointQueryEngine(filesManager);

        assertEquals(0, queryEngine.queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("harbor", WaypointSorting.SortMode.NAME, null)
        ).waypointCount());
        assertEquals(1, queryEngine.queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("trading", WaypointSorting.SortMode.NAME, null)
        ).waypointCount());
    }

    @Test
    void filterDoesNotMatchWaypointInitials() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", list("bases", waypoint("desert village", "dv", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("dv", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(0, result.listCount());
        assertEquals(0, result.waypointCount());
    }

    @Test
    void filterDoesNotMatchWaypointCoordinates() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", list("bases", waypoint("base", 123, 64, 456)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("123", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(0, result.waypointCount());
    }

    private WaypointFilesManagerCore createFilesManager() {
        return new WaypointFilesManagerCore(this.tempDir);
    }

    private static void addDimension(WaypointFilesManagerCore filesManager, String dimensionName, WaypointList... waypointLists) {
        for (WaypointList waypointList : waypointLists) {
            filesManager.putWaypointList(dimensionName, waypointList);
        }
    }

    private static WaypointList list(String name, SimpleWaypoint... waypoints) {
        return new WaypointList(name, 1, List.of(waypoints));
    }

    private static SimpleWaypoint waypoint(String name, int x, int y, int z) {
        return new SimpleWaypoint(name, name.substring(0, 1), x, y, z, 0xFFFFFF, 0, false);
    }

    private static SimpleWaypoint waypoint(String name, String initials, int x, int y, int z) {
        return new SimpleWaypoint(name, initials, x, y, z, 0xFFFFFF, 0, false);
    }

    private static List<String> dimensionNames(WaypointQueryEngine.QueryResult result) {
        return result.dimensions().stream()
                .map(WaypointQueryEngine.DimensionResult::dimensionName)
                .toList();
    }

    private static List<String> listNames(WaypointQueryEngine.DimensionResult result) {
        return result.lists().stream()
                .map(WaypointQueryEngine.ListResult::listName)
                .toList();
    }

    private static List<String> waypointNames(WaypointQueryEngine.ListResult result) {
        return result.waypoints().stream()
                .map(SimpleWaypoint::name)
                .toList();
    }
}
