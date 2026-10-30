package _959.server_waypoint.common.client;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientWaypointQueryTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private static final String DIM = "minecraft:overworld";

    private ClientWaypointCollections collections() {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        collections.bindServer(directory, 12);
        collections.bindLocal(UUID.randomUUID(), new WaypointFilesManagerCore(directory.resolve("local")));
        for (var source : ClientWaypointSource.values()) {
            collections.markSnapshotApplied(source);
            collections.files(source).putWaypointList(DIM, new WaypointList("Homes", "Homes", 7,
                    List.of(new SimpleWaypoint("Base", "B", new WaypointPos(1, 64, 1), 0x123456, 0, false))));
        }
        return collections;
    }

    private WaypointQueryEngine.Query options(String search, WaypointSorting.SortMode sort, boolean reversed) {
        return new WaypointQueryEngine.Query(search, sort, new WaypointPos(0, 64, 0), DIM, reversed);
    }

    @Test
    void combinedKeepsEqualIdentifiersAsThreeDistinctSelectionsAndImmutableGroups() {
        var collections = collections();
        var result = new ClientWaypointQuery(collections).query(ClientWaypointQuery.View.COMBINED, null,
                options("", WaypointSorting.SortMode.NAME, false));
        assertEquals(3, result.lists().size());
        assertEquals(3, result.rows().size());
        assertEquals(3, result.rows().stream().map(ClientWaypointQuery.Row::selection).distinct().count());
        assertEquals(List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE, ClientWaypointSource.LOCAL),
                result.rows().stream().map(row -> row.selection().source()).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.lists().get(0).rows().clear());
        assertEquals(1, collections.files(ClientWaypointSource.SHARED).getWaypointFileManager(DIM).getWaypointLists().size());
    }

    @Test
    void searchDimensionsAndSingleViewUseOnlySelectedReadySources() {
        var collections = collections();
        collections.files(ClientWaypointSource.LOCAL).putWaypointList("local:moon", WaypointList.build("Moon", 1));
        var query = new ClientWaypointQuery(collections);
        assertEquals(List.of(DIM, "local:moon"), query.dimensions(ClientWaypointQuery.View.COMBINED));
        assertEquals(List.of(DIM), query.dimensions(ClientWaypointQuery.View.SHARED));
        assertEquals(1, query.query(ClientWaypointQuery.View.PRIVATE, DIM,
                options("Base", WaypointSorting.SortMode.NAME, false)).rows().size());
        assertTrue(query.query(ClientWaypointQuery.View.COMBINED, DIM,
                options("missing", WaypointSorting.SortMode.NAME, false)).rows().isEmpty());
        collections.serverUnavailable();
        assertEquals(1, query.query(ClientWaypointQuery.View.COMBINED, DIM,
                options("", WaypointSorting.SortMode.NAME, false)).rows().size());
        assertTrue(query.query(ClientWaypointQuery.View.SHARED, DIM,
                options("", WaypointSorting.SortMode.NAME, false)).rows().isEmpty());
    }

    @Test
    void suggestionsRetainAllSourcesEvenForEqualText() {
        var query = new ClientWaypointQuery(collections());
        assertEquals(List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE, ClientWaypointSource.LOCAL),
                query.suggestions(ClientWaypointQuery.View.COMBINED, DIM).stream()
                        .filter(suggestion -> suggestion.text().equals("Base")).map(ClientWaypointQuery.Suggestion::source).toList());
        assertTrue(query.suggestions(ClientWaypointQuery.View.LOCAL, DIM).stream()
                .allMatch(suggestion -> suggestion.source() == ClientWaypointSource.LOCAL));
    }

    @Test
    void distanceSortKeepsUnrelatedDimensionsAfterComparableRowsInBothDirections() {
        var collections = collections();
        collections.files(ClientWaypointSource.LOCAL).putWaypointList("minecraft:the_end", new WaypointList("End", 7,
                List.of(new SimpleWaypoint("End", "E", new WaypointPos(0, 64, 0), 0, 0, false))));
        collections.files(ClientWaypointSource.LOCAL).putWaypointList("minecraft:the_nether", new WaypointList("Nether", 7,
                List.of(new SimpleWaypoint("Nether", "N", new WaypointPos(1, 64, 1), 0, 0, false))));
        var query = new ClientWaypointQuery(collections);
        for (boolean reversed : new boolean[] {false, true}) {
            var rows = query.query(ClientWaypointQuery.View.COMBINED, null, options("", WaypointSorting.SortMode.DISTANCE, reversed)).rows();
            assertEquals("End", rows.get(4).waypoint().name());
            assertEquals("Nether", rows.get(reversed ? 0 : 3).waypoint().name());
            assertEquals(List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE, ClientWaypointSource.LOCAL),
                    rows.stream().filter(row -> row.waypoint().name().equals("Base")).map(row -> row.selection().source()).toList());
        }
    }

    @Test
    void reversedFlatSortRetainsSourceOrderForEqualNamesAndSortsAcrossStores() {
        var collections = collections();
        collections.files(ClientWaypointSource.LOCAL).addWaypoint(DIM, "Homes",
                new SimpleWaypoint("Zoo", "Z", new WaypointPos(2, 64, 2), 0x123456, 0, false), ignored -> {});
        var result = new ClientWaypointQuery(collections).query(ClientWaypointQuery.View.COMBINED, DIM,
                options("", WaypointSorting.SortMode.NAME, true));
        assertEquals(List.of("Zoo", "Base", "Base", "Base"), result.rows().stream().map(row -> row.waypoint().name()).toList());
        assertEquals(List.of(ClientWaypointSource.LOCAL, ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE, ClientWaypointSource.LOCAL),
                result.rows().stream().map(row -> row.selection().source()).toList());
    }
}
