package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.TcpChannel;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single bounded publication worker; capture and encoding never run on the game thread. */
public final class CatalogPublisher {
    @FunctionalInterface public interface Revisions { long next() throws IOException; }
    private final RemoteServerId id;
    private final String displayName;
    private final CatalogSource source;
    private final Revisions revisions;
    private final ProtocolLimits limits;
    private final ApplicationCodec codec;
    private final int intervalMillis;
    private final AtomicBoolean fullRequested = new AtomicBoolean(true);
    private RemoteCatalogSnapshot current;
    private RemoteCatalogSnapshot sent;
    private TcpChannel sentOn;
    private boolean unavailable;

    public CatalogPublisher(RemoteServerId id, String displayName, CatalogSource source, Revisions revisions,
                            ProtocolLimits limits, int intervalMillis) {
        this.id = Objects.requireNonNull(id); this.displayName = Objects.requireNonNull(displayName);
        this.source = Objects.requireNonNull(source); this.revisions = Objects.requireNonNull(revisions);
        this.limits = Objects.requireNonNull(limits); codec = new ApplicationCodec(limits);
        if (intervalMillis < 1 || intervalMillis > 60_000) throw new IllegalArgumentException("Invalid publication interval");
        this.intervalMillis = intervalMillis;
        codec.encode(new ApplicationEnvelope(0, UUID.randomUUID(), new ApplicationMessage.CatalogMetadata(id, displayName,
                new RemoteRevision(0), CatalogExportPolicy.PUBLIC)));
    }
    public RemoteServerId serverId() { return id; }
    public int intervalMillis() { return intervalMillis; }
    public void requestFullSnapshot() { fullRequested.set(true); }

    public synchronized void publish(TcpChannel channel) throws IOException {
        if (!id.equals(channel.serverId())) throw new IOException("Publisher identity mismatch");
        boolean force = fullRequested.getAndSet(false) || sentOn != channel || unavailable;
        byte[] encoded;
        try {
            var captured = source.capture();
            // Normalize source revisions; only content determines publication/list revisions.
            Map<String, Map<String, RemoteListSnapshot>> normalized = new HashMap<>();
            captured.forEach((dimension, lists) -> {
                Map<String, RemoteListSnapshot> values = new HashMap<>();
                lists.forEach((name, list) -> values.put(name, new RemoteListSnapshot(list.displayName(), new RemoteRevision(0), list.waypoints())));
                normalized.put(dimension, values);
            });
            boolean changed = current == null || !sameContent(current.dimensions(), normalized);
            if (changed) {
                long next = revisions.next();
                if (next <= 0 || current != null && next <= current.catalogRevision().value()) throw new IOException("Revision did not advance");
                RemoteRevision revision = new RemoteRevision(next);
                Map<String, Map<String, RemoteListSnapshot>> revised = new HashMap<>();
                normalized.forEach((dimension, lists) -> {
                    Map<String, RemoteListSnapshot> values = new HashMap<>();
                    lists.forEach((name, list) -> {
                        RemoteListSnapshot old = current == null ? null : current.dimensions().getOrDefault(dimension, Map.of()).get(name);
                        values.put(name, old != null && sameList(old, list) ? old : new RemoteListSnapshot(list.displayName(), revision, list.waypoints()));
                    });
                    revised.put(dimension, values);
                });
                RemoteCatalogSnapshot candidate = new RemoteCatalogSnapshot(id, revision, revised, Instant.EPOCH);
                encoded = codec.encodeCatalog(candidate); // full result must fit even when sending a small delta
                current = candidate;
            } else encoded = codec.encodeCatalog(current);
        } catch (Exception failure) {
            if (!unavailable || sentOn != channel) {
                channel.send(UUID.randomUUID(), new ApplicationMessage.CatalogInvalidate(id,
                        current == null ? new RemoteRevision(0) : current.catalogRevision(), RemoteCatalogState.UNAVAILABLE));
            }
            unavailable = true; sentOn = channel; fullRequested.set(true); return;
        }
        if (!force && sent == current) return;
        UUID request = UUID.randomUUID();
        boolean deltaSent = false;
        if (!force && sent != null) {
            ApplicationMessage.CatalogDelta delta = CatalogDelta.between(sent, current);
            try {
                codec.encode(new ApplicationEnvelope(0, request, delta));
                channel.send(request, delta);
                deltaSent = true;
            } catch (IllegalArgumentException oversized) { /* Fall back to a bounded full catalog, not a different protocol. */ }
        }
        if (!deltaSent) {
            channel.send(request, new ApplicationMessage.CatalogMetadata(id, displayName, current.catalogRevision(), CatalogExportPolicy.PUBLIC));
            UUID snapshot = UUID.randomUUID();
            int chunkBytes = Math.min(limits.chunkBytes(), limits.frameBytes() - 160);
            if (chunkBytes <= 0) throw new IOException("Frame budget cannot carry catalog chunks");
            for (int offset = 0; offset < encoded.length; offset += chunkBytes) {
                byte[] chunk = Arrays.copyOfRange(encoded, offset, Math.min(encoded.length, offset + chunkBytes));
                channel.send(request, new ApplicationMessage.CatalogSnapshot(id, current.catalogRevision(), snapshot, offset,
                        encoded.length, new ApplicationMessage.Bytes(chunk)));
            }
        }
        sent = current; sentOn = channel; unavailable = false;
    }
    private static boolean sameList(RemoteListSnapshot a, RemoteListSnapshot b) {
        return a.displayName().equals(b.displayName()) && a.waypoints().equals(b.waypoints());
    }
    private static boolean sameContent(Map<String, Map<String, RemoteListSnapshot>> a, Map<String, Map<String, RemoteListSnapshot>> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (String dimension : a.keySet()) {
            if (!a.get(dimension).keySet().equals(b.get(dimension).keySet())) return false;
            for (String list : a.get(dimension).keySet()) if (!sameList(a.get(dimension).get(list), b.get(dimension).get(list))) return false;
        }
        return true;
    }
}
