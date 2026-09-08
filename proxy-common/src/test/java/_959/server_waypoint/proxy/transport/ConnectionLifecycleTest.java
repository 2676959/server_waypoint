package _959.server_waypoint.proxy.transport;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.pairing.PairingCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ConnectionLifecycleTest {
    @TempDir Path temporary;
    private static final RemoteServerId ID = new RemoteServerId("backend");
    private static final TcpLimits LIMITS = new TcpLimits(4, 1000, 500, 64, 4_194_304, 10_000);
    private static final LifecycleSettings SETTINGS = new LifecycleSettings(true, 40, 25, 200);
    private static TransportResult result(CompletionStage<TransportResult> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Condition did not become true before deadline");
    }
    private final class Fixture implements AutoCloseable {
        final TransportMode mode;
        final CredentialFiles serverFiles;
        final CredentialFiles backendFiles;
        final PairingCoordinator pairing;
        final LocalCredentials local;
        final NoiseKeys backendKeys;
        final List<BackendAgent> backends = new ArrayList<>();
        final List<CoordinatorAgent> coordinators = new ArrayList<>();
        Fixture(TransportMode mode) throws Exception {
            this.mode = mode;
            if (mode == TransportMode.NOISE_KK) {
                serverFiles = new CredentialFiles(temporary.toRealPath().resolve("coordinator"));
                backendFiles = new CredentialFiles(temporary.toRealPath().resolve("backend"));
                pairing = new PairingCoordinator(serverFiles); local = new LocalCredentials(backendFiles);
                try (var invitation = pairing.pair(ID, null, 1000);
                     var attempt = new BackendPairing(local, invitation.ticket(), ID, invitation.code(), null, 1000)) {
                    attempt.finish(pairing.complete(invitation.ticket(), attempt.confirm(pairing.begin(attempt.request()))));
                }
                backendKeys = local.noiseKeys();
            } else { serverFiles = null; backendFiles = null; pairing = null; local = null; backendKeys = null; }
        }
        CoordinatorAgent coordinator(int port) throws Exception {
            CoordinatorAgent agent = new CoordinatorAgent(() -> mode == TransportMode.NOISE_KK
                    ? pairing.listen(new TcpEndpoint("127.0.0.1", port), LIMITS, ProtocolLimits.DEFAULT)
                    : new TcpCoordinator(new TcpEndpoint("127.0.0.1", port), mode, null, Map.of(ID, new byte[0]), LIMITS, ProtocolLimits.DEFAULT), LIMITS, SETTINGS);
            coordinators.add(agent); assertEquals(TransportResult.SUCCESS, result(agent.start())); return agent;
        }
        BackendAgent backend(int port) throws Exception {
            BackendAgent agent = new BackendAgent(new TcpEndpoint("127.0.0.1", port), mode, ID, Set.of(1, 7), backendKeys,
                    mode == TransportMode.NOISE_KK ? CanonicalKey.rawPublic(local.coordinatorPin()) : null,
                    LIMITS, ProtocolLimits.DEFAULT, SETTINGS);
            backends.add(agent); assertEquals(TransportResult.SUCCESS, result(agent.start())); return agent;
        }
        @Override public void close() throws Exception {
            for (BackendAgent agent : backends) assertEquals(TransportResult.SUCCESS, result(agent.stop()));
            for (CoordinatorAgent agent : coordinators) assertEquals(TransportResult.SUCCESS, result(agent.stop()));
            if (pairing != null) { pairing.close(); backendKeys.close(); backendFiles.close(); serverFiles.close(); }
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void registersHeartbeatsAndReconnectsAfterCoordinatorRestart(TransportMode mode) throws Exception {
        try (Fixture f = new Fixture(mode)) {
            CoordinatorAgent first = f.coordinator(0);
            int port = first.status().port();
            BackendAgent backend = f.backend(port);
            await(() -> backend.status().presence() != null && first.status().backends().containsKey(ID));
            BackendPresence presence = first.status().backends().get(ID);
            assertEquals(Set.of(1, 7), presence.capabilities());
            assertEquals(mode, presence.mode());
            assertEquals(mode == TransportMode.NOISE_KK, presence.authenticated());
            assertEquals(mode == TransportMode.NOISE_KK ? "KK_AUTHENTICATED" : "TRUSTED_LOOPBACK", presence.securityStatus());
            await(() -> backend.status().metrics().receivedHeartbeats() >= 2 && first.status().metrics().receivedHeartbeats() >= 2);
            long generation = backend.status().presence().generation();
            assertEquals(TransportResult.SUCCESS, result(first.stop()));
            await(() -> backend.status().presence() == null);
            CoordinatorAgent restarted = f.coordinator(port);
            await(() -> backend.status().presence() != null && backend.status().presence().generation() > generation
                    && restarted.status().backends().containsKey(ID));
            assertTrue(backend.status().metrics().registrations() >= 2);
            assertTrue(backend.status().metrics().disconnects() >= 1);
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void duplicateBackendCannotDisplaceRegisteredPeer(TransportMode mode) throws Exception {
        try (Fixture f = new Fixture(mode)) {
            CoordinatorAgent coordinator = f.coordinator(0);
            BackendAgent first = f.backend(coordinator.status().port());
            await(() -> first.status().presence() != null);
            BackendAgent duplicate = f.backend(coordinator.status().port());
            await(() -> duplicate.status().metrics().failures() >= 2);
            assertNull(duplicate.status().presence()); assertNotNull(first.status().presence());
            assertEquals(1, coordinator.status().backends().size());
            result(first.stop());
            await(() -> duplicate.status().presence() != null);
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void disconnectIsAsynchronousAndReconnectRestoresPresence(TransportMode mode) throws Exception {
        try (Fixture f = new Fixture(mode)) {
            CoordinatorAgent coordinator = f.coordinator(0);
            BackendAgent backend = f.backend(coordinator.status().port());
            await(() -> backend.status().presence() != null);
            long generation = backend.status().presence().generation();
            assertEquals(TransportResult.SUCCESS, result(coordinator.disconnect(ID)));
            await(() -> {
                BackendPresence presence = backend.status().presence();
                return presence != null && presence.generation() > generation;
            });
            assertEquals(TransportResult.SUCCESS, result(coordinator.disconnect(new RemoteServerId("absent"))));
        }
    }

    @Test void disabledAndRepeatedLifecycleCallsHaveStableOutcomes() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        LifecycleSettings disabled = new LifecycleSettings(false, 40, 25, 200);
        CoordinatorAgent coordinator = new CoordinatorAgent(() -> { opened.set("opened"); throw new IOException(); }, LIMITS, disabled);
        assertEquals(TransportResult.DISABLED, result(coordinator.start()));
        assertEquals(TransportResult.DISABLED, result(coordinator.start()));
        assertNull(opened.get());
        assertEquals(TransportResult.SUCCESS, result(coordinator.stop()));
        assertEquals(TransportResult.SUCCESS, result(coordinator.stop()));
        assertEquals(TransportResult.UNAVAILABLE, result(coordinator.start()));
        BackendAgent backend = new BackendAgent(new TcpEndpoint("127.0.0.1", 1), TransportMode.PLAINTEXT, ID, Set.of(), null, null,
                LIMITS, ProtocolLimits.DEFAULT, disabled);
        assertEquals(TransportResult.DISABLED, result(backend.start()));
        assertEquals(0, backend.status().metrics().attempts());
        assertEquals(TransportResult.SUCCESS, result(backend.stop()));
        BackendAgent disabledKk = new BackendAgent(new TcpEndpoint("unused.invalid", 1), TransportMode.NOISE_KK,
                ID, Set.of(), null, null, LIMITS, ProtocolLimits.DEFAULT, disabled);
        assertEquals(TransportResult.DISABLED, result(disabledKk.start()));
        assertEquals(0, disabledKk.status().metrics().attempts());
        assertEquals(TransportResult.SUCCESS, result(disabledKk.stop()));
    }

    @Test void bindingAndShutdownDoNotBlockCallerThread() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        String caller = Thread.currentThread().getName();
        CoordinatorAgent coordinator = new CoordinatorAgent(() -> {
            assertNotEquals(caller, Thread.currentThread().getName());
            factoryEntered.countDown();
            try { releaseFactory.await(); } catch (InterruptedException failure) { throw new IOException(); }
            return new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT, null,
                    Map.of(ID, new byte[0]), LIMITS, ProtocolLimits.DEFAULT);
        }, LIMITS, SETTINGS);
        try {
            CompletionStage<TransportResult> start = coordinator.start();
            assertTrue(factoryEntered.await(2, TimeUnit.SECONDS));
            assertFalse(start.toCompletableFuture().isDone());
            List<CompletionStage<TransportResult>> commands = new ArrayList<>();
            for (int n = 0; n < 100; n++) commands.add(coordinator.disconnect(ID));
            assertEquals(64, commands.stream().filter(command -> !command.toCompletableFuture().isDone()).count());
            CompletionStage<TransportResult> stop = coordinator.stop();
            assertFalse(stop.toCompletableFuture().isDone());
            releaseFactory.countDown();
            assertEquals(TransportResult.SUCCESS, result(stop));
            assertFalse(coordinator.status().running());
            for (CompletionStage<TransportResult> command : commands) assertTrue(command.toCompletableFuture().isDone());
        } finally { releaseFactory.countDown(); result(coordinator.stop()); }
    }

    @Test void stoppingInterruptsAnUnfinishedOutboundHandshake() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); Fixture f = new Fixture(TransportMode.PLAINTEXT)) {
            BackendAgent backend = f.backend(silent.getLocalPort());
            try (Socket pending = silent.accept()) {
                assertEquals(TransportResult.SUCCESS, result(backend.stop()));
                assertNull(backend.status().presence());
                pending.setSoTimeout(1000);
                while (pending.getInputStream().read() != -1) { }
                assertEquals(TransportResult.UNAVAILABLE, result(backend.start()));
            }
        }
    }

    @Test void failedConnectionsBackOffWithinCeilingAndStopDuringWait() throws Exception {
        int port;
        try (ServerSocket unused = new ServerSocket(0)) { port = unused.getLocalPort(); }
        try (Fixture f = new Fixture(TransportMode.PLAINTEXT)) {
            BackendAgent backend = f.backend(port);
            await(() -> backend.status().metrics().attempts() >= 3);
            assertNull(backend.status().presence());
            assertEquals(TransportResult.SUCCESS, result(backend.stop()));
            long attempts = backend.status().metrics().attempts();
            Thread.sleep(100);
            assertEquals(attempts, backend.status().metrics().attempts());
        }
        assertEquals(25, SETTINGS.reconnectDelay(1));
        assertEquals(50, SETTINGS.reconnectDelay(2));
        assertEquals(200, SETTINGS.reconnectDelay(20));
    }

    @Test void missingRegistrationAndTranscriptMismatchNeverBecomePresence() throws Exception {
        try (Fixture f = new Fixture(TransportMode.PLAINTEXT)) {
            CoordinatorAgent coordinator = f.coordinator(0);
            for (boolean mismatch : new boolean[]{false, true}) {
                try (TcpChannel raw = TcpBackend.connect(new TcpEndpoint("127.0.0.1", coordinator.status().port()), TransportMode.PLAINTEXT,
                        ID, Set.of(1), null, null, LIMITS, ProtocolLimits.DEFAULT)) {
                    assertTrue(coordinator.status().backends().isEmpty());
                    if (mismatch) raw.send(UUID.randomUUID(), new ApplicationMessage.RegisterServer(new RemoteServerId("other"), 1, Set.of(1)));
                    assertThrows(IOException.class, raw::receive);
                    assertTrue(coordinator.status().backends().isEmpty());
                }
            }
        }
    }

    @Test void heartbeatsDoNotKeepSilentPeerRegistered() throws Exception {
        try (Fixture f = new Fixture(TransportMode.PLAINTEXT)) {
            CoordinatorAgent coordinator = f.coordinator(0);
            try (TcpChannel raw = TcpBackend.connect(new TcpEndpoint("127.0.0.1", coordinator.status().port()), TransportMode.PLAINTEXT,
                    ID, Set.of(), null, null, LIMITS, ProtocolLimits.DEFAULT)) {
                raw.send(UUID.randomUUID(), new ApplicationMessage.RegisterServer(ID, 1, Set.of()));
                raw.receive();
                await(() -> coordinator.status().backends().containsKey(ID));
                await(() -> coordinator.status().backends().isEmpty() && coordinator.status().metrics().disconnects() >= 1);
            }
        }
    }

    @Test void listenerClosedByCredentialOwnerDoesNotBusyLoop() throws Exception {
        try (Fixture f = new Fixture(TransportMode.NOISE_KK)) {
            CoordinatorAgent coordinator = f.coordinator(0);
            BackendAgent backend = f.backend(coordinator.status().port());
            await(() -> backend.status().presence() != null);
            f.pairing.rotateCoordinator();
            await(() -> !coordinator.status().running());
            long failures = coordinator.status().metrics().failures();
            Thread.sleep(100);
            assertTrue(coordinator.status().metrics().failures() <= failures + LIMITS.connections());
            assertTrue(coordinator.status().backends().isEmpty());
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3})
    void backendRejectsInvalidRegistrationResultOrMissingHeartbeat(int mode) throws Exception {
        try (Fixture f = new Fixture(TransportMode.PLAINTEXT);
             TcpCoordinator raw = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT,
                     null, Map.of(ID, new byte[0]), LIMITS, ProtocolLimits.DEFAULT)) {
            BackendAgent backend = f.backend(raw.port());
            try (TcpChannel channel = raw.accept()) {
                ApplicationEnvelope registration = channel.receive().envelope();
                if (mode == 0) channel.send(registration.requestId(), new ApplicationMessage.Heartbeat());
                else channel.send(mode == 1 ? UUID.randomUUID() : registration.requestId(),
                        new ApplicationMessage.RegisterResult(mode == 2 ? new RemoteServerId("other") : ID, ApplicationMessage.Result.SUCCESS));
                if (mode == 3) await(() -> backend.status().presence() != null);
                await(() -> backend.status().metrics().failures() > 0);
                assertNull(backend.status().presence());
            }
        }
    }

    @Test void invalidConfigurationAndBindFailureReturnResults() throws Exception {
        BackendAgent backend = new BackendAgent(new TcpEndpoint("0.0.0.0", 1), TransportMode.PLAINTEXT,
                ID, Set.of(), null, null, LIMITS, ProtocolLimits.DEFAULT, SETTINGS);
        assertEquals(TransportResult.INVALID_CONFIGURATION, result(backend.start()));
        assertEquals(TransportResult.SUCCESS, result(backend.stop()));
        CoordinatorAgent coordinator = new CoordinatorAgent(() -> { throw new IOException("fixture bind failure"); }, LIMITS, SETTINGS);
        assertEquals(TransportResult.UNAVAILABLE, result(coordinator.start()));
        assertEquals(TransportResult.SUCCESS, result(coordinator.stop()));
    }
}
