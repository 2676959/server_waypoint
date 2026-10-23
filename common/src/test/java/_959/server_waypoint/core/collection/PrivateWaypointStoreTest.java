package _959.server_waypoint.core.collection;

import _959.server_waypoint.config.PrivateWaypointConfig;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PrivateWaypointStoreTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    @TempDir Path root;

    @Test
    void ownersAndDimensionsPersistIndependentlyAndSharedRemainsEmpty() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Path privateRoot = this.root.resolve("private-waypoints");
        try (PrivateWaypointStore store = new PrivateWaypointStore(privateRoot, PrivateWaypointConfig::new)) {
            add(store, alice, OVERWORLD, "Homes", "Base");
            add(store, alice, NETHER, "Homes", "Portal");
            add(store, bob, OVERWORLD, "Homes", "Other");
            store.evict(alice);
            assertEquals(1, count(store, alice, OVERWORLD));
            assertEquals(1, count(store, alice, NETHER));
            assertEquals(1, count(store, bob, OVERWORLD));
            WaypointFilesManagerCore shared = new WaypointFilesManagerCore(this.root.resolve("waypoints"));
            assertTrue(shared.getFileManagerMap().isEmpty());
            assertTrue(shared.snapshotWaypointData(100).isEmpty());
        }
        assertTrue(Files.isRegularFile(privateRoot.resolve(alice.toString()).resolve("minecraft$overworld.json")));
        try (PrivateWaypointStore reopened = new PrivateWaypointStore(privateRoot, PrivateWaypointConfig::new)) {
            assertEquals(1, count(reopened, alice, OVERWORLD));
            assertEquals(1, count(reopened, bob, OVERWORLD));
        }
    }

    @Test
    void ownerGateAllowsOnlyOneAdditionToTheLastSlotAcrossDimensions() throws Exception {
        UUID owner = UUID.randomUUID();
        try (PrivateWaypointStore store = new PrivateWaypointStore(this.root, () -> new PrivateWaypointConfig(2, 100))) {
            add(store, owner, OVERWORLD, "Homes", "First");
            var executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger rejected = new AtomicInteger();
            try {
                var first = executor.submit(() -> competingAdd(store, owner, OVERWORLD, start, rejected));
                var second = executor.submit(() -> competingAdd(store, owner, NETHER, start, rejected));
                start.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1, rejected.get());
            assertEquals(2, count(store, owner, OVERWORLD) + count(store, owner, NETHER));
        }
    }

    @Test
    void emptyListsCountAndRejectedAutomaticCreationLeavesNoListOrDimension() throws Exception {
        UUID owner = UUID.randomUUID();
        try (PrivateWaypointStore store = new PrivateWaypointStore(this.root, () -> new PrivateWaypointConfig(1, 1))) {
            store.withOwner(owner, files -> files.addWaypointList(OVERWORLD, "Empty", ignored -> {}));
            assertThrows(PrivateWaypointStore.QuotaExceededException.class,
                    () -> add(store, owner, NETHER, "New", "No"));
            store.withOwner(owner, files -> {
                assertNull(files.getWaypointFileManager(NETHER));
                assertEquals(1, files.getWaypointFileManager(OVERWORLD).getWaypointLists().size());
                return null;
            });
            add(store, owner, OVERWORLD, "Empty", "First");
            assertThrows(PrivateWaypointStore.QuotaExceededException.class,
                    () -> add(store, owner, NETHER, "Another", "Second"));
        }
    }

    @Test
    void reloadedLimitsPreserveExistingDataAndAllowRenameAndRemoval() throws Exception {
        UUID owner = UUID.randomUUID();
        AtomicReference<PrivateWaypointConfig> limits = new AtomicReference<>(new PrivateWaypointConfig(0, 0));
        try (PrivateWaypointStore store = new PrivateWaypointStore(this.root, limits::get)) {
            add(store, owner, OVERWORLD, "Homes", "One");
            add(store, owner, OVERWORLD, "Homes", "Two");
            limits.set(new PrivateWaypointConfig(1, 1));
            assertThrows(PrivateWaypointStore.QuotaExceededException.class,
                    () -> add(store, owner, OVERWORLD, "Homes", "Three"));
            store.withOwner(owner, files -> files.updateWaypointList(EditTarget.list(OVERWORLD, "Homes"), null,
                    new WaypointListPatch(PatchField.set("Renamed"), PatchField.unchanged()), ignored -> {}));
            store.withOwner(owner, files -> files.removeWaypoint(OVERWORLD, "Renamed", "One", ignored -> {}));
            assertEquals(1, count(store, owner, OVERWORLD));
            assertThrows(PrivateWaypointStore.QuotaExceededException.class,
                    () -> store.withOwner(owner, files -> files.restoreWaypoint(OVERWORLD, "Renamed", waypoint("One"), ignored -> {})));
        }
        assertThrows(IllegalArgumentException.class, () -> new PrivateWaypointConfig(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PrivateWaypointConfig(0, -1));
        assertEquals(1000, new PrivateWaypointConfig().maxWaypointsPerPlayer());
        assertEquals(100, new PrivateWaypointConfig().maxListsPerPlayer());
    }

    @Test
    void evictionWaitsForMutationAndAQueuedOperationUsesTheCurrentOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        try (PrivateWaypointStore store = new PrivateWaypointStore(this.root, PrivateWaypointConfig::new)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(4);
            try {
                var mutation = executor.submit(() -> store.withOwner(owner, files -> {
                    files.addWaypoint(OVERWORLD, "Homes", waypoint("First"), ignored -> {});
                    entered.countDown();
                    await(release);
                    return null;
                }));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var eviction = executor.submit(() -> { store.evict(owner); return null; });
                var queued = executor.submit(() -> { add(store, owner, NETHER, "Homes", "Second"); return null; });
                var otherOwner = executor.submit(() -> { add(store, UUID.randomUUID(), OVERWORLD, "Other", "Free"); return null; });
                otherOwner.get(5, TimeUnit.SECONDS);
                assertFalse(eviction.isDone());
                release.countDown();
                mutation.get(5, TimeUnit.SECONDS);
                eviction.get(5, TimeUnit.SECONDS);
                queued.get(5, TimeUnit.SECONDS);
                assertEquals(1, count(store, owner, OVERWORLD));
                assertEquals(1, count(store, owner, NETHER));
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void retainedManagersCannotMutateAfterTheirOwnerOperationOrEviction() throws Exception {
        UUID owner = UUID.randomUUID();
        try (PrivateWaypointStore store = new PrivateWaypointStore(this.root, PrivateWaypointConfig::new)) {
            WaypointFilesManagerCore borrowed = store.withOwner(owner, files -> files);
            assertThrows(IllegalStateException.class,
                    () -> borrowed.addWaypoint(OVERWORLD, "Homes", waypoint("Detached"), ignored -> {}));
            store.evict(owner);
            assertThrows(IllegalStateException.class,
                    () -> borrowed.addWaypoint(OVERWORLD, "Homes", waypoint("Detached"), ignored -> {}));
            assertEquals(0, count(store, owner, OVERWORLD));
        }
    }

    @Test
    void invalidOwnerFilesFailClosedAndCannotBeOverwritten() throws Exception {
        UUID owner = UUID.randomUUID();
        Path ownerPath = this.root.resolve(owner.toString());
        Files.createDirectories(ownerPath);
        Path file = ownerPath.resolve("minecraft$overworld.json");
        for (String invalid : new String[]{"broken json", "null", "{}"}) {
            Files.writeString(file, invalid);
            try (PrivateWaypointStore store = new PrivateWaypointStore(this.root, PrivateWaypointConfig::new)) {
                assertThrows(IOException.class, () -> add(store, owner, OVERWORLD, "Homes", "Base"));
            }
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void saveFailuresAreReportedAndShutdownCanRetryAfterRepair() throws Exception {
        UUID owner = UUID.randomUUID();
        PrivateWaypointStore store = new PrivateWaypointStore(this.root, PrivateWaypointConfig::new);
        Path ownerPath = this.root.resolve(owner.toString());
        try {
            assertThrows(IOException.class, () -> store.withOwner(owner, files -> {
                files.addWaypoint(OVERWORLD, "Homes", waypoint("Base"), ignored -> {});
                Files.delete(ownerPath);
                Files.writeString(ownerPath, "blocked");
                return null;
            }));
            assertThrows(IOException.class, store::close);
            Files.delete(ownerPath);
            Files.createDirectories(ownerPath);
            store.close();
            assertTrue(Files.isRegularFile(ownerPath.resolve("minecraft$overworld.json")));
            assertThrows(IllegalStateException.class, () -> count(store, owner, OVERWORLD));
        } finally {
            if (Files.isRegularFile(ownerPath)) Files.delete(ownerPath);
            store.close();
        }
    }

    private static void competingAdd(PrivateWaypointStore store, UUID owner, String dimension,
                                     CountDownLatch start, AtomicInteger rejected) {
        try {
            await(start);
            add(store, owner, dimension, "New list", dimension);
        } catch (PrivateWaypointStore.QuotaExceededException expected) {
            rejected.incrementAndGet();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }

    private static void add(PrivateWaypointStore store, UUID owner, String dimension, String list, String name) throws IOException {
        store.withOwner(owner, files -> files.addWaypoint(dimension, list, waypoint(name), ignored -> {}));
    }

    private static SimpleWaypoint waypoint(String name) {
        return new SimpleWaypoint(name, "B", new WaypointPos(10, 64, 20), 0x123456, 0, false);
    }

    private static int count(PrivateWaypointStore store, UUID owner, String dimension) throws IOException {
        return store.withOwner(owner, files -> files.getWaypointFileManager(dimension) == null ? 0
                : files.getWaypointFileManager(dimension).getWaypointLists().stream().mapToInt(list -> list.size()).sum());
    }
}
