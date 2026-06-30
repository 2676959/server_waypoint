package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WaypointQueryEngineTest {
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
        addDimension(filesManager, "minecraft:overworld", list("bases", waypoint("base", 0, 0, 0), waypoint("base", 1, 0, 0)));
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
    void filterDoesNotMatchListNames() {
        WaypointFilesManagerCore filesManager = createFilesManager();
        addDimension(filesManager, "minecraft:overworld", list("homes", waypoint("storage", 0, 0, 0)));

        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine(filesManager).queryDimension(
                "minecraft:overworld",
                new WaypointQueryEngine.Query("hoems", WaypointSorting.SortMode.NAME, null)
        );

        assertEquals(0, result.listCount());
        assertEquals(0, result.waypointCount());
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
        WaypointFileManager fileManager = filesManager.getOrCreateWaypointFileManager(dimensionName);
        for (WaypointList waypointList : waypointLists) {
            fileManager.addWaypointList(waypointList);
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
