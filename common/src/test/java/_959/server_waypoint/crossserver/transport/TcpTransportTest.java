package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.*;
import com.southernstorm.noise.protocol.Noise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class TcpTransportTest {
    private static final RemoteServerId ID = new RemoteServerId("backend");
    private static final ProtocolLimits PROTOCOL = ProtocolLimits.DEFAULT;
    private static final ApplicationCodec CODEC = new ApplicationCodec(PROTOCOL);
    private static final TcpLimits LIMITS = new TcpLimits(4, 1000, 3000, 64, 4_194_304, 65_536);

    private static final class Fixture implements AutoCloseable {
        final byte[] backendPrivate = new byte[32];
        final byte[] backendPublic = new byte[32];
        final byte[] coordinatorPrivate = new byte[32];
        final byte[] coordinatorPublic = new byte[32];
        final NoiseKeys backendKeys;
        final NoiseKeys coordinatorKeys;
        final TransportMode mode;
        final TcpLimits limits;
        final TcpCoordinator listener;
        final ExecutorService workers = Executors.newFixedThreadPool(4);
        Fixture(TransportMode mode) throws Exception { this(mode, LIMITS); }
        Fixture(TransportMode mode, TcpLimits limits) throws Exception {
            this.mode = mode;
            this.limits = limits;
            var dh = Noise.createDH("25519");
            try {
                dh.generateKeyPair(); dh.getPrivateKey(backendPrivate, 0); dh.getPublicKey(backendPublic, 0);
                dh.generateKeyPair(); dh.getPrivateKey(coordinatorPrivate, 0); dh.getPublicKey(coordinatorPublic, 0);
            } finally { dh.destroy(); }
            backendKeys = new NoiseKeys(backendPrivate);
            coordinatorKeys = new NoiseKeys(coordinatorPrivate);
            listener = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), mode,
                    mode == TransportMode.NOISE_KK ? coordinatorKeys : null,
                    Map.of(ID, mode == TransportMode.NOISE_KK ? backendPublic : new byte[0]), limits, PROTOCOL);
        }
        TcpChannel connect() throws IOException {
            return TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), mode, ID, Set.of(1, 7),
                    mode == TransportMode.NOISE_KK ? backendKeys : null,
                    mode == TransportMode.NOISE_KK ? coordinatorPublic : null, limits, PROTOCOL);
        }
        Future<TcpChannel> accept() { return workers.submit(listener::accept); }
        @Override public void close() throws Exception {
            listener.close(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(4, TimeUnit.SECONDS));
            backendKeys.close(); coordinatorKeys.close();
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void bidirectionalCorrelationsAndFragmentedCatalog(TransportMode mode) throws Exception {
        try (Fixture f = new Fixture(mode)) {
            Future<TcpChannel> accepted = f.accept();
            try (TcpChannel backend = f.connect(); TcpChannel coordinator = accepted.get()) {
                assertEquals(mode == TransportMode.NOISE_KK, coordinator.authenticated());
                assertEquals(Set.of(1, 7), coordinator.capabilities());
                UUID request = UUID.randomUUID();
                backend.send(request, new ApplicationMessage.RegisterServer(ID, 1, Set.of(1, 7)));
                assertEquals(0, coordinator.receive().envelope().sequence());
                coordinator.send(request, new ApplicationMessage.RegisterResult(ID, ApplicationMessage.Result.SUCCESS));
                assertEquals(request, backend.receive().envelope().requestId());
                Map<String, RemoteListSnapshot> lists = new HashMap<>();
                for (int i = 0; i < 4; i++) lists.put("list" + i,
                        new RemoteListSnapshot("x".repeat(60_000), new RemoteRevision(0), Map.of()));
                RemoteCatalogSnapshot catalog = new RemoteCatalogSnapshot(ID, new RemoteRevision(3),
                        Map.of("dimension", lists), Instant.EPOCH);
                byte[] bytes = CODEC.encodeCatalog(catalog);
                UUID publication = UUID.randomUUID();
                UUID snapshot = UUID.randomUUID();
                for (int offset = 0; offset < bytes.length; offset += 120_000) {
                    int end = Math.min(offset + 120_000, bytes.length);
                    backend.send(publication, new ApplicationMessage.CatalogSnapshot(ID, new RemoteRevision(3),
                            snapshot, offset, bytes.length, new ApplicationMessage.Bytes(Arrays.copyOfRange(bytes, offset, end))));
                    TcpChannel.Received received = coordinator.receive();
                    if (end == bytes.length) assertEquals(catalog.dimensions(), received.completedCatalog().dimensions());
                    else assertNull(received.completedCatalog());
                }
                backend.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat());
                coordinator.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat());
                assertInstanceOf(ApplicationMessage.Heartbeat.class, backend.receive().envelope().message());
                assertInstanceOf(ApplicationMessage.Heartbeat.class, coordinator.receive().envelope().message());
            }
            assertEquals(0, f.listener.connectionCount());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"localhost", "0.0.0.0", "::", "192.168.0.1", "127.1", "127.0.0.1.example", "::ffff:127.0.0.1", "[::1%lo0]"})
    void rejectsPlaintextNonliteralOrNonloopback(String host) {
        assertThrows(IllegalArgumentException.class, () -> new TcpEndpoint(host, 1).resolve(TransportMode.PLAINTEXT));
    }

    @ParameterizedTest @ValueSource(strings = {"127.0.0.1", "127.2.3.4", "::1", "[::1]", "0:0:0:0:0:0:0:1"})
    void acceptsLiteralLoopback(String host) throws Exception {
        assertTrue(new TcpEndpoint(host, 1).resolve(TransportMode.PLAINTEXT).getAddress().isLoopbackAddress());
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void duplicateIdentityAndDisconnectStormReleaseReservations(TransportMode mode) throws Exception {
        try (Fixture f = new Fixture(mode)) {
            for (int i = 0; i < 12; i++) {
                Future<TcpChannel> accepted = f.accept();
                try (TcpChannel backend = f.connect(); TcpChannel coordinator = accepted.get()) {
                    Future<TcpChannel> duplicate = f.accept();
                    assertThrows(IOException.class, f::connect);
                    assertThrows(ExecutionException.class, duplicate::get);
                    assertFalse(coordinator.isClosed());
                }
                assertEquals(0, f.listener.connectionCount());
            }
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void rejectsModeMismatchWithoutFallback(TransportMode mode) throws Exception {
        try (Fixture f = new Fixture(mode)) {
            Future<TcpChannel> accepted = f.accept();
            TransportMode other = mode == TransportMode.NOISE_KK ? TransportMode.PLAINTEXT : TransportMode.NOISE_KK;
            assertThrows(IOException.class, () -> TcpBackend.connect(new TcpEndpoint("127.0.0.1", f.listener.port()),
                    other, ID, Set.of(), other == TransportMode.NOISE_KK ? f.backendKeys : null,
                    other == TransportMode.NOISE_KK ? f.coordinatorPublic : null, LIMITS, PROTOCOL));
            assertThrows(ExecutionException.class, accepted::get);
            assertEquals(0, f.listener.connectionCount());
        }
    }

    @Test void wrongPinAndUnknownIdFailClosed() throws Exception {
        try (Fixture f = new Fixture(TransportMode.NOISE_KK)) {
            for (boolean wrongId : new boolean[]{false, true}) {
                Future<TcpChannel> accepted = f.accept();
                assertThrows(IOException.class, () -> TcpBackend.connect(new TcpEndpoint("127.0.0.1", f.listener.port()),
                        f.mode, wrongId ? new RemoteServerId("unknown") : ID, Set.of(), f.backendKeys,
                        wrongId ? f.coordinatorPublic : f.backendPublic, LIMITS, PROTOCOL));
                assertThrows(ExecutionException.class, accepted::get);
                assertEquals(0, f.listener.connectionCount());
            }
        }
    }

    @Test void slowHandshakeAndConnectionLimit() throws Exception {
        TcpLimits limits = new TcpLimits(1, 200, 1000, 64, 4_194_304, 100);
        try (Fixture f = new Fixture(TransportMode.PLAINTEXT, limits)) {
            Future<TcpChannel> first = f.accept();
            try (Socket stalled = new Socket("127.0.0.1", f.listener.port())) {
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (f.listener.connectionCount() == 0 && System.nanoTime() < until) Thread.sleep(1);
                Future<TcpChannel> second = f.accept();
                try (Socket excess = new Socket("127.0.0.1", f.listener.port())) {
                    assertThrows(ExecutionException.class, second::get);
                    assertEquals(-1, excess.getInputStream().read());
                }
                assertThrows(ExecutionException.class, first::get);
                assertEquals(-1, stalled.getInputStream().read());
                assertEquals(0, f.listener.connectionCount());
            }
        }
    }

    private static final class RawPeer implements AutoCloseable {
        final Socket socket;
        final NoiseRecordCipher cipher;
        RawPeer(Fixture f) throws Exception {
            socket = new Socket("127.0.0.1", f.listener.port());
            socket.setSoTimeout(3000);
            byte[] first = TcpWire.hello(f.mode, ID, Set.of(), false);
            TcpWire.write(socket, first, 65_536);
            byte[] second = TcpWire.read(socket, 65_536);
            cipher = f.mode == TransportMode.NOISE_KK ? NoiseRecordCipher.handshake(socket, true, f.backendKeys,
                    f.coordinatorPublic, TcpWire.prologue(first, second)) : null;
        }
        byte[] record(byte[] clear) throws Exception { return cipher == null ? clear : cipher.encrypt(clear); }
        void write(byte[] clear) throws Exception { TcpWire.write(socket, record(clear), TcpWire.RECORD); }
        void envelope(long sequence, UUID id) throws Exception {
            byte[] frame = CODEC.encode(new ApplicationEnvelope(sequence, id, new ApplicationMessage.Heartbeat()));
            write(ByteBuffer.allocate(8 + frame.length).putInt(frame.length).putInt(0).put(frame).array());
        }
        @Override public void close() throws Exception { socket.close(); if (cipher != null) cipher.close(); }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void sequenceAndOperationReplayAreTerminal(TransportMode mode) throws Exception {
        for (boolean badSequence : new boolean[]{true, false}) {
            try (Fixture f = new Fixture(mode)) {
                Future<TcpChannel> accepted = f.accept();
                try (RawPeer peer = new RawPeer(f); TcpChannel channel = accepted.get()) {
                    UUID id = UUID.randomUUID();
                    peer.envelope(0, id); channel.receive();
                    peer.envelope(badSequence ? 0 : 1, badSequence ? UUID.randomUUID() : id);
                    assertThrows(IOException.class, channel::receive);
                    assertTrue(channel.isClosed());
                    assertThrows(IOException.class, () -> channel.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat()));
                }
            }
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void malformedFramesNeverAllocateUnboundedInput(TransportMode mode) throws Exception {
        for (int mutation = 0; mutation < 6; mutation++) {
            try (Fixture f = new Fixture(mode)) {
                Future<TcpChannel> accepted = f.accept();
                try (RawPeer peer = new RawPeer(f); TcpChannel channel = accepted.get()) {
                    switch (mutation) {
                        case 0 -> new DataOutputStream(peer.socket.getOutputStream()).writeInt(Integer.MAX_VALUE);
                        case 1 -> peer.write(ByteBuffer.allocate(9).putInt(Integer.MAX_VALUE).putInt(0).put((byte) 1).array());
                        case 2 -> peer.write(ByteBuffer.allocate(9).putInt(36).putInt(1).put((byte) 1).array());
                        case 3 -> peer.write(new byte[8]);
                        case 4 -> {
                            peer.write(ByteBuffer.allocate(9).putInt(36).putInt(0).put((byte) 1).array());
                            peer.write(ByteBuffer.allocate(9).putInt(37).putInt(1).put((byte) 1).array());
                        }
                        case 5 -> peer.write(ByteBuffer.allocate(44).putInt(36).putInt(0).put(new byte[36]).array());
                    }
                    assertThrows(IOException.class, channel::receive);
                    assertTrue(channel.isClosed());
                }
            }
        }
    }

    @Test void invalidTagsAndEncryptedRecordReplayCloseSession() throws Exception {
        for (boolean replay : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(TransportMode.NOISE_KK)) {
                Future<TcpChannel> accepted = f.accept();
                try (RawPeer peer = new RawPeer(f); TcpChannel channel = accepted.get()) {
                    byte[] frame = CODEC.encode(new ApplicationEnvelope(0, UUID.randomUUID(), new ApplicationMessage.Heartbeat()));
                    byte[] record = peer.record(ByteBuffer.allocate(frame.length + 8).putInt(frame.length).putInt(0).put(frame).array());
                    if (replay) { TcpWire.write(peer.socket, record, TcpWire.RECORD); channel.receive(); }
                    else record[record.length - 1] ^= 1;
                    TcpWire.write(peer.socket, record, TcpWire.RECORD);
                    assertThrows(IOException.class, channel::receive);
                    assertTrue(channel.isClosed());
                }
            }
        }
    }

    @Test void closeInterruptsBlockedReadAndReleasesAdmission() throws Exception {
        try (Fixture f = new Fixture(TransportMode.NOISE_KK)) {
            Future<TcpChannel> accepted = f.accept();
            try (TcpChannel backend = f.connect(); TcpChannel channel = accepted.get()) {
                Future<?> read = f.workers.submit(channel::receive);
                f.listener.close();
                assertThrows(ExecutionException.class, read::get);
                assertEquals(0, f.listener.connectionCount());
                channel.close();
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void handshakeCannotExposeChannelBeforeTranscriptBoundConfirmation(int mutation) throws Exception {
        TcpLimits limits = new TcpLimits(2, 300, 1000, 64, 4_194_304, 100);
        try (Fixture f = new Fixture(TransportMode.NOISE_KK, limits)) {
            Future<TcpChannel> accepted = f.accept();
            try (Socket socket = new Socket("127.0.0.1", f.listener.port())) {
                socket.setSoTimeout(2000);
                byte[] first = TcpWire.hello(f.mode, ID, Set.of(1), false);
                TcpWire.write(socket, first, 65_536);
                byte[] second = TcpWire.read(socket, 65_536);
                byte[] prologue = TcpWire.prologue(first, second);
                if (mutation == 0) prologue[prologue.length - 1] ^= 1;
                var handshake = new com.southernstorm.noise.protocol.HandshakeState("Noise_KK_25519_AESGCM_SHA256",
                        com.southernstorm.noise.protocol.HandshakeState.INITIATOR);
                try {
                    handshake.getLocalKeyPair().setPrivateKey(f.backendPrivate, 0);
                    handshake.getRemotePublicKey().setPublicKey(f.coordinatorPublic, 0);
                    handshake.setPrologue(prologue, 0, prologue.length);
                    handshake.start();
                    byte[] message = new byte[TcpWire.RECORD];
                    byte[] payload = mutation == 1 ? new byte[]{1} : new byte[0];
                    int length = handshake.writeMessage(message, 0, payload, 0, payload.length);
                    TcpWire.write(socket, Arrays.copyOf(message, length), TcpWire.RECORD);
                    if (mutation >= 2) {
                        byte[] reply = TcpWire.read(socket, TcpWire.RECORD);
                        handshake.readMessage(reply, 0, reply.length, new byte[TcpWire.RECORD], 0);
                        var pair = handshake.split();
                        try {
                            assertFalse(accepted.isDone());
                            if (mutation != 2) { // missing confirmation waits for absolute deadline
                                byte[] clear = mutation == 3 ? new byte[33]
                                        : CODEC.encode(new ApplicationEnvelope(0, UUID.randomUUID(), new ApplicationMessage.Heartbeat()));
                                byte[] encrypted = new byte[clear.length + 16];
                                pair.getSender().encryptWithAd(null, clear, 0, encrypted, 0, clear.length);
                                TcpWire.write(socket, encrypted, TcpWire.RECORD);
                            }
                        } finally { pair.destroy(); }
                    }
                    assertThrows(ExecutionException.class, accepted::get);
                    assertEquals(0, f.listener.connectionCount());
                } finally { handshake.destroy(); }
            }
        }
    }

    @Test void prefaceVersionsSuitesAndBoundsAreStrict() throws Exception {
        byte[] hello = TcpWire.hello(TransportMode.NOISE_KK, ID, Set.of(1), false);
        for (int position : new int[]{0, 4, 8, 12, hello.length - 4}) {
            byte[] changed = hello.clone(); changed[position + 3] ^= 4;
            assertThrows(IOException.class, () -> TcpWire.parse(changed, TransportMode.NOISE_KK, false));
        }
        // Extensible offer parser accepts sorted future suites, but the server selects only suite 1.
        byte[] future = new byte[hello.length + 4];
        System.arraycopy(hello, 0, future, 0, hello.length - 12);
        ByteBuffer.wrap(future, hello.length - 12, 16).putInt(2).putInt(1).putInt(900).putInt(0);
        assertEquals(ID, TcpWire.parse(future, TransportMode.NOISE_KK, false).serverId());
        ByteBuffer.wrap(future).putInt(future.length - 4, 900);
        assertThrows(IOException.class, () -> TcpWire.parse(future, TransportMode.NOISE_KK, true));
        for (int length = 0; length < hello.length; length++) {
            byte[] truncated = Arrays.copyOf(hello, length);
            assertThrows(Exception.class, () -> TcpWire.parse(truncated, TransportMode.NOISE_KK, false));
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void idleAndIncompleteFramesExpire(TransportMode mode) throws Exception {
        TcpLimits limits = new TcpLimits(2, 1000, 150, 64, 4_194_304, 100);
        try (Fixture f = new Fixture(mode, limits)) {
            Future<TcpChannel> accepted = f.accept();
            try (RawPeer peer = new RawPeer(f); TcpChannel channel = accepted.get()) {
                peer.write(ByteBuffer.allocate(9).putInt(36).putInt(0).put((byte) 1).array());
                assertThrows(IOException.class, channel::receive);
                assertTrue(channel.isClosed());
            }
            Future<TcpChannel> next = f.accept();
            try (TcpChannel backend = f.connect(); TcpChannel channel = next.get()) {
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (!channel.isClosed() && System.nanoTime() < until) Thread.sleep(5);
                assertTrue(channel.isClosed());
                assertEquals(0, f.listener.connectionCount());
            }
        }
    }

    @Test void fullDuplexAndConcurrentWritersRemainSerialized() throws Exception {
        try (Fixture f = new Fixture(TransportMode.NOISE_KK)) {
            Future<TcpChannel> accepted = f.accept();
            try (TcpChannel backend = f.connect(); TcpChannel coordinator = accepted.get()) {
                List<Future<?>> work = new ArrayList<>();
                for (int writer = 0; writer < 2; writer++) work.add(f.workers.submit(() -> {
                    for (int i = 0; i < 50; i++) backend.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat());
                    return null;
                }));
                work.add(f.workers.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        assertEquals(i, coordinator.receive().envelope().sequence());
                        coordinator.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat());
                    }
                    return null;
                }));
                for (int i = 0; i < 100; i++) assertEquals(i, backend.receive().envelope().sequence());
                for (Future<?> future : work) future.get();
            }
        }
    }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void stalledReaderCannotHoldWriterIndefinitely(TransportMode mode) throws Exception {
        TcpLimits limits = new TcpLimits(2, 1000, 250, 64, 4_194_304, 10_000);
        try (Fixture f = new Fixture(mode, limits)) {
            Future<TcpChannel> accepted = f.accept();
            try (RawPeer peer = new RawPeer(f); TcpChannel channel = accepted.get()) {
                peer.socket.setReceiveBufferSize(1024);
                Future<?> writing = f.workers.submit(() -> {
                    for (int i = 0; i < 1000; i++) channel.send(UUID.randomUUID(),
                            new ApplicationMessage.CatalogMetadata(ID, "x".repeat(60_000), new RemoteRevision(i), CatalogExportPolicy.PUBLIC));
                    return null;
                });
                assertThrows(ExecutionException.class, () -> writing.get(3, TimeUnit.SECONDS));
                assertTrue(channel.isClosed());
            }
        }
    }

    @Test void administrativeRevocationInvalidatesPendingHandshakeAndReadmission() throws Exception {
        try (Fixture f = new Fixture(TransportMode.NOISE_KK)) {
            Future<TcpChannel> accepted = f.accept();
            try (Socket socket = new Socket("127.0.0.1", f.listener.port())) {
                socket.setSoTimeout(2000);
                TcpWire.write(socket, TcpWire.hello(f.mode, ID, Set.of(), false), 65_536);
                TcpWire.read(socket, 65_536); // identity reserved, authentication still pending
                f.listener.replacePin(ID, null);
                assertThrows(ExecutionException.class, accepted::get);
                assertEquals(0, f.listener.connectionCount());
                Future<TcpChannel> denied = f.accept();
                assertThrows(IOException.class, f::connect);
                assertThrows(ExecutionException.class, denied::get);
                f.listener.replacePin(ID, f.backendPublic);
                Future<TcpChannel> restored = f.accept();
                try (TcpChannel backend = f.connect(); TcpChannel coordinator = restored.get()) {
                    assertTrue(coordinator.authenticated());
                }
            }
        }
    }
}
