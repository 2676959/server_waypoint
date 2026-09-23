package _959.server_waypoint.proxy.catalog;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.pairing.CanonicalKey;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class CatalogPublicationTest {
    @TempDir Path temporary;
    private static final RemoteServerId ID = new RemoteServerId("backend");
    private static final TcpLimits TCP = new TcpLimits(4, 1000, 1500, 64, 4_194_304, 10000);
    private static final LifecycleSettings LIFE = new LifecycleSettings(true, 40, 25, 100);
    private static final ApplicationCodec CODEC = new ApplicationCodec(ProtocolLimits.DEFAULT);
    private static void stop(TransportLifecycle lifecycle) throws Exception { assertEquals(TransportResult.SUCCESS, lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS)); }
    private static void await(BooleanSupplier check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!check.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(check.getAsBoolean());
    }
    private static Map<String, Map<String, RemoteListSnapshot>> data(String name) {
        return Map.of("dimension", Map.of(name, new RemoteListSnapshot(name, new RemoteRevision(0), Map.of())));
    }
    private static CatalogReceiver.View view(CoordinatorAgent agent) { return agent.catalogs().get(ID); }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void publishesEditsEmptyCatalogUnavailableAndReconnect(TransportMode mode) throws Exception {
        byte[] backendPrivate = CanonicalKey.generatePrivate(); byte[] serverPrivate = CanonicalKey.generatePrivate();
        try (NoiseKeys backendKey = CanonicalKey.noiseKeys(backendPrivate); NoiseKeys serverKey = CanonicalKey.noiseKeys(serverPrivate);
             CatalogRevisionSequence revisions = new CatalogRevisionSequence(temporary.toRealPath().resolve("catalog-state"))) {
            CoordinatorAgent coordinator = new CoordinatorAgent(() -> new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), mode,
                    mode == TransportMode.NOISE_KK ? serverKey : null,
                    Map.of(ID, mode == TransportMode.NOISE_KK ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(backendPrivate)) : new byte[0]),
                    TCP, ProtocolLimits.DEFAULT), TCP, LIFE);
            assertEquals(TransportResult.SUCCESS, coordinator.start().toCompletableFuture().get());
            AtomicReference<Map<String, Map<String, RemoteListSnapshot>>> source = new AtomicReference<>(data("first"));
            CatalogPublisher publisher = new CatalogPublisher(ID, "Backend display", () -> {
                if (source.get() == null) throw new IOException("source unavailable"); return source.get();
            }, revisions::next, ProtocolLimits.DEFAULT, 30, "minecraft:compass");
            BackendAgent backend = new BackendAgent(new TcpEndpoint("127.0.0.1", coordinator.status().port()), mode, ID, Set.of(1),
                    mode == TransportMode.NOISE_KK ? backendKey : null,
                    mode == TransportMode.NOISE_KK ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(serverPrivate)) : null,
                    TCP, ProtocolLimits.DEFAULT, LIFE, publisher);
            try {
                backend.start().toCompletableFuture().get();
                await(() -> view(coordinator) != null && view(coordinator).state() == RemoteCatalogState.AVAILABLE);
                long firstRevision = view(coordinator).snapshot().catalogRevision().value();
                assertEquals("Backend display", view(coordinator).displayName()); assertEquals(mode, view(coordinator).mode());
                source.set(data("second"));
                await(() -> view(coordinator).snapshot().catalogRevision().value() > firstRevision);
                assertEquals(Set.of("second"), view(coordinator).snapshot().dimensions().get("dimension").keySet());
                RemoteCatalogSnapshot good = view(coordinator).snapshot();
                source.set(null);
                await(() -> view(coordinator).state() == RemoteCatalogState.STALE);
                assertEquals(good, view(coordinator).snapshot());
                source.set(Map.of());
                await(() -> view(coordinator).state() == RemoteCatalogState.AVAILABLE && view(coordinator).snapshot().dimensions().isEmpty());
                long generation = backend.status().presence().generation();
                coordinator.disconnect(ID).toCompletableFuture().get();
                await(() -> backend.status().presence() != null && backend.status().presence().generation() > generation
                        && view(coordinator).state() == RemoteCatalogState.AVAILABLE);
                stop(backend);
                await(() -> view(coordinator).state() == RemoteCatalogState.STALE);
                assertNotNull(view(coordinator).snapshot());
            } finally { stop(backend); stop(coordinator); }
        }
    }

    private static TcpChannel.Received received(long sequence, UUID request, ApplicationMessage message, RemoteCatalogSnapshot catalog) {
        return new TcpChannel.Received(new ApplicationEnvelope(sequence, request, message), catalog);
    }
    private static void full(CatalogReceiver receiver, Object owner, RemoteCatalogSnapshot catalog) throws Exception {
        UUID request = UUID.randomUUID(); byte[] bytes = CODEC.encodeCatalog(catalog);
        receiver.receive(owner, received(0, request, new ApplicationMessage.CatalogMetadata(ID, "display", catalog.catalogRevision(), CatalogExportPolicy.PUBLIC, "minecraft:compass"), null));
        receiver.receive(owner, received(1, request, new ApplicationMessage.CatalogSnapshot(ID, catalog.catalogRevision(), UUID.randomUUID(),
                0, bytes.length, new ApplicationMessage.Bytes(bytes)), catalog));
    }
    @Test void deltaGapAndInvalidPublicationPreservePreviousSnapshot() throws Exception {
        CatalogReceiver receiver = new CatalogReceiver(ID, ProtocolLimits.DEFAULT); Object owner = new Object();
        receiver.connected(owner, TransportMode.NOISE_KK);
        RemoteCatalogSnapshot baseline = new RemoteCatalogSnapshot(ID, new RemoteRevision(1), data("first"), Instant.EPOCH);
        full(receiver, owner, baseline);
        var gap = new ApplicationMessage.CatalogDelta(ID, new RemoteRevision(2), new RemoteRevision(3), Map.of(), Map.of(), Set.of());
        assertTrue(receiver.receive(owner, received(2, UUID.randomUUID(), gap, null)));
        assertEquals(baseline, receiver.view().snapshot()); assertEquals(RemoteCatalogState.STALE, receiver.view().state());
        RemoteCatalogSnapshot recovered = new RemoteCatalogSnapshot(ID, new RemoteRevision(3), data("second"), Instant.EPOCH);
        full(receiver, owner, recovered);
        var invalid = new ApplicationMessage.CatalogDelta(ID, new RemoteRevision(3), new RemoteRevision(4), Map.of(), Map.of(), Set.of("missing"));
        assertThrows(IOException.class, () -> receiver.receive(owner, received(3, UUID.randomUUID(), invalid, null)));
        assertEquals(recovered, receiver.view().snapshot()); assertEquals(RemoteCatalogState.STALE, receiver.view().state());
        Object replacement = new Object(); receiver.connected(replacement, TransportMode.NOISE_KK);
        receiver.disconnected(owner); assertNotNull(receiver.view().snapshot());
        assertThrows(IOException.class, () -> receiver.receive(owner, received(4, UUID.randomUUID(), gap, null)));
    }

    @Test void publicationRevisionPersistenceAndEncodingDoNotHoldMutationLocks() throws Exception {
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(temporary);
        manager.addWaypoint("dimension", "public", new SimpleWaypoint("one", "O", new WaypointPos(1,2,3), 0,0,false), ignored -> { });
        CountDownLatch captured = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        CatalogPublisher publisher = new CatalogPublisher(ID, "display", CatalogSource.fromManager(manager, CatalogSelection.allPublic(), 100), () -> {
            captured.countDown();
            try { release.await(); } catch (InterruptedException failure) { throw new IOException(); }
            return 1;
        }, ProtocolLimits.DEFAULT, 100, "minecraft:compass");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (TcpCoordinator listener = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT, null,
                Map.of(ID, new byte[0]), TCP, ProtocolLimits.DEFAULT)) {
            Future<TcpChannel> accepted = workers.submit(listener::accept);
            try (TcpChannel sender = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.PLAINTEXT,
                    ID, Set.of(), null, null, TCP, ProtocolLimits.DEFAULT); TcpChannel receiver = accepted.get()) {
                Future<?> publication = workers.submit(() -> { publisher.publish(sender); return null; });
                assertTrue(captured.await(1, TimeUnit.SECONDS));
                Future<?> mutation = workers.submit(() -> manager.addWaypoint("dimension", "public",
                        new SimpleWaypoint("two", "T", new WaypointPos(4,5,6), 0,0,false), ignored -> { }));
                mutation.get(1, TimeUnit.SECONDS); // publication's disk/encoding phase holds no model lock
                release.countDown(); publication.get();
                receiver.receive();
                RemoteCatalogSnapshot snapshot = receiver.receive().completedCatalog();
                assertEquals(Set.of("one"), snapshot.dimensions().get("dimension").get("public").waypoints().keySet());
            }
        } finally { release.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS)); }
    }

    private static TcpChannel.Received nextCatalog(TcpChannel channel) throws IOException {
        while (true) {
            TcpChannel.Received received = channel.receive();
            if (!(received.envelope().message() instanceof ApplicationMessage.Heartbeat)) return received;
        }
    }
    @Test void deltaGapRequestsFullSnapshotOverTheLiveChannel() throws Exception {
        AtomicReference<Map<String, Map<String, RemoteListSnapshot>>> source = new AtomicReference<>(data("first"));
        AtomicLong revision = new AtomicLong();
        CatalogPublisher publisher = new CatalogPublisher(ID, "display", source::get, revision::incrementAndGet, ProtocolLimits.DEFAULT, 30, "minecraft:compass");
        try (TcpCoordinator listener = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT, null,
                Map.of(ID, new byte[0]), TCP, ProtocolLimits.DEFAULT)) {
            BackendAgent backend = new BackendAgent(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.PLAINTEXT,
                    ID, Set.of(), null, null, TCP, ProtocolLimits.DEFAULT, LIFE, publisher);
            try {
                backend.start().toCompletableFuture().get();
                try (TcpChannel channel = listener.accept()) {
                    ApplicationEnvelope register = channel.receive().envelope();
                    channel.send(register.requestId(), new ApplicationMessage.RegisterResult(ID, ApplicationMessage.Result.SUCCESS));
                    CatalogReceiver receiver = new CatalogReceiver(ID, ProtocolLimits.DEFAULT); receiver.connected(channel, TransportMode.PLAINTEXT);
                    receiver.receive(channel, nextCatalog(channel)); receiver.receive(channel, nextCatalog(channel));
                    source.set(data("second"));
                    assertInstanceOf(ApplicationMessage.CatalogDelta.class, nextCatalog(channel).envelope().message()); // deliberately drop revision 2
                    source.set(data("third"));
                    TcpChannel.Received delta = nextCatalog(channel);
                    assertTrue(receiver.receive(channel, delta));
                    channel.send(delta.envelope().requestId(), new ApplicationMessage.Error(ApplicationMessage.Result.STALE_CATALOG));
                    TcpChannel.Received metadata = nextCatalog(channel);
                    assertInstanceOf(ApplicationMessage.CatalogMetadata.class, metadata.envelope().message());
                    receiver.receive(channel, metadata); receiver.receive(channel, nextCatalog(channel));
                    assertEquals(RemoteCatalogState.AVAILABLE, receiver.view().state());
                    assertEquals(Set.of("third"), receiver.view().snapshot().dimensions().get("dimension").keySet());
                    assertEquals(3, receiver.view().snapshot().catalogRevision().value());
                }
            } finally { stop(backend); }
        }
    }

    @Test void oversizedDeltaFallsBackToBoundedFullSnapshotAndEncodingFailurePreservesOldState() throws Exception {
        ProtocolLimits limits = new ProtocolLimits(32768, 1_048_576, 262144, 65536, 16384, 65536, 8388608);
        AtomicReference<Map<String, Map<String, RemoteListSnapshot>>> source = new AtomicReference<>(data("first"));
        AtomicLong revisions = new AtomicLong();
        CatalogPublisher publisher = new CatalogPublisher(ID, "display", source::get, revisions::incrementAndGet, limits, 30, "minecraft:compass");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (TcpCoordinator listener = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT, null,
                Map.of(ID, new byte[0]), TCP, limits)) {
            Future<TcpChannel> accepted = workers.submit(listener::accept);
            try (TcpChannel sender = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.PLAINTEXT,
                    ID, Set.of(), null, null, TCP, limits); TcpChannel channel = accepted.get()) {
                CatalogReceiver receiver = new CatalogReceiver(ID, limits); receiver.connected(channel, TransportMode.PLAINTEXT);
                publisher.publish(sender); receiver.receive(channel, nextCatalog(channel)); receiver.receive(channel, nextCatalog(channel));
                source.set(Map.of("dimension", Map.of("first", new RemoteListSnapshot("x".repeat(60000), new RemoteRevision(0), Map.of()))));
                Future<?> publication = workers.submit(() -> { publisher.publish(sender); return null; });
                TcpChannel.Received metadata = nextCatalog(channel);
                assertInstanceOf(ApplicationMessage.CatalogMetadata.class, metadata.envelope().message()); receiver.receive(channel, metadata);
                int chunks = 0;
                while (receiver.view().state() != RemoteCatalogState.AVAILABLE) { receiver.receive(channel, nextCatalog(channel)); chunks++; }
                publication.get(); assertTrue(chunks > 1);
                RemoteCatalogSnapshot good = receiver.view().snapshot();
                source.set(Map.of("dimension", Map.of("first", new RemoteListSnapshot("x".repeat(65537), new RemoteRevision(0), Map.of()))));
                publisher.publish(sender); receiver.receive(channel, nextCatalog(channel));
                assertEquals(RemoteCatalogState.STALE, receiver.view().state()); assertEquals(good, receiver.view().snapshot());
            }
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS)); }
    }

    @Test void accumulatedDeltaBudgetAndConflictingFullRevisionAreRejectedAtomically() throws Exception {
        ProtocolLimits small = new ProtocolLimits(1024, 100, 64, 100, 100, 1000, 10000);
        CatalogReceiver receiver = new CatalogReceiver(ID, small); Object owner = new Object(); receiver.connected(owner, TransportMode.NOISE_KK);
        RemoteCatalogSnapshot baseline = new RemoteCatalogSnapshot(ID, new RemoteRevision(1), data("first"), Instant.EPOCH);
        full(receiver, owner, baseline);
        var tooLarge = new ApplicationMessage.CatalogDelta(ID, new RemoteRevision(1), new RemoteRevision(2),
                Map.of("dimension", Map.of("first", new RemoteListSnapshot("x".repeat(60), new RemoteRevision(2), Map.of()))), Map.of(), Set.of());
        assertThrows(IOException.class, () -> receiver.receive(owner, received(2, UUID.randomUUID(), tooLarge, null)));
        assertEquals(baseline, receiver.view().snapshot());
        var conflict = new RemoteCatalogSnapshot(ID, new RemoteRevision(1), data("other"), Instant.EPOCH);
        assertThrows(IOException.class, () -> full(receiver, owner, conflict));
        assertEquals(baseline, receiver.view().snapshot());
        var changedWithoutListRevision = new RemoteCatalogSnapshot(ID, new RemoteRevision(2),
                Map.of("dimension", Map.of("first", new RemoteListSnapshot("changed", new RemoteRevision(0), Map.of()))), Instant.EPOCH);
        assertThrows(IOException.class, () -> full(receiver, owner, changedWithoutListRevision));
        assertEquals(baseline, receiver.view().snapshot());
        Object replacement = new Object(); receiver.connected(replacement, TransportMode.NOISE_KK); full(receiver, replacement, baseline);
        receiver.disconnected(owner); assertEquals(RemoteCatalogState.AVAILABLE, receiver.view().state());
    }

    @Test void authoritativeWaypointEditsAdvanceCatalogAndOnlyChangedListRevision() throws Exception {
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(temporary);
        manager.addWaypoint("dimension", "changed", new SimpleWaypoint("one", "O", new WaypointPos(1,2,3), 0,0,false), ignored -> { });
        manager.addWaypoint("dimension", "keep", new SimpleWaypoint("keep", "K", new WaypointPos(4,5,6), 0,0,false), ignored -> { });
        AtomicLong revisions = new AtomicLong();
        CatalogPublisher publisher = new CatalogPublisher(ID, "display", CatalogSource.fromManager(manager, CatalogSelection.allPublic(), 100),
                revisions::incrementAndGet, ProtocolLimits.DEFAULT, 100, "minecraft:compass");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (TcpCoordinator listener = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT, null,
                Map.of(ID, new byte[0]), TCP, ProtocolLimits.DEFAULT)) {
            Future<TcpChannel> accepted = worker.submit(listener::accept);
            try (TcpChannel sender = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.PLAINTEXT,
                    ID, Set.of(), null, null, TCP, ProtocolLimits.DEFAULT); TcpChannel channel = accepted.get()) {
                CatalogReceiver receiver = new CatalogReceiver(ID, ProtocolLimits.DEFAULT); receiver.connected(channel, TransportMode.PLAINTEXT);
                publisher.publish(sender); receiver.receive(channel, nextCatalog(channel)); receiver.receive(channel, nextCatalog(channel));
                manager.addWaypoint("dimension", "changed", new SimpleWaypoint("two", "T", new WaypointPos(7,8,9), 0,0,false), ignored -> { });
                publisher.publish(sender);
                TcpChannel.Received delta = nextCatalog(channel); assertInstanceOf(ApplicationMessage.CatalogDelta.class, delta.envelope().message());
                receiver.receive(channel, delta);
                var lists = receiver.view().snapshot().dimensions().get("dimension");
                assertEquals(2, receiver.view().snapshot().catalogRevision().value());
                assertEquals(2, lists.get("changed").listRevision().value()); assertEquals(1, lists.get("keep").listRevision().value());
                assertEquals(Set.of("one", "two"), lists.get("changed").waypoints().keySet());
                publisher.publish(sender); assertEquals(2, revisions.get());
            }
        } finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS)); }
    }
}
