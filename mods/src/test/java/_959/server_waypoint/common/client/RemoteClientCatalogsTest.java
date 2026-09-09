package _959.server_waypoint.common.client;

import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RemoteClientCatalogsTest {
    @Test void reconnectAndReplacedRequestsRejectLateResponses() {
        AtomicLong now = new AtomicLong(); var cache = new RemoteClientCatalogs(now::get); cache.clear();
        var old = cache.poll(); cache.clear(); var current = cache.poll();
        assertFalse(cache.apply(new RemoteCatalogMessage(old.requestId(), RemoteCatalogState.AVAILABLE, Map.of())));
        assertTrue(cache.apply(new RemoteCatalogMessage(current.requestId(), RemoteCatalogState.AVAILABLE, Map.of())));
        assertFalse(cache.apply(new RemoteCatalogMessage(current.requestId(), RemoteCatalogState.UNAUTHORIZED, Map.of())));
        assertEquals(RemoteCatalogState.AVAILABLE, cache.state());
    }
    @Test void refreshReplacesRemoteStateAndDenialClearsIt() {
        AtomicLong now = new AtomicLong(); var cache = new RemoteClientCatalogs(now::get); cache.clear();
        var id = new RemoteServerId("server");
        var view = new CatalogReceiver.View(new RemoteCatalogSnapshot(id, new RemoteRevision(0), Map.of(), Instant.EPOCH),
                RemoteCatalogState.AVAILABLE, "Display", null);
        assertTrue(cache.apply(new RemoteCatalogMessage(cache.poll().requestId(), RemoteCatalogState.AVAILABLE, Map.of(id, view))));
        assertThrows(UnsupportedOperationException.class, () -> cache.snapshot().clear());
        now.set(5_000_000_000L);
        assertTrue(cache.apply(new RemoteCatalogMessage(cache.poll().requestId(), RemoteCatalogState.UNAUTHORIZED, Map.of())));
        assertTrue(cache.snapshot().isEmpty()); assertEquals(RemoteCatalogState.UNAUTHORIZED, cache.state());
        cache.clear(); assertTrue(cache.snapshot().isEmpty());
    }
    @Test void missingResponseExpiresAndOnlyOneRequestIsPending() {
        AtomicLong now = new AtomicLong(); var cache = new RemoteClientCatalogs(now::get); cache.clear();
        var first = cache.poll(); now.set(5_000_000_000L); assertNull(cache.poll());
        now.set(15_000_000_000L); var second = cache.poll(); assertNotNull(second);
        assertFalse(cache.apply(new RemoteCatalogMessage(first.requestId(), RemoteCatalogState.AVAILABLE, Map.of())));
        assertEquals(RemoteCatalogState.UNAVAILABLE, cache.state());
    }
}
