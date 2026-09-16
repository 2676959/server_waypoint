package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.codec.ChunkedMessageManager;

import java.util.Map;
import java.util.WeakHashMap;

final class ChunkedMessageManagerRegistry {
    private static final Map<Object, ChunkedMessageManager<?>> MANAGERS = new WeakHashMap<>();

    private ChunkedMessageManagerRegistry() {
    }

    @SuppressWarnings("unchecked")
    static synchronized <P> ChunkedMessageManager<P> get(Object endpoint) {
        return (ChunkedMessageManager<P>) MANAGERS.computeIfAbsent(
                endpoint,
                ignored -> new ChunkedMessageManager<>()
        );
    }
}
