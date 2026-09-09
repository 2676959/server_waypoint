package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.network.*;
import _959.server_waypoint.core.network.codec.RemoteCatalogMessageCodec;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.crossserver.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.LongSupplier;

/** Per-player bounded request admission; callers evaluate player permissions on their owning thread. */
public final class ClientCatalogSync<P> {
    private final Map<P, Long> requests = new HashMap<>();
    private final LongSupplier clock;
    public ClientCatalogSync() { this(System::nanoTime); }
    public ClientCatalogSync(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public synchronized boolean admit(P player) {
        long now = clock.getAsLong();
        Long last = requests.get(player);
        if (last != null && now - last < 4_000_000_000L) return false;
        if (last == null && requests.size() >= 4096) return false;
        requests.put(player, now); return true;
    }
    public synchronized void disconnect(P player) { requests.remove(player); }
    public synchronized void clear() { requests.clear(); }

    public static RemoteCatalogMessage snapshot(UUID request, boolean authorized,
                                               Map<RemoteServerId, CatalogReceiver.View> views) {
        if (!authorized) return new RemoteCatalogMessage(request, RemoteCatalogState.UNAUTHORIZED, Map.of());
        Map<RemoteServerId, CatalogReceiver.View> filtered = new HashMap<>();
        views.forEach((id, view) -> {
            if (view.state() != RemoteCatalogState.UNAUTHORIZED) filtered.put(id, view);
        });
        var buffer = Unpooled.buffer(256, RemoteCatalogMessageCodec.MAX_BYTES);
        try {
            var message = new RemoteCatalogMessage(request, RemoteCatalogState.AVAILABLE, filtered);
            RemoteCatalogMessageCodec.encode(buffer, message, new EncodingContext(64 * 1024 * 1024));
            // Preflight the same aggregate allocation/object limits used by the client transport.
            RemoteCatalogMessageCodec.decode(buffer, new DecodingContext(64 * 1024 * 1024, 1_000_000));
            return message;
        } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
            return new RemoteCatalogMessage(request, RemoteCatalogState.UNAVAILABLE, Map.of());
        } finally { buffer.release(); }
    }
}
