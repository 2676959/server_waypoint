package _959.server_waypoint.proxy;

import _959.server_waypoint.crossserver.RemoteServerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProxyAdapterContractTest {
    private final RemoteServerId source = new RemoteServerId("source");
    private final RemoteServerId destination = new RemoteServerId("destination");
    private final UUID player = UUID.randomUUID();
    private FakeProxyAdapter adapter;
    private FakeProxyAdapter.ServerHandle target;

    @BeforeEach
    void setup() {
        adapter = new FakeProxyAdapter();
        adapter.register(source);
        target = adapter.register(destination);
        adapter.connect(player, source);
        adapter.grant(player, FakeProxyAdapter.TRANSFER_PERMISSION);
        adapter.start();
    }

    @Test
    void registeredIdsResolveExactOpaqueHandles() {
        assertEquals(target, adapter.findServer(destination).orElseThrow());
        assertTrue(adapter.findServer(new RemoteServerId("other")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> adapter.register(destination));
        adapter.unregister(destination);
        assertNotEquals(target, adapter.register(destination), "A new registration must not revive a stale handle");
    }

    @Test
    void playerSnapshotDoesNotAuthorizeLaterSource() {
        var snapshot = adapter.findPlayer(player).orElseThrow();
        assertEquals(source, snapshot.currentServer().orElseThrow());
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        assertFalse(transfer.isDone());
        adapter.connect(player, destination);
        adapter.advance(Duration.ZERO);
        assertEquals(TransferResult.SOURCE_MISMATCH, transfer.join());
        assertEquals(source, snapshot.currentServer().orElseThrow());
    }

    @Test
    void successfulTransferIsAsynchronousAndChangesOnlyProxyRoute() {
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        assertFalse(transfer.isDone());
        assertEquals(source, adapter.findPlayer(player).orElseThrow().currentServer().orElseThrow());
        adapter.advance(Duration.ZERO);
        assertEquals(TransferResult.SUCCESS, transfer.join());
        assertEquals(destination, adapter.findPlayer(player).orElseThrow().currentServer().orElseThrow());
    }

    @Test
    void offlinePlayerCannotUseOldSnapshotOrPermissions() {
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        adapter.disconnect(player);
        adapter.advance(Duration.ZERO);
        assertEquals(TransferResult.PLAYER_OFFLINE, transfer.join());
        assertTrue(adapter.findPlayer(player).isEmpty());
        assertFalse(adapter.hasPermission(player, FakeProxyAdapter.TRANSFER_PERMISSION));
    }

    @Test
    void staleDestinationHandleCannotTransfer() {
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        adapter.unregister(destination);
        adapter.register(destination);
        adapter.advance(Duration.ZERO);
        assertEquals(TransferResult.UNKNOWN_DESTINATION, transfer.join());
        assertEquals(source, adapter.findPlayer(player).orElseThrow().currentServer().orElseThrow());
    }

    @Test
    void revokedPermissionDeniesBeforeSwitch() {
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        adapter.revoke(player, FakeProxyAdapter.TRANSFER_PERMISSION);
        adapter.advance(Duration.ZERO);
        assertEquals(TransferResult.PERMISSION_DENIED, transfer.join());
        assertEquals(source, adapter.findPlayer(player).orElseThrow().currentServer().orElseThrow());
    }

    @Test
    void connectionFailurePreservesSource() {
        adapter.failNextConnection();
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        adapter.advance(Duration.ZERO);
        assertEquals(TransferResult.CONNECTION_FAILED, transfer.join());
        assertEquals(source, adapter.findPlayer(player).orElseThrow().currentServer().orElseThrow());
    }

    @Test
    void shutdownCancelsPendingTransfers() {
        var transfer = adapter.transfer(player, source, target).toCompletableFuture();
        adapter.stop();
        adapter.advance(Duration.ofDays(1));
        assertEquals(TransferResult.CANCELLED, transfer.join());
        assertEquals(TransferResult.CANCELLED, adapter.transfer(player, source, target).toCompletableFuture().join());
        assertEquals(source, adapter.findPlayer(player).orElseThrow().currentServer().orElseThrow());
    }

    @Test
    void lifecycleEventsAndScheduledCallbacksHaveCancellableOwnership() {
        adapter.stop();
        List<String> events = new ArrayList<>();
        Runnable start = () -> events.add("start");
        var removedStart = adapter.onStart(start);
        adapter.onStart(start);
        removedStart.close();
        removedStart.close();
        adapter.onStop(() -> events.add("stop"));
        var disconnect = adapter.onPlayerDisconnect(id -> events.add("disconnect:" + id));
        adapter.start();
        adapter.start();
        var cancelled = adapter.schedule(Duration.ZERO, () -> events.add("cancelled"));
        cancelled.close();
        cancelled.close();
        adapter.schedule(Duration.ofSeconds(2), () -> events.add("due"));
        adapter.advance(Duration.ofSeconds(1));
        assertEquals(List.of("start"), events);
        adapter.advance(Duration.ofSeconds(1));
        adapter.disconnect(player);
        disconnect.close();
        adapter.disconnect(player);
        adapter.stop();
        adapter.stop();
        assertEquals(List.of("start", "due", "disconnect:" + player, "stop"), events);
    }
}
