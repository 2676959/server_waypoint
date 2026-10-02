package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogDelta;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import java.io.IOException;
import java.util.UUID;

/** One retained backend publication, separate from the later coordinator-wide fan-out/index. */
public final class CatalogReceiver {
    public record View(RemoteCatalogSnapshot snapshot, RemoteCatalogState state, String displayName, TransportMode mode, String iconItem) {
        public View { ServerIcon.validate(iconItem); }
    }
    private final RemoteServerId id;
    private final ApplicationCodec codec;
    private RemoteCatalogSnapshot current;
    private RemoteRevision highWater;
    private byte[] fingerprint;
    private final java.util.function.Predicate<RemoteCatalogSnapshot> admission;
    private RemoteCatalogState state = RemoteCatalogState.UNAVAILABLE;
    private String displayName;
    private String iconItem = ServerIcon.DEFAULT;
    private TransportMode mode;
    private Object owner;
    private UUID request;
    private ApplicationMessage.CatalogMetadata metadata;

    public CatalogReceiver(RemoteServerId id, ProtocolLimits limits) { this(id, limits, snapshot -> true); }
    CatalogReceiver(RemoteServerId id, ProtocolLimits limits, java.util.function.Predicate<RemoteCatalogSnapshot> admission) {
        this.id = id; codec = new ApplicationCodec(limits); displayName = id.value(); this.admission = admission;
    }
    public synchronized void expire() {
        current = null; state = RemoteCatalogState.UNAVAILABLE;
    }
    public synchronized RemoteRevision revision() { return highWater == null ? new RemoteRevision(0) : highWater; }
    private void install(RemoteCatalogSnapshot candidate) throws IOException {
        byte[] bytes = codec.encodeCatalog(candidate);
        byte[] digest;
        try { digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        if (highWater != null && (candidate.catalogRevision().compareTo(highWater) < 0
                || candidate.catalogRevision().equals(highWater) && !java.util.Arrays.equals(fingerprint, digest))) {
            throw new IOException("Old or conflicting expired catalog");
        }
        if (!admission.test(candidate)) throw new IOException("Catalog cache limit");
        current = candidate; highWater = candidate.catalogRevision(); fingerprint = digest;
    }
    public synchronized void connected(Object session, TransportMode mode) {
        owner = session; this.mode = mode; metadata = null; request = null;
        state = current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE;
    }
    public synchronized void disconnected(Object session) {
        if (owner != session) return;
        owner = null; metadata = null; request = null;
        state = current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE;
    }
    public synchronized View view() {
        RemoteCatalogState visible = owner instanceof TcpChannel channel && channel.isClosed()
                ? current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE : state;
        return new View(current, visible, displayName, mode, iconItem);
    }

    /** Returns true only when a delta gap requires a correlated full-snapshot request. */
    public synchronized boolean receive(Object session, TcpChannel.Received received) throws IOException {
        if (owner != session) throw new IOException("Catalog session replaced");
        ApplicationEnvelope envelope = received.envelope();
        ApplicationMessage message = envelope.message();
        try {
            if (message instanceof ApplicationMessage.CatalogMetadata m) {
                identity(m.serverId());
                if (metadata != null || current != null && m.revision().compareTo(current.catalogRevision()) < 0) throw new IOException("Invalid catalog metadata");
                metadata = m; request = envelope.requestId();
                state = current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE; return false;
            }
            if (message instanceof ApplicationMessage.CatalogSnapshot m) {
                identity(m.serverId());
                if (metadata == null || !envelope.requestId().equals(request) || !m.revision().equals(metadata.revision())) {
                    throw new IOException("Missing catalog metadata");
                }
                RemoteCatalogSnapshot completed = received.completedCatalog();
                if (completed == null) return false;
                if (!completed.serverId().equals(id) || !completed.catalogRevision().equals(m.revision())) throw new IOException("Catalog identity mismatch");
                if (current != null) {
                    int order = completed.catalogRevision().compareTo(current.catalogRevision());
                    if (order < 0 || order == 0 && !completed.dimensions().equals(current.dimensions())) throw new IOException("Old or conflicting catalog");
                }
                completed.dimensions().forEach((dimension, lists) -> lists.forEach((name, list) -> {
                    if (list.listRevision().compareTo(completed.catalogRevision()) > 0) throw new IllegalArgumentException("Future list revision");
                    RemoteListSnapshot old = current == null ? null : current.dimensions().getOrDefault(dimension, java.util.Map.of()).get(name);
                    if (old != null && (list.listRevision().compareTo(old.listRevision()) < 0
                            || list.listRevision().equals(old.listRevision()) && !list.equals(old))) {
                        throw new IllegalArgumentException("Conflicting list revision");
                    }
                }));
                install(completed); displayName = metadata.displayName(); iconItem = metadata.iconItem(); metadata = null; request = null;
                state = RemoteCatalogState.AVAILABLE; return false;
            }
            if (message instanceof ApplicationMessage.CatalogDelta m) {
                identity(m.serverId());
                if (metadata != null) throw new IOException("Delta interleaved with snapshot");
                if (current != null && m.revision().compareTo(current.catalogRevision()) <= 0) throw new IOException("Old delta");
                if (current == null || state != RemoteCatalogState.AVAILABLE || !current.catalogRevision().equals(m.baseRevision())) {
                    state = current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE; return true;
                }
                RemoteCatalogSnapshot candidate = CatalogDelta.apply(current, m);
                install(candidate); state = RemoteCatalogState.AVAILABLE; return false;
            }
            if (message instanceof ApplicationMessage.CatalogInvalidate m) {
                identity(m.serverId());
                if (current != null && m.revision().compareTo(current.catalogRevision()) < 0) throw new IOException("Old invalidation");
                metadata = null; request = null;
                state = current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE; return false;
            }
            throw new IOException("Unexpected catalog message");
        } catch (Exception failure) {
            state = current == null ? RemoteCatalogState.UNAVAILABLE : RemoteCatalogState.STALE;
            metadata = null; request = null;
            throw new IOException("Catalog publication rejected");
        }
    }
    private void identity(RemoteServerId serverId) throws IOException {
        if (!id.equals(serverId)) throw new IOException("Catalog source mismatch");
    }
}
