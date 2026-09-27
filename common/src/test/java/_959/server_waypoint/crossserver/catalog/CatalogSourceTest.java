package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.crossserver.RemoteCatalogSnapshot;
import _959.server_waypoint.crossserver.RemoteRevision;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.ApplicationCodec;
import _959.server_waypoint.crossserver.protocol.ProtocolLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogSourceTest {
    @TempDir Path temporary;
    @Test void captureAndEncodingChangeOnIconOnlyEdit() throws Exception {
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(temporary);
        SimpleWaypoint waypoint = new SimpleWaypoint("name", "N", new WaypointPos(1, 2, 3), 0, 0, false);
        manager.addWaypoint("dimension", "list", waypoint, ignored -> { });
        CatalogSource source = CatalogSource.fromManager(manager,
                new CatalogSelection(false, Map.of("dimension", Set.of("list"))), 100);
        var before = source.capture();
        assertNull(before.get("dimension").get("list").waypoints().get("name").icon());

        manager.updateWaypoint(EditTarget.waypoint("dimension", "list", "name"), null,
                new WaypointPatch(PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                        PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                        PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                        PatchField.set(NamespacedId.parse("voxelmap:star"))), ignored -> { });
        var after = source.capture();
        assertEquals(NamespacedId.parse("voxelmap:star"),
                after.get("dimension").get("list").waypoints().get("name").icon());
        assertNotEquals(before, after);
        ApplicationCodec codec = new ApplicationCodec(ProtocolLimits.DEFAULT);
        RemoteServerId server = new RemoteServerId("test");
        byte[] previousBytes = codec.encodeCatalog(new RemoteCatalogSnapshot(server,
                new RemoteRevision(1), before, Instant.EPOCH));
        byte[] changedBytes = codec.encodeCatalog(new RemoteCatalogSnapshot(server,
                new RemoteRevision(1), after, Instant.EPOCH));
        assertFalse(Arrays.equals(previousBytes, changedBytes));
    }
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
