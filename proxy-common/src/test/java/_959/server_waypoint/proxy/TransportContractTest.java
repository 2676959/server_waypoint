package _959.server_waypoint.proxy;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.transport.BackendTransport;
import _959.server_waypoint.crossserver.transport.CoordinatorTransport;
import _959.server_waypoint.crossserver.transport.TransportResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class TransportContractTest {
    @Test
    void lifecycleRegistrationDisconnectAndReconnectStayPlatformNeutral() {
        var coordinator = new FakeCoordinator();
        var id = new RemoteServerId("backend");
        var backend = new FakeBackend(id, coordinator);
        assertEquals(TransportResult.UNAVAILABLE, backend.start().toCompletableFuture().join());
        coordinator.start();
        assertEquals(TransportResult.DISABLED, backend.start().toCompletableFuture().join());
        coordinator.enabled.add(id);
        assertEquals(TransportResult.SUCCESS, backend.start().toCompletableFuture().join());
        assertEquals(TransportResult.SUCCESS, backend.start().toCompletableFuture().join());
        assertEquals(1, coordinator.sessions.size());
        assertEquals(TransportResult.UNAVAILABLE,
                new FakeBackend(id, coordinator).start().toCompletableFuture().join());
        coordinator.disconnect(id);
        assertFalse(backend.connected);
        assertEquals(TransportResult.SUCCESS, backend.start().toCompletableFuture().join());
        coordinator.enabled.remove(id);
        coordinator.disconnect(id);
        assertEquals(TransportResult.DISABLED, backend.start().toCompletableFuture().join());
        assertEquals(TransportResult.SUCCESS, coordinator.disconnect(id).toCompletableFuture().join());
        assertEquals(TransportResult.SUCCESS, coordinator.stop().toCompletableFuture().join());
        assertEquals(TransportResult.SUCCESS, coordinator.stop().toCompletableFuture().join());
        assertTrue(coordinator.sessions.isEmpty());
    }

    @Test
    void coordinatorShutdownReleasesAllBackendSessions() {
        var coordinator = new FakeCoordinator();
        coordinator.start();
        var id = new RemoteServerId("backend");
        coordinator.enabled.add(id);
        var backend = new FakeBackend(id, coordinator);
        backend.start();
        coordinator.stop();
        assertFalse(backend.connected);
        assertTrue(coordinator.sessions.isEmpty());
        assertEquals(TransportResult.SUCCESS, backend.stop().toCompletableFuture().join());
        assertEquals(TransportResult.UNAVAILABLE, backend.start().toCompletableFuture().join());
    }

    private static CompletionStage<TransportResult> result(TransportResult value) {
        return CompletableFuture.completedFuture(value);
    }

    private static final class FakeCoordinator implements CoordinatorTransport {
        private boolean running;
        private final Set<RemoteServerId> enabled = new HashSet<>();
        private final Map<RemoteServerId, FakeBackend> sessions = new HashMap<>();

        @Override
        public CompletionStage<TransportResult> start() {
            running = true;
            return result(TransportResult.SUCCESS);
        }

        @Override
        public CompletionStage<TransportResult> stop() {
            running = false;
            Set.copyOf(sessions.keySet()).forEach(this::disconnect);
            return result(TransportResult.SUCCESS);
        }

        @Override
        public CompletionStage<TransportResult> disconnect(RemoteServerId id) {
            FakeBackend backend = sessions.remove(id);
            if (backend != null) {
                backend.connected = false;
            }
            return result(TransportResult.SUCCESS);
        }
    }

    private static final class FakeBackend implements BackendTransport {
        private final RemoteServerId id;
        private final FakeCoordinator coordinator;
        private boolean connected;

        private FakeBackend(RemoteServerId id, FakeCoordinator coordinator) {
            this.id = id;
            this.coordinator = coordinator;
        }

        @Override
        public RemoteServerId serverId() {
            return id;
        }

        @Override
        public CompletionStage<TransportResult> start() {
            if (!coordinator.running) {
                return result(TransportResult.UNAVAILABLE);
            }
            if (!coordinator.enabled.contains(id)) {
                return result(TransportResult.DISABLED);
            }
            FakeBackend existing = coordinator.sessions.putIfAbsent(id, this);
            if (existing != null && existing != this) {
                return result(TransportResult.UNAVAILABLE);
            }
            connected = true;
            return result(TransportResult.SUCCESS);
        }

        @Override
        public CompletionStage<TransportResult> stop() {
            coordinator.sessions.remove(id, this);
            connected = false;
            return result(TransportResult.SUCCESS);
        }
    }
}
