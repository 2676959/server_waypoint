package _959.server_waypoint.crossserver;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RemoteCatalogSnapshotTest {
    private static final RemoteServerId SERVER = new RemoteServerId("survival");
    private static final RemoteRevision ZERO = new RemoteRevision(0);

    @Test
    void defensivelyCopiesEveryCollectionLayer() {
        var keywords = new ArrayList<>(List.of(" One ", "世界"));
        var waypoint = waypoint(keywords);
        var waypoints = new HashMap<>(Map.of("Café", waypoint));
        var list = new RemoteListSnapshot("Display", ZERO, waypoints);
        var lists = new HashMap<>(Map.of(" List ", list));
        var dimensions = new HashMap<String, Map<String, RemoteListSnapshot>>();
        dimensions.put("custom:世界", lists);
        var snapshot = snapshot(0, dimensions);
        keywords.clear();
        waypoints.clear();
        lists.clear();
        dimensions.clear();

        var key = new RemoteWaypointKey(SERVER, "custom:世界", " List ", "Café");
        assertEquals(waypoint, snapshot.find(key).orElseThrow());
        assertEquals(List.of(" One ", "世界"), waypoint.keywords());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.dimensions().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.dimensions().get("custom:世界").clear());
        assertThrows(UnsupportedOperationException.class, () -> list.waypoints().clear());
        assertThrows(UnsupportedOperationException.class, () -> waypoint.keywords().clear());
    }

    @Test
    void lookupUsesExactHierarchyAndServerRatherThanDisplayNames() {
        var waypoint = waypoint(List.of());
        var snapshot = snapshot(2, Map.of("", Map.of(" List ",
                new RemoteListSnapshot("label", ZERO, Map.of("Café", waypoint, "", waypoint)))));
        assertEquals(waypoint, snapshot.find(new RemoteWaypointKey(SERVER, "", " List ", "Café")).orElseThrow());
        assertTrue(snapshot.find(new RemoteWaypointKey(SERVER, "", " List ", "")).isPresent());
        for (String name : List.of("Cafe\u0301", "café", "Display", "missing")) {
            assertTrue(snapshot.find(new RemoteWaypointKey(SERVER, "", " List ", name)).isEmpty());
        }
        assertTrue(snapshot.find(new RemoteWaypointKey(SERVER, "", "label", "Café")).isEmpty());
        assertTrue(snapshot.find(new RemoteWaypointKey(SERVER, "", "List", "Café")).isEmpty());
        assertTrue(snapshot.find(new RemoteWaypointKey(SERVER, "other", " List ", "Café")).isEmpty());
        assertTrue(snapshot.find(new RemoteWaypointKey(new RemoteServerId("other"), "", " List ", "Café")).isEmpty());
    }

    @Test
    void preservesEmptyDimensionsAndLists() {
        var snapshot = snapshot(1, Map.of("empty", Map.of(), "d",
                Map.of("", new RemoteListSnapshot("", ZERO, Map.of()))));
        assertTrue(snapshot.dimensions().containsKey("empty"));
        assertTrue(snapshot.dimensions().get("d").containsKey(""));
    }

    @Test
    void revisionsOrderWithoutOverflowAndNeverWrap() {
        assertThrows(IllegalArgumentException.class, () -> new RemoteRevision(-1));
        assertEquals(new RemoteRevision(1), ZERO.next());
        var maximum = new RemoteRevision(Long.MAX_VALUE);
        assertTrue(maximum.compareTo(ZERO) > 0);
        assertTrue(ZERO.compareTo(maximum) < 0);
        assertEquals(0, maximum.compareTo(new RemoteRevision(Long.MAX_VALUE)));
        assertThrows(ArithmeticException.class, maximum::next);
        var first = snapshot(0, Map.of());
        var latest = snapshot(Long.MAX_VALUE, Map.of());
        assertTrue(latest.isNewerThan(first));
        assertFalse(first.isNewerThan(latest));
        assertFalse(first.isNewerThan(first));
        var laterReceipt = new RemoteCatalogSnapshot(SERVER, ZERO, Map.of(), Instant.MAX);
        assertFalse(laterReceipt.isNewerThan(first));
        var other = new RemoteCatalogSnapshot(new RemoteServerId("other"), ZERO, Map.of(), Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> first.isNewerThan(other));
    }

    @Test
    void catalogAndListRevisionsAreIndependent() {
        var list = new RemoteListSnapshot("", new RemoteRevision(42), Map.of());
        var before = snapshot(7, Map.of("d", Map.of("l", list)));
        var after = snapshot(9, before.dimensions());
        assertTrue(after.isNewerThan(before));
        assertEquals(new RemoteRevision(42), after.dimensions().get("d").get("l").listRevision());
    }

    @Test
    void unavailablePreservesRetainedDataAndDiffersFromPublishedEmpty() {
        var populated = snapshot(1, Map.of("d", Map.of("l",
                new RemoteListSnapshot("", ZERO, Map.of("w", waypoint(List.of()))))));
        var unavailable = new RemoteCatalogView(SERVER, RemoteCatalogState.UNAVAILABLE, Optional.of(populated));
        assertSame(populated, unavailable.snapshot().orElseThrow());
        assertEquals(1, unavailable.snapshot().orElseThrow().dimensions().size());
        var missing = new RemoteCatalogView(SERVER, RemoteCatalogState.UNAVAILABLE, Optional.empty());
        var empty = new RemoteCatalogView(SERVER, RemoteCatalogState.AVAILABLE, Optional.of(snapshot(2, Map.of())));
        assertNotEquals(missing, empty);
        assertTrue(empty.snapshot().orElseThrow().isNewerThan(populated));
        assertTrue(empty.snapshot().orElseThrow().dimensions().isEmpty());
        assertEquals(populated, new RemoteCatalogView(SERVER, RemoteCatalogState.STALE,
                Optional.of(populated)).snapshot().orElseThrow());
    }

    @Test
    void rejectsInconsistentOrUnauthorizedViews() {
        var retained = Optional.of(snapshot(0, Map.of()));
        for (var state : List.of(RemoteCatalogState.AVAILABLE, RemoteCatalogState.STALE)) {
            assertThrows(IllegalArgumentException.class, () -> new RemoteCatalogView(SERVER, state, Optional.empty()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteCatalogView(SERVER, RemoteCatalogState.UNAUTHORIZED, retained));
        assertThrows(IllegalArgumentException.class, () -> new RemoteCatalogView(new RemoteServerId("other"),
                RemoteCatalogState.AVAILABLE, retained));
        assertTrue(new RemoteCatalogView(SERVER, RemoteCatalogState.UNAUTHORIZED, Optional.empty()).snapshot().isEmpty());
        assertThrows(NullPointerException.class, () -> new RemoteCatalogView(SERVER, null, retained));
        assertThrows(NullPointerException.class, () -> new RemoteCatalogView(SERVER, RemoteCatalogState.UNAVAILABLE, null));
    }

    @Test
    void rejectsNullsThroughoutSnapshotGraph() {
        assertThrows(NullPointerException.class, () -> new RemoteCatalogSnapshot(null, ZERO, Map.of(), Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> new RemoteCatalogSnapshot(SERVER, null, Map.of(), Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> new RemoteCatalogSnapshot(SERVER, ZERO, null, Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> new RemoteCatalogSnapshot(SERVER, ZERO, Map.of(), null));
        var dimensions = new HashMap<String, Map<String, RemoteListSnapshot>>();
        dimensions.put(null, Map.of());
        assertThrows(NullPointerException.class, () -> snapshot(0, dimensions));
        dimensions.clear();
        dimensions.put("d", null);
        assertThrows(NullPointerException.class, () -> snapshot(0, dimensions));
        var lists = new HashMap<String, RemoteListSnapshot>();
        lists.put("l", null);
        assertThrows(NullPointerException.class, () -> snapshot(0, Map.of("d", lists)));
        var waypoints = new HashMap<String, RemoteWaypointSnapshot>();
        waypoints.put("w", null);
        assertThrows(NullPointerException.class, () -> new RemoteListSnapshot("", ZERO, waypoints));
        assertThrows(NullPointerException.class, () -> new RemoteListSnapshot(null, ZERO, Map.of()));
        assertThrows(NullPointerException.class, () -> new RemoteListSnapshot("", null, Map.of()));
        var keywords = new ArrayList<String>();
        keywords.add(null);
        assertThrows(NullPointerException.class, () -> waypoint(keywords));
    }

    @Test
    void preservesCoordinatesAndRejectsInvalidPresentationNumbers() {
        assertEquals(new WaypointPos(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), waypoint(List.of()).position());
        assertThrows(IllegalArgumentException.class, () -> new RemoteWaypointSnapshot("", "",
                new WaypointPos(0, 0, 0), -1, 0, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new RemoteWaypointSnapshot("", "",
                new WaypointPos(0, 0, 0), 0x1000000, 0, false, List.of(), ""));
        for (int yaw : new int[]{-181, 181}) {
            assertThrows(IllegalArgumentException.class, () -> new RemoteWaypointSnapshot("", "",
                    new WaypointPos(0, 0, 0), 0, yaw, false, List.of(), ""));
        }
        for (int yaw : new int[]{-180, 180}) {
            assertEquals(yaw, new RemoteWaypointSnapshot("", "", new WaypointPos(0, 0, 0),
                    0xFFFFFF, yaw, false, List.of(), "").yaw());
        }
    }

    private static RemoteWaypointSnapshot waypoint(List<String> keywords) {
        return new RemoteWaypointSnapshot("Display", "D", new WaypointPos(Integer.MIN_VALUE, 0, Integer.MAX_VALUE),
                0xABCDEF, -180, true, keywords, "Description");
    }

    private static RemoteCatalogSnapshot snapshot(long revision, Map<String, Map<String, RemoteListSnapshot>> dimensions) {
        return new RemoteCatalogSnapshot(SERVER, new RemoteRevision(revision), dimensions, Instant.EPOCH);
    }
}
