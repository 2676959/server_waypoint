package _959.server_waypoint.proxy.pairing;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.crossserver.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class PairingCoordinatorTest {
    @TempDir Path temporary;
    private static final RemoteServerId ID = new RemoteServerId("backend");
    private final AtomicLong clock = new AtomicLong();
    private final class Fixture implements AutoCloseable {
        final CredentialFiles serverFiles = new CredentialFiles(temporary.toRealPath().resolve("server"));
        final CredentialFiles backendFiles = new CredentialFiles(temporary.toRealPath().resolve("backend"));
        final PairingCoordinator coordinator = new PairingCoordinator(serverFiles, clock::get);
        final LocalCredentials backend = new LocalCredentials(backendFiles);
        Fixture() throws Exception { }
        void pair(RemoteServerId id) throws Exception { pair(id, backend); }
        void pair(RemoteServerId id, LocalCredentials backend) throws Exception {
            try (var invitation = coordinator.pair(id, coordinator.pins().get(id), 1000);
                 var attempt = new BackendPairing(backend, invitation.ticket(), id, invitation.code(), backend.coordinatorPin(), 1000)) {
                byte[] confirmation = attempt.confirm(coordinator.begin(attempt.request()));
                attempt.finish(coordinator.complete(invitation.ticket(), confirmation));
            }
        }
        @Override public void close() throws Exception { coordinator.close(); backendFiles.close(); serverFiles.close(); }
    }

    @Test void authenticatedPairingPersistsOnlyPublicPinsAndIsSingleUse() throws Exception {
        String backendKey;
        try (Fixture f = new Fixture()) {
            try (var invitation = f.coordinator.pair(ID, null, 1000);
                 var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
                byte[] request = attempt.request();
                byte[] response = f.coordinator.begin(request);
                assertNull(f.backend.coordinatorPin()); assertTrue(f.coordinator.pins().isEmpty());
                byte[] confirmation = attempt.confirm(response);
                assertTrue(f.coordinator.pins().isEmpty());
                byte[] ack = f.coordinator.complete(invitation.ticket(), confirmation);
                assertNull(f.backend.coordinatorPin());
                attempt.finish(ack);
                backendKey = f.backend.publicKey();
                assertEquals(backendKey, f.coordinator.pins().get(ID));
                assertEquals(f.coordinator.publicKey(), f.backend.coordinatorPin());
                assertThrows(IOException.class, () -> f.coordinator.complete(invitation.ticket(), confirmation));
                assertThrows(IOException.class, () -> f.coordinator.begin(request));
                assertThrows(IOException.class, () -> attempt.finish(ack));
                String secret = invitation.code().exportCode();
                String diagnostics = invitation + " " + attempt + " " + f.coordinator + " " + f.backend;
                assertFalse(diagnostics.contains(secret));
                assertFalse(new String(request, java.nio.charset.StandardCharsets.ISO_8859_1).contains(secret));
                for (String file : List.of("backend-pins.json", "coordinator.pin")) {
                    Path path = temporary.toRealPath().resolve(file.equals("coordinator.pin") ? "backend" : "server").resolve(file);
                    String content = Files.readString(path);
                    assertFalse(content.contains(secret));
                    byte[] privateBytes = Files.readAllBytes(temporary.toRealPath().resolve("backend/static.key"));
                    assertFalse(content.contains(Base64.getEncoder().encodeToString(privateBytes)));
                }
            }
        }
        try (CredentialFiles files = new CredentialFiles(temporary.toRealPath().resolve("server"));
             PairingCoordinator restarted = new PairingCoordinator(files)) {
            assertEquals(backendKey, restarted.pins().get(ID));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void requestTranscriptTamperingNeverInstallsPins(int field) throws Exception {
        try (Fixture f = new Fixture(); var invitation = f.coordinator.pair(ID, null, 1000);
             var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
            byte[] request = attempt.request();
            int position = switch (field) { case 0 -> 0; case 1 -> 7; case 2 -> 11; case 3 -> 20;
                case 4 -> 32; case 5 -> 60; default -> request.length - 1; };
            request[position] ^= 1;
            assertThrows(IOException.class, () -> f.coordinator.begin(request));
            assertTrue(f.coordinator.pins().isEmpty()); assertNull(f.backend.coordinatorPin());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void responseConfirmationAndAckTamperingFailClosed(int phase) throws Exception {
        try (Fixture f = new Fixture(); var invitation = f.coordinator.pair(ID, null, 1000);
             var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
            byte[] response = f.coordinator.begin(attempt.request());
            if (phase < 3) {
                response[phase == 0 ? 20 : phase == 1 ? 50 : 100] ^= 1;
                assertThrows(IOException.class, () -> attempt.confirm(response));
                assertTrue(f.coordinator.pins().isEmpty());
            } else {
                byte[] confirmation = attempt.confirm(response);
                if (phase == 3) {
                    confirmation[0] ^= 1;
                    assertThrows(IOException.class, () -> f.coordinator.complete(invitation.ticket(), confirmation));
                    assertTrue(f.coordinator.pins().isEmpty());
                } else {
                    byte[] acknowledgement = f.coordinator.complete(invitation.ticket(), confirmation);
                    acknowledgement[0] ^= 1;
                    assertThrows(IOException.class, () -> attempt.finish(acknowledgement));
                }
            }
            assertNull(f.backend.coordinatorPin());
        }
    }

    @Test void wrongCodeExpiryRevocationAndNewInvitationInvalidateOldExchange() throws Exception {
        try (Fixture f = new Fixture(); var invitation = f.coordinator.pair(ID, null, 1000);
             var wrong = PairingCode.generate();
             var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, wrong, null, 1000)) {
            assertThrows(IOException.class, () -> f.coordinator.begin(attempt.request()));
            try (var valid = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
                byte[] confirmation = valid.confirm(f.coordinator.begin(valid.request()));
                clock.set(1_000_000_000L);
                assertThrows(IOException.class, () -> f.coordinator.complete(invitation.ticket(), confirmation));
            }
            try (var fresh = f.coordinator.pair(ID, null, 1000);
                 var valid = new BackendPairing(f.backend, fresh.ticket(), ID, fresh.code(), null, 1000)) {
                byte[] request = valid.request();
                f.coordinator.revoke(ID);
                assertThrows(IOException.class, () -> f.coordinator.begin(request));
            }
            assertTrue(f.coordinator.pins().isEmpty());
        }
    }

    @Test void concurrentCompletionInstallsExactlyOnce() throws Exception {
        try (Fixture f = new Fixture(); var invitation = f.coordinator.pair(ID, null, 1000);
             var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
            byte[] proof = attempt.confirm(f.coordinator.begin(attempt.request()));
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) results.add(workers.submit(() -> {
                    try { f.coordinator.complete(invitation.ticket(), proof); return true; }
                    catch (IOException rejected) { return false; }
                }));
                assertNotEquals(results.get(0).get(), results.get(1).get());
            } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS)); }
        }
    }

    @Test void rotationIsExplicitAndRevocationDoesNotAffectOtherBackend() throws Exception {
        try (Fixture f = new Fixture()) {
            f.pair(ID);
            RemoteServerId other = new RemoteServerId("other");
            try (CredentialFiles otherFiles = new CredentialFiles(temporary.toRealPath().resolve("other"))) {
                f.pair(other, new LocalCredentials(otherFiles));
            }
            String otherPin = f.coordinator.pins().get(other);
            String oldBackend = f.backend.publicKey();
            assertThrows(IOException.class, () -> f.coordinator.pair(ID, null, 1000));
            f.backend.rotate(); f.pair(ID);
            assertNotEquals(oldBackend, f.coordinator.pins().get(ID));
            f.coordinator.revoke(ID);
            assertEquals(Map.of(other, otherPin), f.coordinator.pins());
            String coordinatorKey = f.coordinator.publicKey();
            f.coordinator.rotateCoordinator();
            assertTrue(f.coordinator.pins().isEmpty());
            assertNotEquals(coordinatorKey, f.coordinator.publicKey());
            f.pair(ID); assertEquals(f.coordinator.publicKey(), f.backend.coordinatorPin());
        }
    }

    @Test void durableFailureConsumesCodeWithoutBackendInstallation() throws Exception {
        try (Fixture f = new Fixture(); var invitation = f.coordinator.pair(ID, null, 1000);
             var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
            byte[] proof = attempt.confirm(f.coordinator.begin(attempt.request()));
            Files.createDirectory(temporary.toRealPath().resolve("server/backend-pins.json"));
            assertThrows(IOException.class, () -> f.coordinator.complete(invitation.ticket(), proof));
            assertThrows(IOException.class, () -> f.coordinator.complete(invitation.ticket(), proof));
            assertTrue(f.coordinator.pins().isEmpty()); assertNull(f.backend.coordinatorPin());
        }
    }

    @Test void pairedCredentialsConnectAndLiveRevocationRejectsReconnect() throws Exception {
        try (Fixture f = new Fixture()) {
            f.pair(ID);
            TcpCoordinator listener = f.coordinator.listen(new TcpEndpoint("127.0.0.1", 0), TcpLimits.DEFAULT, ProtocolLimits.DEFAULT);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try (NoiseKeys keys = f.backend.noiseKeys()) {
                Future<TcpChannel> accepted = worker.submit(listener::accept);
                try (TcpChannel backend = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.NOISE_KK,
                        ID, Set.of(), keys, CanonicalKey.rawPublic(f.backend.coordinatorPin()), TcpLimits.DEFAULT, ProtocolLimits.DEFAULT);
                     TcpChannel coordinator = accepted.get()) {
                    assertTrue(coordinator.authenticated());
                    f.coordinator.revoke(ID); assertTrue(coordinator.isClosed());
                    Future<TcpChannel> rejected = worker.submit(listener::accept);
                    assertThrows(IOException.class, () -> TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.NOISE_KK,
                            ID, Set.of(), keys, CanonicalKey.rawPublic(f.backend.coordinatorPin()), TcpLimits.DEFAULT, ProtocolLimits.DEFAULT));
                    assertThrows(ExecutionException.class, rejected::get);
                }
            } finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS)); }
        }
    }

    @Test void duplicateBackendKeysAndRegistryCorruptionAreRejected() throws Exception {
        try (Fixture f = new Fixture()) {
            f.pair(ID);
            RemoteServerId other = new RemoteServerId("other");
            try (var invitation = f.coordinator.pair(other, null, 1000);
                 var attempt = new BackendPairing(f.backend, invitation.ticket(), other, invitation.code(), f.backend.coordinatorPin(), 1000)) {
                assertThrows(IOException.class, () -> f.coordinator.begin(attempt.request()));
            }
            assertEquals(1, f.coordinator.pins().size());
        }
        Path registry = temporary.toRealPath().resolve("server/backend-pins.json");
        Files.writeString(registry, "{\"backend\":\"invalid\",\"backend\":\"invalid\"}");
        try (CredentialFiles files = new CredentialFiles(registry.getParent())) {
            assertThrows(IOException.class, () -> new PairingCoordinator(files));
        }
    }

    @Test void lostPrivateKeyNeverSilentlyRegeneratesForPairedCoordinator() throws Exception {
        try (Fixture f = new Fixture()) { f.pair(ID); }
        Path directory = temporary.toRealPath().resolve("server");
        Files.delete(directory.resolve("static.key"));
        try (CredentialFiles files = new CredentialFiles(directory)) {
            assertThrows(IOException.class, () -> new PairingCoordinator(files));
            assertFalse(Files.exists(directory.resolve("static.key")));
        }
    }

    @Test void freshInvitationsAndLocalRotationInvalidateStaleProofs() throws Exception {
        try (Fixture f = new Fixture(); var old = f.coordinator.pair(ID, null, 1000);
             var attempt = new BackendPairing(f.backend, old.ticket(), ID, old.code(), null, 1000)) {
            byte[] stale = attempt.confirm(f.coordinator.begin(attempt.request()));
            try (var fresh = f.coordinator.pair(ID, null, 1000);
                 var next = new BackendPairing(f.backend, fresh.ticket(), ID, fresh.code(), null, 1000)) {
                assertThrows(IOException.class, () -> f.coordinator.complete(old.ticket(), stale));
                byte[] proof = next.confirm(f.coordinator.begin(next.request()));
                byte[] ack = f.coordinator.complete(fresh.ticket(), proof);
                f.backend.rotate();
                assertThrows(IOException.class, () -> next.finish(ack));
                assertNull(f.backend.coordinatorPin());
            }
        }
    }

    @Test void invitationsAndMalformedInputRemainBounded() throws Exception {
        try (Fixture f = new Fixture()) {
            List<PairingCoordinator.Invitation> issued = new ArrayList<>();
            try {
                for (int i = 0; i < 64; i++) issued.add(f.coordinator.pair(new RemoteServerId("backend" + i), null, 1000));
                assertThrows(IOException.class, () -> f.coordinator.pair(ID, null, 1000));
                assertThrows(IOException.class, () -> f.coordinator.begin(new byte[205]));
                assertThrows(IOException.class, () -> f.coordinator.begin(new byte[0]));
                clock.set(1_000_000_000L);
                try (var available = f.coordinator.pair(ID, null, 1000)) { assertNotNull(available); }
            } finally { issued.forEach(PairingCoordinator.Invitation::close); }
        }
    }

    @Test void revokingOneLiveBackendPreservesOtherSession() throws Exception {
        try (Fixture f = new Fixture(); CredentialFiles otherFiles = new CredentialFiles(temporary.toRealPath().resolve("other"))) {
            RemoteServerId otherId = new RemoteServerId("other");
            LocalCredentials other = new LocalCredentials(otherFiles);
            f.pair(ID); f.pair(otherId, other);
            TcpCoordinator listener = f.coordinator.listen(new TcpEndpoint("127.0.0.1", 0), TcpLimits.DEFAULT, ProtocolLimits.DEFAULT);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try (NoiseKeys firstKeys = f.backend.noiseKeys(); NoiseKeys otherKeys = other.noiseKeys()) {
                Future<TcpChannel> firstAccepted = worker.submit(listener::accept);
                try (TcpChannel first = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.NOISE_KK,
                        ID, Set.of(), firstKeys, CanonicalKey.rawPublic(f.backend.coordinatorPin()), TcpLimits.DEFAULT, ProtocolLimits.DEFAULT);
                     TcpChannel firstServer = firstAccepted.get()) {
                    Future<TcpChannel> otherAccepted = worker.submit(listener::accept);
                    try (TcpChannel second = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.NOISE_KK,
                            otherId, Set.of(), otherKeys, CanonicalKey.rawPublic(other.coordinatorPin()), TcpLimits.DEFAULT, ProtocolLimits.DEFAULT);
                         TcpChannel otherServer = otherAccepted.get()) {
                        f.coordinator.revoke(ID);
                        assertTrue(firstServer.isClosed()); assertFalse(otherServer.isClosed());
                        second.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat());
                        assertInstanceOf(ApplicationMessage.Heartbeat.class, otherServer.receive().envelope().message());
                    }
                }
            } finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS)); }
        }
    }

    @Test void logsExceptionsAndObjectDescriptionsContainNoSecrets() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        String codeText;
        String privateBase64;
        try (PrintStream output = new PrintStream(captured)) {
            System.setOut(output); System.setErr(output);
            try (Fixture f = new Fixture(); var invitation = f.coordinator.pair(ID, null, 1000);
                 var attempt = new BackendPairing(f.backend, invitation.ticket(), ID, invitation.code(), null, 1000)) {
                codeText = invitation.code().exportCode();
                privateBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(temporary.toRealPath().resolve("backend/static.key")));
                System.out.println(invitation + " " + attempt + " " + f.coordinator + " " + f.backend + " " + f.backendFiles);
                byte[] response = f.coordinator.begin(attempt.request());
                response[100] ^= 1;
                IOException failure = assertThrows(IOException.class, () -> attempt.confirm(response));
                failure.printStackTrace(System.err);
            }
        } finally { System.setOut(originalOut); System.setErr(originalErr); }
        String logs = captured.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(logs.contains(codeText)); assertFalse(logs.contains(privateBase64));
        assertTrue(logs.contains("redacted"));
    }
}
