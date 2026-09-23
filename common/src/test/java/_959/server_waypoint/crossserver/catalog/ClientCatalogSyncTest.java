package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.network.*;
import _959.server_waypoint.core.network.codec.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.transport.TransportMode;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ClientCatalogSyncTest {
    private static final RemoteServerId ID = new RemoteServerId("remote");
    private static CatalogReceiver.View view(RemoteCatalogState state) {
        var snapshot = state == RemoteCatalogState.AVAILABLE || state == RemoteCatalogState.STALE
                ? new RemoteCatalogSnapshot(ID, new RemoteRevision(4), Map.of("Case:Dimension", Map.of(
                        "", new RemoteListSnapshot("", new RemoteRevision(3), Map.of()))), Instant.EPOCH) : null;
        return new CatalogReceiver.View(snapshot, state, "Same display", TransportMode.NOISE_KK, "minecraft:diamond");
    }
    @Test void roundTripRetainsExactIdentityAndStatuses() {
        for (var state : List.of(RemoteCatalogState.AVAILABLE, RemoteCatalogState.STALE, RemoteCatalogState.UNAVAILABLE)) {
            var message = new RemoteCatalogMessage(UUID.randomUUID(), RemoteCatalogState.AVAILABLE, Map.of(ID, view(state)));
            var buffer = Unpooled.buffer();
            try {
                ChunkedMessageRegistry.encode(buffer, message, new EncodingContext(64 * 1024 * 1024));
                var copy = (RemoteCatalogMessage) ChunkedMessageRegistry.decode(6, buffer, new DecodingContext(64 * 1024 * 1024, 1000000));
                assertEquals(message.requestId(), copy.requestId());
                assertEquals(state, copy.servers().get(ID).state());
                assertEquals("minecraft:diamond", copy.servers().get(ID).iconItem());
                assertEquals(message.servers().get(ID).mode(), copy.servers().get(ID).mode());
                if (state != RemoteCatalogState.UNAVAILABLE) assertEquals(message.servers().get(ID).snapshot().dimensions(), copy.servers().get(ID).snapshot().dimensions());
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }
    @Test void filtersDeniedIdentitiesAndGlobalDenialClearsEverything() {
        var hidden = new RemoteServerId("hidden");
        var views = Map.of(ID, view(RemoteCatalogState.AVAILABLE), hidden, view(RemoteCatalogState.UNAUTHORIZED));
        assertEquals(Set.of(ID), ClientCatalogSync.snapshot(UUID.randomUUID(), true, views).servers().keySet());
        var denied = ClientCatalogSync.snapshot(UUID.randomUUID(), false, views);
        assertEquals(RemoteCatalogState.UNAUTHORIZED, denied.state()); assertTrue(denied.servers().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new RemoteCatalogMessage(UUID.randomUUID(), RemoteCatalogState.AVAILABLE,
                Map.of(hidden, view(RemoteCatalogState.AVAILABLE))));
    }
    @Test void emptyAvailableDiffersFromUnavailable() {
        var empty = ClientCatalogSync.snapshot(UUID.randomUUID(), true, Map.of());
        assertEquals(RemoteCatalogState.AVAILABLE, empty.state()); assertTrue(empty.servers().isEmpty());
        var excessive = new HashMap<RemoteServerId, CatalogReceiver.View>();
        for (int i = 0; i < 257; i++) excessive.put(new RemoteServerId("s" + i), view(RemoteCatalogState.UNAVAILABLE));
        assertEquals(RemoteCatalogState.UNAVAILABLE, ClientCatalogSync.snapshot(UUID.randomUUID(), true, excessive).state());
    }
    @Test void malformedAndAllocationBombsReject() {
        var message = new RemoteCatalogMessage(UUID.randomUUID(), RemoteCatalogState.AVAILABLE, Map.of(ID, view(RemoteCatalogState.AVAILABLE)));
        var buffer = Unpooled.buffer();
        try {
            RemoteCatalogMessageCodec.encode(buffer, message, new EncodingContext(64 * 1024 * 1024));
            for (int n = 0; n < buffer.writerIndex(); n++) {
                var truncated = buffer.copy(0, n);
                try { assertThrows(RuntimeException.class, () -> RemoteCatalogMessageCodec.decode(truncated, new DecodingContext(64 * 1024 * 1024, 1000000))); }
                finally { truncated.release(); }
            }
            assertThrows(IllegalArgumentException.class, () -> RemoteCatalogMessageCodec.decode(buffer, new DecodingContext(32, 4)));
            buffer.readerIndex(0); buffer.setInt(20, 257);
            assertThrows(IllegalArgumentException.class, () -> RemoteCatalogMessageCodec.decode(buffer, new DecodingContext(64 * 1024 * 1024, 1000000)));
        } finally { buffer.release(); }
    }
    @Test void requestsAreBoundedAndDisconnectResetsAdmission() {
        AtomicLong clock = new AtomicLong(); var sync = new ClientCatalogSync<String>(clock::get);
        assertTrue(sync.admit("p")); assertFalse(sync.admit("p")); clock.set(4_000_000_000L); assertTrue(sync.admit("p"));
        sync.disconnect("p"); assertTrue(sync.admit("p"));
        for (int n = 1; n < 4096; n++) assertTrue(sync.admit("p" + n));
        assertFalse(sync.admit("overflow")); sync.clear(); assertTrue(sync.admit("overflow"));
    }
    @Test void oversizedAggregateFailsClosedInsteadOfPublishingAPartialDirectory() {
        var waypoints = new HashMap<String, RemoteWaypointSnapshot>();
        for (int i = 0; i < 16; i++) waypoints.put("w" + i, new RemoteWaypointSnapshot("w", "W",
                new _959.server_waypoint.core.waypoint.WaypointPos(1, 2, 3), 0, 0, false, List.of(), "x".repeat(60000)));
        var dimensions = Map.of("world", Map.of("list", new RemoteListSnapshot("list", new RemoteRevision(0), waypoints)));
        var views = new HashMap<RemoteServerId, CatalogReceiver.View>();
        for (int i = 0; i < 9; i++) {
            var id = new RemoteServerId("s" + i);
            views.put(id, new CatalogReceiver.View(new RemoteCatalogSnapshot(id, new RemoteRevision(0), dimensions, Instant.EPOCH),
                    RemoteCatalogState.AVAILABLE, id.value(), TransportMode.NOISE_KK, "minecraft:diamond"));
        }
        var response = ClientCatalogSync.snapshot(UUID.randomUUID(), true, views);
        assertEquals(RemoteCatalogState.UNAVAILABLE, response.state()); assertTrue(response.servers().isEmpty());
    }

    @Test void requestCodecIsExactlySixteenBytes() {
        var request = new RemoteCatalogRequestMessage(UUID.randomUUID()); var buffer = Unpooled.buffer();
        try {
            ChunkedMessageRegistry.encode(buffer, request, new EncodingContext(16)); assertEquals(16, buffer.readableBytes());
            assertEquals(request, ChunkedMessageRegistry.decode(7, buffer, new DecodingContext(16, 4)));
        } finally { buffer.release(); }
    }
}
