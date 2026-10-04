package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteCatalogSnapshot;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.RemoteRevision;
import _959.server_waypoint.crossserver.protocol.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Accessed only under the channel state monitor. Histories are never evicted within a session. */
final class TcpSessionState {
    private record Operation(UUID id, int type) { }
    private static final class Publication {
        final RemoteServerId serverId;
        final RemoteRevision revision;
        final UUID snapshotId;
        final byte[] bytes;
        final long started = System.nanoTime();
        int offset;
        Publication(ApplicationMessage.CatalogSnapshot first) {
            serverId = first.serverId();
            revision = first.revision();
            snapshotId = first.snapshotId();
            bytes = new byte[first.totalBytes()];
        }
    }
    private final Set<Operation> seen = new HashSet<>();
    private final Map<UUID, Publication> publications = new HashMap<>();
    private record Pending(int type, long started) { }
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final TcpLimits limits;
    private final ProtocolLimits protocol;
    private int retained;
    long sequence;

    TcpSessionState(TcpLimits limits, ProtocolLimits protocol) {
        this.limits = limits;
        this.protocol = protocol;
    }

    RemoteCatalogSnapshot accept(ApplicationEnvelope envelope, ApplicationCodec codec) throws IOException {
        if (sequence >= limits.sessionMessages() || envelope.sequence() != sequence) throw new IOException("Invalid sequence");
        sequence++;
        Operation operation = new Operation(envelope.requestId(), ApplicationCodec.typeId(envelope.message()));
        ApplicationMessage.CatalogSnapshot chunk = envelope.message() instanceof ApplicationMessage.CatalogSnapshot c ? c : null;
        Publication publication = publications.get(envelope.requestId());
        if (chunk == null || publication == null) {
            if (seen.contains(operation)) throw new IOException("Replayed operation");
            claim(192);
            seen.add(operation);
        }
        int type = operation.type();
        if (type == 1 || type == 20 || type == 23) {
            if (!pending.containsKey(operation.id()) && pending.size() >= limits.pendingRequests()) {
                throw new IOException("Pending request limit");
            }
            if (!pending.containsKey(operation.id())) {
                claim(128);
                pending.put(operation.id(), new Pending(type, System.nanoTime()));
            }
        }
        if (chunk == null) return null;
        if (publication == null) {
            if (chunk.offset() != 0 || publications.size() >= limits.pendingRequests()
                    || chunk.totalBytes() > protocol.catalogBytes()) throw new IOException("Invalid publication start");
            claim(chunk.totalBytes() + 256);
            publication = new Publication(chunk);
            publications.put(operation.id(), publication);
        }
        if (chunk.offset() != publication.offset || chunk.totalBytes() != publication.bytes.length
                || !chunk.serverId().equals(publication.serverId) || !chunk.revision().equals(publication.revision)
                || !chunk.snapshotId().equals(publication.snapshotId)) throw new IOException("Inconsistent publication");
        byte[] bytes = chunk.data().copy();
        System.arraycopy(bytes, 0, publication.bytes, publication.offset, bytes.length);
        publication.offset += bytes.length;
        if (publication.offset != publication.bytes.length) return null;
        RemoteCatalogSnapshot catalog = codec.decodeCatalog(publication.bytes, Instant.now());
        if (!catalog.serverId().equals(publication.serverId) || !catalog.catalogRevision().equals(publication.revision)) {
            throw new IOException("Catalog identity mismatch");
        }
        publications.remove(operation.id());
        retained -= publication.bytes.length + 256;
        return catalog;
    }

    void response(ApplicationEnvelope envelope) {
        int type = ApplicationCodec.typeId(envelope.message());
        Pending request = pending.get(envelope.requestId());
        if (request != null && (type == 30 || type == 26 || request.type() == 1 && type == 2
                || request.type() == 20 && (type == 21 || type == 22) || request.type() == 23 && type == 24)) {
            pending.remove(envelope.requestId());
            retained -= 128;
        }
    }

    private void claim(int bytes) throws IOException {
        if (bytes > limits.retainedBytes() - retained) throw new IOException("Retained byte limit");
        retained += bytes;
    }

    boolean expired(long now) {
        long timeout = limits.operationMillis() * 1_000_000L;
        return pending.values().stream().anyMatch(request -> now - request.started() >= timeout)
                || publications.values().stream().anyMatch(p -> now - p.started >= timeout);
    }

    void clear() {
        seen.clear(); publications.clear(); pending.clear(); retained = 0;
    }
}
