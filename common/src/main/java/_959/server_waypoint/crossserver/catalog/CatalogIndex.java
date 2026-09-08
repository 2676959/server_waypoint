package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;

/** Serialized admission and immutable readers. No socket I/O occurs under the index lock. */
public final class CatalogIndex {
    public record Publication(CatalogReceiver.View view, ApplicationMessage.CatalogDelta delta, RemoteRevision revision) { }
    private static final class Entry {
        CatalogReceiver receiver;
        ApplicationMessage.CatalogDelta delta;
        int bytes;
        int deltaBytes;
        int metadataBytes;
        int pendingMetadataBytes;
        long staleSince;
        boolean stale;
        Object owner;
    }
    private final Map<RemoteServerId, Entry> entries = new HashMap<>();
    private final CatalogCacheLimits limits;
    private final LongSupplier clock;

    public CatalogIndex(CatalogCacheLimits limits) { this(limits, System::nanoTime); }
    public CatalogIndex(CatalogCacheLimits limits, LongSupplier clock) {
        this.limits = Objects.requireNonNull(limits); this.clock = Objects.requireNonNull(clock);
    }
    public synchronized void connected(RemoteServerId id, Object owner, TransportMode mode, ProtocolLimits protocol) throws IOException {
        Entry entry = entries.get(id);
        if (entry == null) {
            if (entries.size() >= limits.servers()) throw new IOException("Catalog identity limit");
            entry = new Entry();
            entry.metadataBytes = id.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (entry.metadataBytes > limits.perServerBytes() || retainedBytes() + entry.metadataBytes > limits.totalBytes()) {
                throw new IOException("Catalog metadata cache limit");
            }
            Entry target = entry;
            ApplicationCodec codec = new ApplicationCodec(protocol);
            entry.receiver = new CatalogReceiver(id, protocol, candidate -> {
                int size = codec.encodeCatalog(candidate).length;
                // Deltas are optional acceleration; discard them before rejecting authoritative data.
                entries.values().forEach(value -> { value.delta = null; value.deltaBytes = 0; });
                long total = retainedBytes();
                if (size + target.metadataBytes + target.pendingMetadataBytes > limits.perServerBytes()
                        || total - target.bytes + size > limits.totalBytes()) return false;
                target.bytes = size;
                return true;
            });
            entries.put(id, entry);
        }
        entry.receiver.connected(owner, mode);
        entry.owner = owner; entry.pendingMetadataBytes = 0;
        refresh(entry);
    }
    public synchronized boolean contains(RemoteServerId id) { return entries.containsKey(id); }
    public synchronized boolean receive(RemoteServerId id, Object owner, TcpChannel.Received received) throws IOException {
        Entry entry = entries.get(id);
        if (entry == null || entry.owner != owner) throw new IOException("Unadmitted catalog source or session");
        try {
            if (received.envelope().message() instanceof ApplicationMessage.CatalogMetadata metadata) {
                entries.values().forEach(value -> { value.delta = null; value.deltaBytes = 0; });
                int bytes = metadata.displayName().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (entry.bytes + entry.metadataBytes + bytes > limits.perServerBytes()
                        || retainedBytes() - entry.pendingMetadataBytes + bytes > limits.totalBytes()) {
                    throw new IOException("Catalog metadata cache limit");
                }
                entry.pendingMetadataBytes = bytes;
            }
            boolean gap = entry.receiver.receive(owner, received);
            if (!gap && received.envelope().message() instanceof ApplicationMessage.CatalogDelta delta) {
                int size = new ApplicationCodec(ProtocolLimits.DEFAULT).encode(received.envelope()).length;
                long total = retainedBytes();
                if (entry.bytes + entry.metadataBytes + size <= limits.perServerBytes() && total + size <= limits.totalBytes()) {
                    entry.delta = delta; entry.deltaBytes = size;
                }
            }
            if (received.completedCatalog() != null || received.envelope().message() instanceof ApplicationMessage.CatalogInvalidate) {
                entry.pendingMetadataBytes = 0;
                entry.metadataBytes = entry.receiver.view().displayName().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
            return gap;
        } catch (IOException | RuntimeException failure) {
            entry.pendingMetadataBytes = 0;
            throw failure;
        } finally { refresh(entry); }
    }
    public synchronized void disconnected(RemoteServerId id, Object owner) {
        Entry entry = entries.get(id);
        if (entry != null && entry.owner == owner) {
            entry.receiver.disconnected(owner); entry.owner = null; entry.pendingMetadataBytes = 0; refresh(entry);
        }
    }
    public synchronized void disconnected(Object owner) {
        for (RemoteServerId id : entries.keySet()) disconnected(id, owner);
    }
    /** Coordinator expiry notifications discard content while retaining bounded revision fingerprints. */
    public synchronized void expire(RemoteServerId id) {
        Entry entry = entries.get(id);
        if (entry != null) discard(entry);
    }
    private void discard(Entry entry) {
        entry.receiver.expire(); entry.bytes = 0; entry.delta = null; entry.deltaBytes = 0;
    }
    private void refresh(Entry entry) {
        CatalogReceiver.View view = entry.receiver.view();
        if (view.state() == RemoteCatalogState.AVAILABLE) { entry.stale = false; return; }
        if (!entry.stale) { entry.stale = true; entry.staleSince = clock.getAsLong(); }
        if (view.snapshot() != null && clock.getAsLong() - entry.staleSince >= limits.staleMillis() * 1_000_000L) discard(entry);
    }
    public synchronized Map<RemoteServerId, CatalogReceiver.View> views() {
        Map<RemoteServerId, CatalogReceiver.View> result = new HashMap<>();
        entries.forEach((id, entry) -> { refresh(entry); result.put(id, entry.receiver.view()); });
        return Map.copyOf(result);
    }
    public synchronized Set<RemoteServerId> serverIds() { return Set.copyOf(entries.keySet()); }
    public synchronized void maintain() { entries.values().forEach(this::refresh); }
    public synchronized Publication publication(RemoteServerId id) {
        Entry entry = entries.get(id);
        if (entry == null) return null;
        refresh(entry);
        return new Publication(entry.receiver.view(), entry.delta, entry.receiver.revision());
    }
    public synchronized long retainedBytes() {
        return entries.values().stream().mapToLong(entry -> entry.bytes + entry.deltaBytes + entry.metadataBytes + entry.pendingMetadataBytes).sum();
    }
    public synchronized void clear() { entries.clear(); }
}
