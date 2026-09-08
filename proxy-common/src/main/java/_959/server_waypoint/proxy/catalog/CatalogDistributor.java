package _959.server_waypoint.proxy.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.TcpChannel;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One coalescing task per peer. Retains revision stamps, never per-peer catalog copies or queues. */
public final class CatalogDistributor {
    private record Sent(RemoteRevision revision, RemoteCatalogState state, String metadataFingerprint, UUID request) { }
    private final Map<RemoteServerId, Sent> sent = new ConcurrentHashMap<>();
    private final CatalogIndex index;
    private final TcpChannel channel;

    public CatalogDistributor(CatalogIndex index, TcpChannel channel) { this.index = index; this.channel = channel; }
    public void resynchronize(UUID request) {
        sent.entrySet().removeIf(entry -> entry.getValue().request().equals(request));
    }
    public void publish() throws IOException {
        ApplicationCodec codec = new ApplicationCodec(channel.protocolLimits());
        for (RemoteServerId id : index.serverIds()) {
            if (id.equals(channel.serverId())) continue;
            CatalogIndex.Publication publication = index.publication(id);
            if (publication == null) continue;
            CatalogReceiver.View view = publication.view();
            Sent previous = sent.get(id);
            RemoteRevision revision = publication.revision();
            if (revision.value() == 0 && view.snapshot() == null && previous == null) continue; // no validated publication yet
            String metadataFingerprint = fingerprint(view.displayName());
            if (previous != null && previous.revision().equals(revision) && previous.state() == view.state()
                    && previous.metadataFingerprint().equals(metadataFingerprint)) continue;
            UUID request = UUID.randomUUID();
            // Record correlation before send: the peer can request resync before send returns.
            Sent stamp = new Sent(revision, view.state(), metadataFingerprint, request);
            sent.put(id, stamp);
            if (view.snapshot() != null) {
                boolean delta = previous != null && previous.state() == RemoteCatalogState.AVAILABLE
                        && view.state() == RemoteCatalogState.AVAILABLE && publication.delta() != null
                        && previous.revision().equals(publication.delta().baseRevision());
                if (delta) {
                    try { codec.encode(new ApplicationEnvelope(0, request, publication.delta())); }
                    catch (IllegalArgumentException oversized) { delta = false; }
                }
                if (delta) channel.send(request, publication.delta());
                else if (previous == null || !previous.revision().equals(revision)
                        || view.state() == RemoteCatalogState.AVAILABLE) {
                    byte[] bytes = codec.encodeCatalog(view.snapshot());
                    channel.send(request, new ApplicationMessage.CatalogMetadata(id, view.displayName(), revision, CatalogExportPolicy.PUBLIC));
                    int chunk = Math.min(channel.protocolLimits().chunkBytes(), channel.protocolLimits().frameBytes() - 160);
                    if (chunk <= 0) throw new IOException("Catalog frame budget");
                    UUID snapshot = UUID.randomUUID();
                    for (int offset = 0; offset < bytes.length; offset += chunk) {
                        channel.send(request, new ApplicationMessage.CatalogSnapshot(id, revision, snapshot, offset, bytes.length,
                                new ApplicationMessage.Bytes(Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + chunk)))));
                    }
                }
            }
            if (view.state() != RemoteCatalogState.AVAILABLE) {
                channel.send(UUID.randomUUID(), new ApplicationMessage.CatalogInvalidate(id, revision, view.state()));
            }
        }
    }
    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
