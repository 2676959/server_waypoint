package _959.server_waypoint.common.client;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.data.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClientWaypointCollectionsTest {
    @TempDir Path directory;

    @Test
    void equalNamesRevisionsAndRecoveryStayInTheirCollection() throws Exception {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        collections.bindServer(directory, 12);
        long generation = collections.generation();
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), generation);
        assertFalse(collections.ready(ClientWaypointSource.PRIVATE));
        for (WaypointScope scope : WaypointScope.values()) collections.apply(snapshot(scope, "Homes", 7), generation);
        assertTrue(collections.ready(ClientWaypointSource.PRIVATE));
        assertNotSame(collections.files(ClientWaypointSource.SHARED), collections.files(ClientWaypointSource.PRIVATE));
        collections.tracker(ClientWaypointSource.PRIVATE).markOutOfSync("minecraft:overworld", "Homes");
        collections.apply(snapshot(WaypointScope.SHARED, "Homes", 7), generation);
        assertTrue(collections.tracker(ClientWaypointSource.PRIVATE).isOutOfSync("minecraft:overworld", "Homes"));
        collections.apply(WaypointData.updates(WaypointScope.PRIVATE, List.of(new DimensionWaypointData("minecraft:overworld",
                List.of(WaypointList.build("Homes", WaypointList.REMOVE_LIST))))), generation);
        assertNotNull(collections.files(ClientWaypointSource.SHARED).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes"));
        assertNull(collections.files(ClientWaypointSource.PRIVATE).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes"));
    }

    @Test
    void denialClearsOnlyPrivateAndRejectsLateSnapshotsUntilAuthorized() throws Exception {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        collections.bindServer(directory, 12);
        var local = new WaypointFilesManagerCore(directory.resolve("local"));
        UUID profile = UUID.randomUUID();
        collections.bindLocal(profile, local);
        long generation = collections.generation();
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), generation);
        for (WaypointScope scope : WaypointScope.values()) collections.apply(snapshot(scope, "Homes", 7), generation);
        var selection = collections.selection(ClientWaypointSource.PRIVATE, "minecraft:overworld", "Homes", null);
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, false), generation);
        assertFalse(collections.ready(ClientWaypointSource.PRIVATE));
        assertFalse(collections.valid(selection));
        assertTrue(collections.files(ClientWaypointSource.PRIVATE).getFileManagerMap().isEmpty());
        assertFalse(collections.apply(snapshot(WaypointScope.PRIVATE, "leak", 8), generation));
        assertTrue(collections.ready(ClientWaypointSource.SHARED));
        assertTrue(collections.ready(ClientWaypointSource.LOCAL));
        assertSame(local, collections.files(ClientWaypointSource.LOCAL));
        assertThrows(IllegalArgumentException.class, () -> new ClientWaypointSelection(ClientWaypointSource.LOCAL,
                generation, null, "minecraft:overworld", "Homes", null));
    }

    @Test
    void transfersAccountsAndProfilesInvalidateCapturedWorkAndIsolateDisk() throws Exception {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        collections.beginConnection(alice, "example:25565");
        collections.bindServer(directory, 12);
        long oldGeneration = collections.generation();
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), oldGeneration);
        collections.apply(snapshot(WaypointScope.PRIVATE, "Alice", 7), oldGeneration);
        Path aliceFiles = collections.files(ClientWaypointSource.PRIVATE).getWaypointFilesDir();
        var captured = collections.selection(ClientWaypointSource.PRIVATE, "minecraft:overworld", "Alice", null);
        collections.beginConnection(bob, "example:25565");
        collections.bindServer(directory, 12);
        assertNotEquals(aliceFiles, collections.files(ClientWaypointSource.PRIVATE).getWaypointFilesDir());
        assertFalse(collections.valid(captured));
        assertFalse(collections.apply(snapshot(WaypointScope.PRIVATE, "late", 8), oldGeneration));
        assertTrue(collections.files(ClientWaypointSource.PRIVATE).getFileManagerMap().isEmpty());
        Path bobFiles = collections.files(ClientWaypointSource.PRIVATE).getWaypointFilesDir();
        collections.bindServer(directory, 13);
        assertNotEquals(bobFiles, collections.files(ClientWaypointSource.PRIVATE).getWaypointFilesDir());
        collections.beginConnection(alice, "example:25565");
        collections.bindServer(directory, 12);
        assertEquals(aliceFiles, collections.files(ClientWaypointSource.PRIVATE).getWaypointFilesDir());
        assertNotNull(collections.files(ClientWaypointSource.PRIVATE).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Alice"));
        assertFalse(collections.ready(ClientWaypointSource.PRIVATE));
        collections.disconnect();
        assertFalse(collections.apply(snapshot(WaypointScope.PRIVATE, "late", 8), collections.generation()));
    }

    @Test
    void privateRenameAndIncrementalRevisionsNeverAffectSharedRecovery() throws Exception {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        collections.bindServer(directory, 12);
        long generation = collections.generation();
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), generation);
        for (WaypointScope scope : WaypointScope.values()) collections.apply(snapshot(scope, "Homes", 7), generation);
        assertTrue(collections.apply(new WaypointListUpdateMessage(WaypointScope.PRIVATE, "minecraft:overworld", "Homes",
                WaypointList.build("Renamed", 8)), generation));
        assertNotNull(collections.files(ClientWaypointSource.SHARED).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes"));
        assertNull(collections.files(ClientWaypointSource.PRIVATE).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes"));
        assertFalse(collections.apply(new WaypointModificationMessage(WaypointScope.PRIVATE, "minecraft:overworld", "Renamed", "Renamed",
                null, null, WaypointModificationType.REMOVE_LIST, 10), generation));
        assertTrue(collections.tracker(ClientWaypointSource.PRIVATE).isOutOfSync("minecraft:overworld", "Renamed"));
        assertFalse(collections.tracker(ClientWaypointSource.SHARED).isOutOfSync("minecraft:overworld", "Homes"));
        collections.apply(WaypointData.waypointList(WaypointScope.PRIVATE, "minecraft:overworld", WaypointList.build("Renamed", 10)), generation);
        assertFalse(collections.tracker(ClientWaypointSource.PRIVATE).isOutOfSync("minecraft:overworld", "Renamed"));
        assertTrue(collections.apply(new WaypointModificationMessage(WaypointScope.PRIVATE, "minecraft:overworld", "Renamed", "Renamed",
                null, null, WaypointModificationType.REMOVE_LIST, 11), generation));
        assertNotNull(collections.files(ClientWaypointSource.SHARED).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes"));
    }

    @Test
    void editResultsMatchSourceIdentityAndGenerationAndAreConsumedOnce() throws Exception {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        collections.bindServer(directory, 12);
        long generation = collections.generation();
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), generation);
        collections.apply(snapshot(WaypointScope.PRIVATE, "Homes", 7), generation);
        collections.trackEdit(1, collections.selection(ClientWaypointSource.PRIVATE, "minecraft:overworld", "Homes", "Base"));
        var wrongSource = new WaypointEditResultMessage(WaypointScope.SHARED, 1,
                _959.server_waypoint.core.edit.EditResultStatus.SUCCESS, "minecraft:overworld", "Homes", "Base", null, 8);
        var matching = new WaypointEditResultMessage(WaypointScope.PRIVATE, 1,
                _959.server_waypoint.core.edit.EditResultStatus.SUCCESS, "minecraft:overworld", "Homes", "Base", null, 8);
        assertFalse(collections.acceptResult(wrongSource, generation));
        assertTrue(collections.acceptResult(matching, generation));
        assertFalse(collections.acceptResult(matching, generation));
        collections.trackEdit(2, collections.selection(ClientWaypointSource.PRIVATE, "minecraft:overworld", "Homes", "Base"));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        assertFalse(collections.acceptResult(new WaypointEditResultMessage(WaypointScope.PRIVATE, 2,
                _959.server_waypoint.core.edit.EditResultStatus.SUCCESS, "minecraft:overworld", "Homes", "Base", null, 8), generation));
    }

    @Test
    void equalEditRequestIdsAreIndependentAcrossCollections() throws Exception {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example:25565");
        collections.bindServer(directory, 12);
        long generation = collections.generation();
        collections.apply(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), generation);
        for (WaypointScope scope : WaypointScope.values()) {
            collections.apply(snapshot(scope, "Homes", 7), generation);
            ClientWaypointSource source = ClientWaypointSource.fromScope(scope);
            collections.trackEdit(1, collections.selection(source, "minecraft:overworld", "Homes", "Base"));
        }
        for (WaypointScope scope : WaypointScope.values()) {
            assertTrue(collections.acceptResult(new WaypointEditResultMessage(scope, 1,
                    _959.server_waypoint.core.edit.EditResultStatus.SUCCESS, "minecraft:overworld", "Homes", "Base", null, 8), generation));
        }
    }

    private static WaypointData snapshot(WaypointScope scope, String name, int revision) {
        return WaypointData.world(scope, List.of(new DimensionWaypointData("minecraft:overworld", List.of(WaypointList.build(name, revision)))));
    }
}
