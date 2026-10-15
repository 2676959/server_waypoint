package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.RemoteServerId;
import java.util.Map;
import java.util.Objects;

/** Read-only backend facade over the bounded transport-owned index. No mutation or I/O API. */
public final class RemoteCatalogStore {
    private final CatalogIndex index;

    public RemoteCatalogStore(CatalogIndex index) { this.index = Objects.requireNonNull(index); }
    public static RemoteCatalogStore empty() { return new RemoteCatalogStore(new CatalogIndex(CatalogCacheLimits.DEFAULT)); }
    public Map<RemoteServerId, CatalogReceiver.View> snapshot() { return index.views(); }
}
