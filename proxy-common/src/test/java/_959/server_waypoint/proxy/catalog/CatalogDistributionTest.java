package _959.server_waypoint.proxy.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.pairing.CanonicalKey;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class CatalogDistributionTest {
    private static final RemoteServerId A = new RemoteServerId("a"), B = new RemoteServerId("b");
    private static final TcpLimits TCP = new TcpLimits(4, 1000, 1500, 64, 4_194_304, 10000);
    private static final LifecycleSettings LIFE = new LifecycleSettings(true, 40, 25, 100);
    private static final ApplicationCodec CODEC = new ApplicationCodec(ProtocolLimits.DEFAULT);
    private static Map<String, Map<String, RemoteListSnapshot>> data(String label) {
        return Map.of("world", Map.of("shared-name", new RemoteListSnapshot(label, new RemoteRevision(0), Map.of())));
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    private static void stop(TransportLifecycle service) throws Exception {
        assertEquals(TransportResult.SUCCESS, service.stop().toCompletableFuture().get(5, TimeUnit.SECONDS));
    }
    private static String label(CatalogReceiver.View view) {
        return view.snapshot().dimensions().get("world").get("shared-name").displayName();
    }
    @ParameterizedTest @EnumSource(TransportMode.class)
    void distributesChangesAndSeparatesIdentitiesThenExpiresDisconnectedSource(TransportMode mode) throws Exception {
        byte[] a = CanonicalKey.generatePrivate(), b = CanonicalKey.generatePrivate(), c = CanonicalKey.generatePrivate();
        try (NoiseKeys ak = CanonicalKey.noiseKeys(a); NoiseKeys bk = CanonicalKey.noiseKeys(b); NoiseKeys ck = CanonicalKey.noiseKeys(c)) {
            boolean noise = mode == TransportMode.NOISE_KK;
            CatalogCacheLimits cache = new CatalogCacheLimits(4, 2_097_152, 8_388_608, 400);
            CoordinatorAgent coordinator = new CoordinatorAgent(() -> new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), mode,
                    noise ? ck : null, Map.of(A, noise ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(a)) : new byte[0],
                    B, noise ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(b)) : new byte[0]), TCP, ProtocolLimits.DEFAULT), TCP, LIFE, cache);
            coordinator.start().toCompletableFuture().get();
            TcpEndpoint endpoint = new TcpEndpoint("127.0.0.1", coordinator.status().port());
            byte[] pin = noise ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(c)) : null;
            AtomicReference<String> source = new AtomicReference<>("from A");
            AtomicLong ar = new AtomicLong(), br = new AtomicLong();
            BackendAgent first = new BackendAgent(endpoint, mode, A, Set.of(), noise ? ak : null, pin, TCP, ProtocolLimits.DEFAULT, LIFE,
                    new CatalogPublisher(A, "A display", () -> data(source.get()), ar::incrementAndGet, ProtocolLimits.DEFAULT, 20, "minecraft:diamond"), cache);
            BackendAgent second = new BackendAgent(endpoint, mode, B, Set.of(), noise ? bk : null, pin, TCP, ProtocolLimits.DEFAULT, LIFE,
                    new CatalogPublisher(B, "B display", () -> data("from B"), br::incrementAndGet, ProtocolLimits.DEFAULT, 20, "minecraft:compass"), cache);
            try {
                first.start().toCompletableFuture().get(); second.start().toCompletableFuture().get();
                await(() -> second.remoteCatalogs().containsKey(A) && second.remoteCatalogs().get(A).state() == RemoteCatalogState.AVAILABLE
                        && first.remoteCatalogs().containsKey(B) && first.remoteCatalogs().get(B).state() == RemoteCatalogState.AVAILABLE);
                assertEquals("from A", label(second.remoteCatalogs().get(A)));
                assertEquals("from B", label(first.remoteCatalogs().get(B)));
                assertFalse(first.remoteCatalogs().containsKey(A)); assertFalse(second.remoteCatalogs().containsKey(B));
                assertEquals(mode, coordinator.catalogs().get(A).mode());
                assertEquals("minecraft:diamond", second.remoteCatalogs().get(A).iconItem());
                assertNull(second.remoteCatalogs().get(A).mode()); // no source-link authentication claim
                Map<RemoteServerId, CatalogReceiver.View> detached = coordinator.catalogs();
                source.set("edited A");
                await(() -> "edited A".equals(label(second.remoteCatalogs().get(A))));
                assertEquals("from A", label(detached.get(A)));
                assertThrows(UnsupportedOperationException.class, () -> detached.clear());
                long generation = second.status().presence().generation();
                coordinator.disconnect(B).toCompletableFuture().get();
                await(() -> second.status().presence() != null && second.status().presence().generation() > generation
                        && second.remoteCatalogs().get(A).state() == RemoteCatalogState.AVAILABLE);
                stop(first);
                await(() -> second.remoteCatalogs().get(A).state() == RemoteCatalogState.STALE);
                assertEquals("edited A", label(second.remoteCatalogs().get(A)));
                await(() -> coordinator.catalogs().get(A).snapshot() == null && second.remoteCatalogs().get(A).snapshot() == null);
                assertEquals(RemoteCatalogState.UNAVAILABLE, second.remoteCatalogs().get(A).state());
            } finally { stop(first); stop(second); stop(coordinator); }
        }
    }

    private static TcpChannel.Received received(ApplicationMessage message, UUID request, RemoteCatalogSnapshot snapshot) {
        return new TcpChannel.Received(new ApplicationEnvelope(0, request, message), snapshot);
    }
    private static void full(CatalogIndex index, RemoteServerId id, Object owner, long revision, String label) throws Exception {
        full(index, id, owner, revision, label, "minecraft:compass");
    }
    private static void full(CatalogIndex index, RemoteServerId id, Object owner, long revision, String label, String iconItem) throws Exception {
        RemoteCatalogSnapshot snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(revision),
                Map.of("world", Map.of("shared-name", new RemoteListSnapshot(label, new RemoteRevision(revision), Map.of()))), Instant.EPOCH);
        byte[] bytes = CODEC.encodeCatalog(snapshot); UUID request = UUID.randomUUID();
        index.receive(id, owner, received(new ApplicationMessage.CatalogMetadata(id, id.value(), snapshot.catalogRevision(), CatalogExportPolicy.PUBLIC, iconItem), request, null));
        index.receive(id, owner, received(new ApplicationMessage.CatalogSnapshot(id, snapshot.catalogRevision(), UUID.randomUUID(), 0,
                bytes.length, new ApplicationMessage.Bytes(bytes)), request, snapshot));
    }
    @Test void cacheBudgetsExpiryAndHighWaterMarksAreAtomic() throws Exception {
        AtomicLong clock = new AtomicLong(); Object a = new Object(), b = new Object();
        int bytes = CODEC.encodeCatalog(new RemoteCatalogSnapshot(A, new RemoteRevision(1), data("x"), Instant.EPOCH)).length;
        CatalogIndex index = new CatalogIndex(new CatalogCacheLimits(2, bytes + 64, bytes + 64, 10), clock::get);
        index.connected(A, a, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT);
        index.connected(B, b, TransportMode.PLAINTEXT, ProtocolLimits.DEFAULT);
        full(index, A, a, 5, "x");
        assertThrows(IOException.class, () -> full(index, B, b, 1, "x")); // global ceiling
        assertEquals(5, index.views().get(A).snapshot().catalogRevision().value());
        assertThrows(IOException.class, () -> full(index, A, a, 6, "x".repeat(100))); // per-server ceiling
        assertEquals("x", label(index.views().get(A)));
        assertThrows(IOException.class, () -> index.connected(new RemoteServerId("third"), new Object(), null, ProtocolLimits.DEFAULT));
        index.disconnected(A, a); clock.set(11_000_000);
        assertNull(index.views().get(A).snapshot()); assertTrue(index.retainedBytes() <= 64); // bounded identity metadata remains
        index.connected(A, a, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT);
        assertThrows(IOException.class, () -> full(index, A, a, 4, "x")); // expiry never permits revision rollback
        assertThrows(IOException.class, () -> full(index, A, a, 5, "y")); // equal-revision conflict after eviction
        full(index, A, a, 5, "x"); // identical full refresh is safe
        assertEquals(RemoteCatalogState.AVAILABLE, index.views().get(A).state());
        index.disconnected(A, a); clock.addAndGet(11_000_000); index.views();
        full(index, B, b, 1, "x"); assertNotNull(index.views().get(B).snapshot());
    }

    @Test void sourceMismatchAndOldSessionCannotReplaceIndex() throws Exception {
        CatalogIndex index = new CatalogIndex(CatalogCacheLimits.DEFAULT); Object old = new Object(), replacement = new Object();
        index.connected(A, old, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT); full(index, A, old, 2, "a");
        index.connected(A, replacement, TransportMode.PLAINTEXT, ProtocolLimits.DEFAULT); full(index, A, replacement, 3, "b");
        index.disconnected(A, old);
        assertThrows(IOException.class, () -> full(index, A, old, 4, "c"));
        assertThrows(IOException.class, () -> index.receive(A, replacement, received(
                new ApplicationMessage.CatalogInvalidate(B, new RemoteRevision(10), RemoteCatalogState.STALE), UUID.randomUUID(), null)));
        assertEquals(3, index.views().get(A).snapshot().catalogRevision().value());
        assertEquals(TransportMode.PLAINTEXT, index.views().get(A).mode());
    }

    @Test void distributorUsesDeltasResynchronizesAndFallsBackToChunkedFull() throws Exception {
        ProtocolLimits protocol = new ProtocolLimits(32768, 1_048_576, 262144, 65536, 16384, 65536, 8388608);
        CatalogIndex index = new CatalogIndex(CatalogCacheLimits.DEFAULT); Object owner = new Object();
        index.connected(A, owner, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT); full(index, A, owner, 1, "first");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (TcpCoordinator listener = new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), TransportMode.PLAINTEXT, null,
                Map.of(B, new byte[0]), TCP, protocol)) {
            Future<TcpChannel> accepted = worker.submit(listener::accept);
            try (TcpChannel backend = TcpBackend.connect(new TcpEndpoint("127.0.0.1", listener.port()), TransportMode.PLAINTEXT,
                    B, Set.of(), null, null, TCP, protocol); TcpChannel coordinator = accepted.get()) {
                CatalogDistributor distributor = new CatalogDistributor(index, coordinator);
                distributor.publish();
                assertInstanceOf(ApplicationMessage.CatalogMetadata.class, backend.receive().envelope().message());
                assertEquals(1, backend.receive().completedCatalog().catalogRevision().value());
                // An icon-only restart must fan out even when catalog content/revision are unchanged.
                full(index, A, owner, 1, "first", "minecraft:diamond");
                distributor.publish();
                var iconUpdate = (ApplicationMessage.CatalogMetadata) backend.receive().envelope().message();
                assertEquals("minecraft:diamond", iconUpdate.iconItem());
                assertEquals(1, backend.receive().completedCatalog().catalogRevision().value());
                var delta = new ApplicationMessage.CatalogDelta(A, new RemoteRevision(1), new RemoteRevision(2),
                        Map.of("world", Map.of("shared-name", new RemoteListSnapshot("second", new RemoteRevision(2), Map.of()))), Map.of(), Set.of());
                index.receive(A, owner, received(delta, UUID.randomUUID(), null));
                distributor.publish();
                TcpChannel.Received update = backend.receive();
                assertEquals(delta, update.envelope().message());
                // A bounded correlated request forces a full current snapshot without another source edit.
                backend.send(update.envelope().requestId(), new ApplicationMessage.Error(ApplicationMessage.Result.STALE_CATALOG));
                distributor.resynchronize(coordinator.receive().envelope().requestId());
                distributor.publish();
                assertInstanceOf(ApplicationMessage.CatalogMetadata.class, backend.receive().envelope().message());
                assertEquals(2, backend.receive().completedCatalog().catalogRevision().value());
                var large = new ApplicationMessage.CatalogDelta(A, new RemoteRevision(2), new RemoteRevision(3),
                        Map.of("world", Map.of("shared-name", new RemoteListSnapshot("x".repeat(60000), new RemoteRevision(3), Map.of()))), Map.of(), Set.of());
                index.receive(A, owner, received(large, UUID.randomUUID(), null));
                Future<?> publication = worker.submit(() -> { distributor.publish(); return null; });
                assertInstanceOf(ApplicationMessage.CatalogMetadata.class, backend.receive().envelope().message());
                int chunks = 0; RemoteCatalogSnapshot complete = null;
                while (complete == null) { complete = backend.receive().completedCatalog(); chunks++; }
                publication.get(); assertTrue(chunks > 1); assertEquals(3, complete.catalogRevision().value());
                assertTrue(index.retainedBytes() <= CatalogCacheLimits.DEFAULT.totalBytes());
            }
        } finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS)); }
    }

    @Test void expiryDuringRefreshDoesNotDiscardPendingSnapshotMetadata() throws Exception {
        AtomicLong clock = new AtomicLong(); Object owner = new Object();
        CatalogIndex index = new CatalogIndex(new CatalogCacheLimits(2, 1000, 2000, 10), clock::get);
        index.connected(A, owner, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT); full(index, A, owner, 1, "first");
        RemoteCatalogSnapshot updated = new RemoteCatalogSnapshot(A, new RemoteRevision(2),
                Map.of("world", Map.of("shared-name", new RemoteListSnapshot("second", new RemoteRevision(2), Map.of()))), Instant.EPOCH);
        UUID request = UUID.randomUUID(); byte[] bytes = CODEC.encodeCatalog(updated);
        index.receive(A, owner, received(new ApplicationMessage.CatalogMetadata(A, "display", updated.catalogRevision(), CatalogExportPolicy.PUBLIC, "minecraft:compass"), request, null));
        clock.set(11_000_000); index.maintain(); assertNull(index.views().get(A).snapshot());
        index.receive(A, owner, received(new ApplicationMessage.CatalogSnapshot(A, updated.catalogRevision(), UUID.randomUUID(), 0,
                bytes.length, new ApplicationMessage.Bytes(bytes)), request, updated));
        assertEquals(RemoteCatalogState.AVAILABLE, index.views().get(A).state());
        assertEquals("second", label(index.views().get(A)));
    }
}
