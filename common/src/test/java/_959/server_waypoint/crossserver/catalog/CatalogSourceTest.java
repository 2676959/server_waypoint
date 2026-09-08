package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogSourceTest {
    @TempDir Path temporary;
    @Test void detachedCaptureFiltersExactPublicSelectionAndPreservesIdentity() throws Exception {
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(temporary);
        manager.addWaypoint("dimension", "public", new SimpleWaypoint("identity", "Display", "I", new WaypointPos(1,2,3),
                0x123456, 45, true, List.of("keyword"), "description"), ignored -> { });
        manager.addWaypoint("dimension", "private", new SimpleWaypoint("secret", "S", new WaypointPos(4,5,6), 0, 0, false), ignored -> { });
        var source = CatalogSource.fromManager(manager, new CatalogSelection(false, Map.of("dimension", Set.of("public"))), 100);
        var captured = source.capture();
        assertEquals(Set.of("public"), captured.get("dimension").keySet());
        assertEquals("Display", captured.get("dimension").get("public").waypoints().get("identity").displayName());
        manager.clearWaypointFileManagers();
        assertEquals(1, captured.get("dimension").get("public").waypoints().size());
        assertTrue(source.capture().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> captured.clear());
    }
    @Test void unavailableBudgetAndCallbackCaptureFailWithoutMutatingData() throws Exception {
        assertThrows(IllegalStateException.class, () -> new WaypointFilesManagerCore().snapshotWaypointData(100));
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(temporary);
        manager.addWaypoint("dimension", "list", new SimpleWaypoint("name", "N", new WaypointPos(1,2,3), 0, 0, false), ignored -> {
            assertThrows(IllegalStateException.class, () -> manager.snapshotWaypointData(100));
        });
        assertThrows(IllegalArgumentException.class, () -> manager.snapshotWaypointData(2));
        assertEquals(1, manager.snapshotWaypointData(3).get("dimension").get(0).size());
        assertTrue(CatalogSource.fromManager(manager, new CatalogSelection(false, Map.of()), 100).capture().isEmpty());
    }
    @Test void revisionHighWaterMarkSurvivesRestartAndHasExclusiveOwner() throws Exception {
        Path state = temporary.toRealPath().resolve("catalog-state");
        try (CatalogRevisionSequence revisions = new CatalogRevisionSequence(state)) {
            assertEquals(1, revisions.next()); assertEquals(2, revisions.next());
            assertThrows(java.io.IOException.class, () -> new CatalogRevisionSequence(state));
        }
        try (CatalogRevisionSequence revisions = new CatalogRevisionSequence(state)) { assertEquals(3, revisions.next()); }
        java.nio.file.Files.writeString(state.resolve("revision"), Long.toString(Long.MAX_VALUE));
        try (CatalogRevisionSequence revisions = new CatalogRevisionSequence(state)) { assertThrows(java.io.IOException.class, revisions::next); }
    }
}
