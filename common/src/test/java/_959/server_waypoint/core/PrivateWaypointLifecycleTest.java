package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PrivateWaypointLifecycleTest {
    @TempDir Path root;
    private final Config previousConfig = WaypointServerCore.CONFIG;

    @AfterEach
    void restoreConfig() {
        WaypointServerCore.CONFIG = this.previousConfig;
    }

    @Test
    void dedicatedAndIntegratedRootsStayOutsideSharedSnapshots() throws Exception {
        WaypointServerCore server = new WaypointServerCore(this.root.resolve("config")) {};
        UUID owner = UUID.randomUUID();
        server.withCollection(WaypointCollectionKey.privateFor(owner), files -> files.addWaypoint(
                "minecraft:overworld", "Homes", waypoint(), ignored -> {}));
        assertTrue(Files.exists(this.root.resolve("config/private-waypoints").resolve(owner.toString())
                .resolve("minecraft$overworld.json")));
        assertTrue(server.getFileManagerMap().isEmpty());
        assertNull(server.toWorldWaypointData());
        Path save = this.root.resolve("save");
        server.useIntegratedPrivateWaypoints(save);
        server.withCollection(WaypointCollectionKey.privateFor(owner), files -> {
            assertTrue(files.getFileManagerMap().isEmpty());
            return files.addWaypoint("minecraft:overworld", "Homes", waypoint(), ignored -> {});
        });
        assertTrue(Files.exists(save.resolve("server_waypoint/private-waypoints").resolve(owner.toString())
                .resolve("minecraft$overworld.json")));
        server.freeAllLoadedFiles();
    }

    @Test
    void invalidReloadKeepsThePreviousConfigurationAndPartialConfigKeepsDefaults() {
        WaypointServerCore server = new WaypointServerCore(this.root) {};
        server.loadConfig(new StringReader("{\"privateWaypoints\":{\"maxWaypointsPerPlayer\":7}}"));
        assertEquals(7, WaypointServerCore.CONFIG.privateWaypoints().maxWaypointsPerPlayer());
        assertEquals(100, WaypointServerCore.CONFIG.privateWaypoints().maxListsPerPlayer());
        Config valid = WaypointServerCore.CONFIG;
        assertThrows(RuntimeException.class, () -> server.loadConfig(
                new StringReader("{\"privateWaypoints\":{\"maxListsPerPlayer\":-1}}")));
        assertSame(valid, WaypointServerCore.CONFIG);
        assertThrows(RuntimeException.class, () -> server.loadConfig(new StringReader("{\"privateWaypoints\":null}")));
        assertSame(valid, WaypointServerCore.CONFIG);
    }

    @Test
    void shutdownDoesNotHoldSharedLocksWhileWaitingForAnOwner() throws Exception {
        WaypointServerCore server = new WaypointServerCore(this.root) {};
        CountDownLatch ownerEntered = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicReference<Thread> shutdownThread = new AtomicReference<>();
        var executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        try {
            var owner = executor.submit(() -> server.withCollection(WaypointCollectionKey.privateFor(UUID.randomUUID()), files -> {
                ownerEntered.countDown();
                try {
                    if (!releaseOwner.await(5, TimeUnit.SECONDS)) throw new AssertionError("Owner was not released");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                // Reading Shared while operating on Private is valid, for example while preparing a copy.
                assertTrue(server.getFileManagerMap().isEmpty());
                return null;
            }));
            assertTrue(ownerEntered.await(5, TimeUnit.SECONDS));
            var shutdown = executor.submit(() -> {
                shutdownThread.set(Thread.currentThread());
                server.freeAllLoadedFiles();
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && (shutdownThread.get() == null
                    || shutdownThread.get().getState() != Thread.State.WAITING)) {
                Thread.sleep(1);
            }
            assertNotNull(shutdownThread.get());
            assertEquals(Thread.State.WAITING, shutdownThread.get().getState());
            releaseOwner.countDown();
            owner.get(5, TimeUnit.SECONDS);
            shutdown.get(5, TimeUnit.SECONDS);
        } finally {
            releaseOwner.countDown();
            executor.shutdownNow();
        }
    }

    private static SimpleWaypoint waypoint() {
        return new SimpleWaypoint("Base", "B", new WaypointPos(10, 64, 20), 0x123456, 0, false);
    }
}
