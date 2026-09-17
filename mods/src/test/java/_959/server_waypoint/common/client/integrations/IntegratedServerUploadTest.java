package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IntegratedServerUploadTest {
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final Queue<Runnable> serverTasks = new ArrayDeque<>();
    private final WaypointData snapshot = WaypointData.upload(UUID.randomUUID(), UploadStatus.SUCCESS, List.of());

    @Test
    void collectsOnClientThenAppliesAndCompletesOnServer() {
        AtomicBoolean collected = new AtomicBoolean();
        AtomicReference<WaypointData> applied = new AtomicReference<>();
        var completion = IntegratedServerUpload.collect(
                clientTasks::add, serverTasks::add, () -> true, () -> true,
                () -> {
                    collected.set(true);
                    return snapshot;
                }, applied::set
        );
        assertFalse(collected.get());
        assertTrue(serverTasks.isEmpty());
        clientTasks.remove().run();
        assertTrue(collected.get());
        assertNull(applied.get());
        assertFalse(completion.isDone());
        serverTasks.remove().run();
        assertSame(snapshot, applied.get());
        assertEquals(ChunkedMessageSendResult.DELIVERED, completion.join());
    }

    @Test
    void leavingWorldBeforeCollectionDoesNotReadMapData() {
        var completion = IntegratedServerUpload.collect(
                clientTasks::add, serverTasks::add, () -> false, () -> true,
                () -> { throw new AssertionError("Stale client collected map data"); },
                data -> fail("Stale upload applied")
        );
        clientTasks.remove().run();
        assertFalse(completion.isDone());
        serverTasks.remove().run();
        assertEquals(ChunkedMessageSendResult.DELIVERY_FAILED, completion.join());
    }

    @Test
    void disconnectAfterCollectionDoesNotApplySnapshot() {
        AtomicBoolean connected = new AtomicBoolean(true);
        var completion = IntegratedServerUpload.collect(
                clientTasks::add, serverTasks::add, () -> true, connected::get,
                () -> snapshot, data -> fail("Disconnected player upload applied")
        );
        clientTasks.remove().run();
        connected.set(false);
        serverTasks.remove().run();
        assertEquals(ChunkedMessageSendResult.DELIVERY_FAILED, completion.join());
    }

    @Test
    void collectionFailureCompletesOnServerForRequestCleanup() {
        var completion = IntegratedServerUpload.collect(
                clientTasks::add, serverTasks::add, () -> true, () -> true,
                () -> { throw new IllegalStateException("Map unavailable"); },
                data -> fail("Failed export applied")
        );
        clientTasks.remove().run();
        assertFalse(completion.isDone());
        serverTasks.remove().run();
        assertTrue(completion.isCompletedExceptionally());
    }
}
