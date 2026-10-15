package _959.server_waypoint.common.client;

import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import java.util.*;
import java.util.function.LongSupplier;

/** Session-only remote state. No local manager, persistence, renderer, or map-mod references. */
public final class RemoteClientCatalogs {
    private final LongSupplier clock;
    private Map<RemoteServerId, CatalogReceiver.View> servers = Map.of();
    private RemoteCatalogState state = RemoteCatalogState.UNAVAILABLE;
    private UUID pending;
    private long requestedAt, nextRequest;
    private long session;
    public RemoteClientCatalogs() { this(System::nanoTime); }
    public RemoteClientCatalogs(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public synchronized void clear() {
        session++;
        servers = Map.of(); state = RemoteCatalogState.UNAVAILABLE; pending = null; nextRequest = clock.getAsLong();
    }
    public synchronized RemoteCatalogRequestMessage poll() {
        long now = clock.getAsLong();
        if (pending != null && now - requestedAt < 15_000_000_000L) return null;
        if (pending != null) { servers = Map.of(); state = RemoteCatalogState.UNAVAILABLE; pending = null; }
        if (now - nextRequest < 0) return null;
        pending = UUID.randomUUID(); requestedAt = now; nextRequest = now + 5_000_000_000L;
        return new RemoteCatalogRequestMessage(pending);
    }
    public synchronized boolean apply(RemoteCatalogMessage message) {
        if (!message.requestId().equals(pending) || clock.getAsLong() - requestedAt >= 15_000_000_000L) return false;
        servers = message.servers(); state = message.state(); pending = null; return true;
    }
    public synchronized Map<RemoteServerId, CatalogReceiver.View> snapshot() { return servers; }
    public synchronized RemoteCatalogState state() { return state; }
    public synchronized long session() { return session; }
}
