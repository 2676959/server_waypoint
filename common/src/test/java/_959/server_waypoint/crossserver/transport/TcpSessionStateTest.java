package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpSessionStateTest {
    private final RemoteServerId id = new RemoteServerId("backend");
    private final ApplicationCodec codec = new ApplicationCodec(ProtocolLimits.DEFAULT);
    private final UUID request = UUID.randomUUID();
    private final UUID snapshot = UUID.randomUUID();
    private ApplicationMessage.CatalogSnapshot chunk(int offset, int total, byte[] bytes) {
        return new ApplicationMessage.CatalogSnapshot(id, new RemoteRevision(0), snapshot, offset, total,
                new ApplicationMessage.Bytes(bytes));
    }
    private ApplicationEnvelope envelope(long sequence, UUID uuid, ApplicationMessage message) {
        return new ApplicationEnvelope(sequence, uuid, message);
    }
    private TcpSessionState state(int pending, int retained, int messages) {
        return new TcpSessionState(new TcpLimits(1, 1000, 1000, pending, retained, messages), ProtocolLimits.DEFAULT);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rejectsInconsistentCatalogAssembly(int mutation) throws Exception {
        TcpSessionState state = state(64, 4_194_304, 100);
        state.accept(envelope(0, request, chunk(0, 20, new byte[]{1, 2})), codec);
        ApplicationMessage.CatalogSnapshot next = switch (mutation) {
            case 0 -> chunk(0, 20, new byte[]{1}); // overlap
            case 1 -> chunk(3, 20, new byte[]{1}); // gap
            case 2 -> chunk(2, 21, new byte[]{1});
            case 3 -> new ApplicationMessage.CatalogSnapshot(new RemoteServerId("other"), new RemoteRevision(0),
                    snapshot, 2, 20, new ApplicationMessage.Bytes(new byte[]{1}));
            case 4 -> new ApplicationMessage.CatalogSnapshot(id, new RemoteRevision(1), snapshot, 2, 20,
                    new ApplicationMessage.Bytes(new byte[]{1}));
            default -> new ApplicationMessage.CatalogSnapshot(id, new RemoteRevision(0), UUID.randomUUID(), 2, 20,
                    new ApplicationMessage.Bytes(new byte[]{1}));
        };
        assertThrows(IOException.class, () -> state.accept(envelope(1, request, next), codec));
    }

    @Test void catalogMustMatchOuterMetadataAndReplayIsRejectedAfterCompletion() throws Exception {
        byte[] data = codec.encodeCatalog(new RemoteCatalogSnapshot(id, new RemoteRevision(0), Map.of(), Instant.EPOCH));
        TcpSessionState state = state(64, 4_194_304, 100);
        assertNotNull(state.accept(envelope(0, request, chunk(0, data.length, data)), codec));
        assertThrows(IOException.class, () -> state.accept(envelope(1, request, chunk(0, data.length, data)), codec));
        byte[] other = codec.encodeCatalog(new RemoteCatalogSnapshot(new RemoteServerId("other"), new RemoteRevision(0), Map.of(), Instant.EPOCH));
        TcpSessionState mismatch = state(64, 4_194_304, 100);
        assertThrows(IOException.class, () -> mismatch.accept(envelope(0, request, chunk(0, other.length, other)), codec));
    }

    @Test void publicationPendingRetainedAndSequenceLimits() throws Exception {
        TcpSessionState pending = state(1, 4_194_304, 100);
        pending.accept(envelope(0, request, chunk(0, 20, new byte[]{1})), codec);
        assertThrows(IOException.class, () -> pending.accept(envelope(1, UUID.randomUUID(), chunk(0, 20, new byte[]{1})), codec));
        TcpSessionState retained = state(64, 80, 100);
        assertThrows(IOException.class, () -> retained.accept(envelope(0, request, chunk(0, 20, new byte[]{1})), codec));
        TcpSessionState sequence = state(64, 1000, 1);
        sequence.accept(envelope(0, request, new ApplicationMessage.Heartbeat()), codec);
        assertThrows(IOException.class, () -> sequence.accept(envelope(1, UUID.randomUUID(), new ApplicationMessage.Heartbeat()), codec));
        TcpSessionState exhausted = state(64, 1000, 100);
        exhausted.sequence = Long.MAX_VALUE;
        assertThrows(IOException.class, () -> exhausted.accept(envelope(Long.MAX_VALUE, request, new ApplicationMessage.Heartbeat()), codec));
    }

    @Test void boundedRequestsAllowMatchingResponseButKeepReplayHistory() throws Exception {
        TcpSessionState state = state(1, 1000, 100);
        var register = new ApplicationMessage.RegisterServer(id, _959.server_waypoint.crossserver.CrossServerProtocol.PROTOCOL_VERSION, Set.of());
        state.accept(envelope(0, request, register), codec);
        state.response(envelope(0, request, new ApplicationMessage.RegisterResult(id, ApplicationMessage.Result.SUCCESS)));
        state.accept(envelope(1, UUID.randomUUID(), register), codec);
        assertThrows(IOException.class, () -> state.accept(envelope(2, request, register), codec));
        TcpSessionState full = state(1, 1000, 100);
        full.accept(envelope(0, request, register), codec);
        assertThrows(IOException.class, () -> full.accept(envelope(1, UUID.randomUUID(), register), codec));
    }

    @Test void incompletePublicationAndRequestHaveAbsoluteExpiry() throws Exception {
        TcpSessionState state = state(64, 1000, 100);
        state.accept(envelope(0, request, chunk(0, 20, new byte[]{1})), codec);
        assertTrue(state.expired(System.nanoTime() + 1_100_000_000L));
        state.clear();
        assertFalse(state.expired(System.nanoTime() + 1_100_000_000L));
        state.accept(envelope(1, request, new ApplicationMessage.RegisterServer(id, _959.server_waypoint.crossserver.CrossServerProtocol.PROTOCOL_VERSION, Set.of())), codec);
        assertTrue(state.expired(System.nanoTime() + 1_100_000_000L));
    }
}
