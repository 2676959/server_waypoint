package _959.server_waypoint.crossserver.protocol;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;

/** Canonical big-endian v1 serialization. No sockets, replay registry, authorization, or reassembly. */
public final class ApplicationCodec {
    public static final int HEADER_BYTES = 36;
    private final ProtocolLimits limits;

    public ApplicationCodec(ProtocolLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public byte[] encode(ApplicationEnvelope envelope) {
        return write(limits.frameBytes(), w -> {
            w.object();
            w.object();
            w.i(CrossServerProtocol.PROTOCOL_VERSION);
            w.i(typeId(envelope.message()));
            w.l(envelope.sequence());
            w.uuid(envelope.requestId());
            w.i(0);
            writeMessage(w, envelope.message());
            w.buf.setInt(HEADER_BYTES - 4, w.buf.writerIndex() - HEADER_BYTES);
        });
    }

    public ApplicationEnvelope decode(byte[] encoded) {
        return read(encoded, limits.frameBytes(), r -> {
            if (r.i() != CrossServerProtocol.PROTOCOL_VERSION) {
                throw invalid();
            }
            int type = r.i();
            long sequence = r.l();
            UUID request = r.uuid();
            int length = r.i();
            if (length < 0 || length != r.buf.readableBytes()) {
                throw invalid();
            }
            r.object();
            r.object();
            return new ApplicationEnvelope(sequence, request, readMessage(r, type));
        });
    }

    /** Canonical complete snapshot bytes, subsequently sliced into bounded CatalogSnapshot messages. */
    public byte[] encodeCatalog(RemoteCatalogSnapshot snapshot) {
        return write(limits.catalogBytes(), w -> {
            w.object();
            w.i(CrossServerProtocol.PROTOCOL_VERSION);
            w.server(snapshot.serverId());
            w.revision(snapshot.catalogRevision());
            w.dimensions(snapshot.dimensions());
        });
    }

    /** Receipt time is supplied by the receiver, never trusted from the wire. */
    public RemoteCatalogSnapshot decodeCatalog(byte[] encoded, Instant receivedAt) {
        Objects.requireNonNull(receivedAt, "receivedAt");
        return read(encoded, limits.catalogBytes(), r -> {
            if (r.i() != CrossServerProtocol.PROTOCOL_VERSION) {
                throw invalid();
            }
            r.object();
            return new RemoteCatalogSnapshot(r.server(), r.revision(), r.dimensions(), receivedAt);
        });
    }

    /** Decode nested client catalogs against one aggregate allocation budget. */
    public RemoteCatalogSnapshot decodeCatalog(byte[] encoded, Instant receivedAt, DecodingContext budget) {
        return read(encoded, limits.catalogBytes(), budget, r -> {
            if (r.i() != CrossServerProtocol.PROTOCOL_VERSION) throw invalid();
            r.object();
            return new RemoteCatalogSnapshot(r.server(), r.revision(), r.dimensions(), receivedAt);
        });
    }

    public static int typeId(ApplicationMessage message) {
        if (message instanceof RegisterServer) return 1;
        if (message instanceof RegisterResult) return 2;
        if (message instanceof Heartbeat) return 3;
        if (message instanceof CatalogMetadata) return 10;
        if (message instanceof CatalogSnapshot) return 11;
        if (message instanceof CatalogDelta) return 12;
        if (message instanceof CatalogInvalidate) return 13;
        if (message instanceof PrepareHandoff) return 20;
        if (message instanceof HandoffPrepared) return 21;
        if (message instanceof HandoffRejected) return 22;
        if (message instanceof ClaimHandoff) return 23;
        if (message instanceof HandoffClaimed) return 24;
        if (message instanceof CompleteHandoff) return 25;
        if (message instanceof CancelHandoff) return 26;
        if (message instanceof ApplicationMessage.Error) return 30;
        throw new IllegalArgumentException("Unknown application message");
    }

    private static void writeMessage(Writer w, ApplicationMessage message) {
        if (message instanceof RegisterServer m) {
            w.server(m.serverId());
            w.i(m.protocolVersion());
            w.capabilities(m.capabilities());
        }
        else if (message instanceof RegisterResult m) {
            w.server(m.serverId());
            w.result(m.result());
        }
        else if (message instanceof Heartbeat m) {
        }
        else if (message instanceof CatalogMetadata m) {
            w.server(m.serverId());
            w.string(m.displayName());
            w.revision(m.revision());
            w.i(1);
        }
        else if (message instanceof CatalogSnapshot m) {
            w.server(m.serverId());
            w.revision(m.revision());
            w.uuid(m.snapshotId());
            w.i(m.offset());
            w.total(m.totalBytes());
            w.bytes(m.data());
        }
        else if (message instanceof CatalogDelta m) {
            w.server(m.serverId());
            w.revision(m.baseRevision());
            w.revision(m.revision());
            w.dimensions(m.replacements());
            w.removals(m.removedLists());
            w.names(m.removedDimensions());
        }
        else if (message instanceof CatalogInvalidate m) {
            w.server(m.serverId());
            w.revision(m.revision());
            w.state(m.state());
        }
        else if (message instanceof PrepareHandoff m) {
            w.uuid(m.playerId());
            w.server(m.source());
            w.key(m.target());
            w.action(m.action());
            w.revision(m.observedCatalogRevision());
            w.revision(m.observedListRevision());
        }
        else if (message instanceof HandoffPrepared m) {
            w.binding(m.binding());
        }
        else if (message instanceof HandoffRejected m) {
            w.result(m.reason());
        }
        else if (message instanceof ClaimHandoff m) {
            w.uuid(m.handoffId());
            w.uuid(m.playerId());
            w.server(m.destination());
        }
        else if (message instanceof HandoffClaimed m) {
            w.binding(m.binding());
        }
        else if (message instanceof CompleteHandoff m) {
            w.uuid(m.handoffId());
            w.uuid(m.playerId());
            w.server(m.destination());
            w.result(m.result());
        }
        else if (message instanceof CancelHandoff m) {
            w.uuid(m.handoffId());
            w.result(m.reason());
        }
        else if (message instanceof ApplicationMessage.Error m) {
            w.result(m.reason());
        }
        else {
            throw invalid();
        }
    }

    private static ApplicationMessage readMessage(Reader r, int type) {
        return switch (type) {
            case 1 -> new RegisterServer(r.server(), r.i(), r.capabilities());
            case 2 -> new RegisterResult(r.server(), r.result());
            case 3 -> new Heartbeat();
            case 10 -> new CatalogMetadata(r.server(), r.string(), r.revision(), r.policy());
            case 11 -> new CatalogSnapshot(r.server(), r.revision(), r.uuid(), r.i(), r.total(), r.bytes());
            case 12 -> new CatalogDelta(r.server(), r.revision(), r.revision(), r.dimensions(), r.removals(), r.names());
            case 13 -> new CatalogInvalidate(r.server(), r.revision(), r.state());
            case 20 -> new PrepareHandoff(r.uuid(), r.server(), r.key(), r.action(), r.revision(), r.revision());
            case 21 -> new HandoffPrepared(r.binding());
            case 22 -> new HandoffRejected(r.result());
            case 23 -> new ClaimHandoff(r.uuid(), r.uuid(), r.server());
            case 24 -> new HandoffClaimed(r.binding());
            case 25 -> new CompleteHandoff(r.uuid(), r.uuid(), r.server(), r.result());
            case 26 -> new CancelHandoff(r.uuid(), r.result());
            case 30 -> new ApplicationMessage.Error(r.result());
            default -> throw invalid();
        };
    }

    private byte[] write(int maximum, java.util.function.Consumer<Writer> action) {
        ByteBuf buf = Unpooled.buffer(Math.min(256, maximum), maximum);
        try {
            action.accept(new Writer(buf));
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } catch (IndexOutOfBoundsException exception) {
            throw invalid();
        } finally {
            buf.release();
        }
    }

    private <T> T read(byte[] bytes, int maximum, java.util.function.Function<Reader, T> action) {
        return read(bytes, maximum, new DecodingContext(limits.allocationBytes(), limits.objects()), action);
    }

    private <T> T read(byte[] bytes, int maximum, DecodingContext budget, java.util.function.Function<Reader, T> action) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > maximum) throw invalid();
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            T result = action.apply(new Reader(buf, budget));
            if (buf.isReadable()) throw invalid();
            return result;
        } catch (IndexOutOfBoundsException exception) {
            throw invalid();
        } finally {
            buf.release();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or over-budget cross-server application data");
    }

    private static int resultId(Result result) {
        return switch (result) {
            case SUCCESS -> 0;
            case UNAVAILABLE -> 1;
            case UNAUTHORIZED -> 2;
            case NOT_FOUND -> 3;
            case STALE_CATALOG -> 4;
            case BUSY -> 5;
            case EXPIRED -> 6;
            case REPLAY -> 7;
            case WRONG_SOURCE -> 8;
            case WRONG_DESTINATION -> 9;
            case TRANSFER_FAILED -> 10;
            case CANCELLED -> 11;
            case UNSUPPORTED -> 12;
            case INVALID_REQUEST -> 13;
            case INTERNAL_ERROR -> 14;
        };
    }

    private static Result resultById(int id) {
        return switch (id) {
            case 0 -> Result.SUCCESS;
            case 1 -> Result.UNAVAILABLE;
            case 2 -> Result.UNAUTHORIZED;
            case 3 -> Result.NOT_FOUND;
            case 4 -> Result.STALE_CATALOG;
            case 5 -> Result.BUSY;
            case 6 -> Result.EXPIRED;
            case 7 -> Result.REPLAY;
            case 8 -> Result.WRONG_SOURCE;
            case 9 -> Result.WRONG_DESTINATION;
            case 10 -> Result.TRANSFER_FAILED;
            case 11 -> Result.CANCELLED;
            case 12 -> Result.UNSUPPORTED;
            case 13 -> Result.INVALID_REQUEST;
            case 14 -> Result.INTERNAL_ERROR;
            default -> throw invalid();
        };
    }

    private final class Writer {
        private final ByteBuf buf;
        private final DecodingContext budget = new DecodingContext(limits.allocationBytes(), limits.objects());

        private Writer(ByteBuf buf) { this.buf = buf; }
        private void object() { budget.claimObject(); }
        private void i(int value) { buf.writeInt(value); }
        private void l(long value) { buf.writeLong(value); }
        private void uuid(UUID value) { object(); l(value.getMostSignificantBits()); l(value.getLeastSignificantBits()); }
        private void server(RemoteServerId value) { object(); string(value.value()); }
        private void revision(RemoteRevision value) { object(); l(value.value()); }
        private void result(Result value) { i(resultId(value)); }
        private void action(Action value) { i(switch (value) { case TELEPORT -> 1; }); }
        private void state(RemoteCatalogState value) {
            i(switch (value) { case AVAILABLE -> 1; case STALE -> 2; case UNAVAILABLE -> 3; case UNAUTHORIZED -> 4; });
        }

        private void string(String value) {
            // Preflight exact UTF-8 length and reject unpaired surrogates before allocating encoded bytes.
            long length = 0;
            for (int n = 0; n < value.length(); n++) {
                char c = value.charAt(n);
                if (Character.isHighSurrogate(c)) {
                    if (++n >= value.length() || !Character.isLowSurrogate(value.charAt(n))) throw invalid();
                    length += 4;
                } else if (Character.isLowSurrogate(c)) {
                    throw invalid();
                } else {
                    length += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
                }
                if (length > limits.stringBytes()) throw invalid();
            }
            budget.claimObject();
            budget.claimBytes(Math.toIntExact(length * 6));
            i((int) length);
            buf.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        }

        private void count(int count) {
            if (count < 0 || count > limits.collectionEntries()) throw invalid();
            budget.claimObject();
            budget.claimBytes(Math.multiplyExact(count, 64));
            for (int n = 0; n < count; n++) budget.claimObject();
            i(count);
        }

        private <T> void map(Map<String, T> map, java.util.function.Consumer<T> valueWriter) {
            count(map.size());
            for (var entry : new TreeMap<>(map).entrySet()) {
                string(entry.getKey());
                valueWriter.accept(entry.getValue());
            }
        }

        private void names(Set<String> names) {
            count(names.size());
            for (String name : new TreeSet<>(names)) string(name);
        }

        private void capabilities(Set<Integer> values) {
            count(values.size());
            for (int value : new TreeSet<>(values)) i(value);
        }

        private void dimensions(Map<String, Map<String, RemoteListSnapshot>> dimensions) {
            map(dimensions, lists -> map(lists, this::list));
        }

        private void removals(Map<String, Set<String>> removals) { map(removals, this::names); }

        private void list(RemoteListSnapshot list) {
            object();
            string(list.displayName());
            revision(list.listRevision());
            map(list.waypoints(), this::waypoint);
        }

        private void waypoint(RemoteWaypointSnapshot waypoint) {
            object();
            string(waypoint.displayName());
            string(waypoint.initials());
            object();
            i(waypoint.position().x()); i(waypoint.position().y()); i(waypoint.position().z());
            i(waypoint.rgb()); i(waypoint.yaw());
            buf.writeByte(waypoint.global() ? 1 : 0);
            count(waypoint.keywords().size());
            for (String keyword : waypoint.keywords()) string(keyword);
            string(waypoint.description());
        }

        private void key(RemoteWaypointKey key) {
            object();
            server(key.serverId()); string(key.dimensionName()); string(key.listName()); string(key.waypointName());
        }

        private void binding(HandoffBinding binding) {
            object();
            uuid(binding.handoffId()); uuid(binding.playerId()); server(binding.source()); key(binding.target());
            action(binding.action()); l(binding.expiresAtEpochMillis());
        }

        private void total(int total) {
            if (total <= 0 || total > limits.catalogBytes()) throw invalid();
            i(total);
        }

        private void bytes(Bytes bytes) {
            if (bytes.size() > limits.chunkBytes()) throw invalid();
            budget.claimObject();
            budget.claimBytes(Math.multiplyExact(bytes.size(), 2));
            i(bytes.size());
            buf.writeBytes(bytes.copy());
        }
    }

    private final class Reader {
        private final ByteBuf buf;
        private final DecodingContext budget;
        private final DecodingContext catalogBudget = new DecodingContext(limits.allocationBytes(), limits.objects());

        private Reader(ByteBuf buf, DecodingContext budget) { this.buf = buf; this.budget = budget; }
        private void object() { budget.claimObject(); catalogBudget.claimObject(); }
        private void claimBytes(int count) { budget.claimBytes(count); catalogBudget.claimBytes(count); }
        private int i() { return buf.readInt(); }
        private long l() { return buf.readLong(); }
        private UUID uuid() { object(); return new UUID(l(), l()); }
        private RemoteServerId server() { object(); return new RemoteServerId(string()); }
        private RemoteRevision revision() { object(); return new RemoteRevision(l()); }
        private Result result() { return resultById(i()); }
        private Action action() { if (i() != 1) throw invalid(); return Action.TELEPORT; }
        private CatalogExportPolicy policy() { if (i() != 1) throw invalid(); return CatalogExportPolicy.PUBLIC; }
        private RemoteCatalogState state() {
            return switch (i()) {
                case 1 -> RemoteCatalogState.AVAILABLE;
                case 2 -> RemoteCatalogState.STALE;
                case 3 -> RemoteCatalogState.UNAVAILABLE;
                case 4 -> RemoteCatalogState.UNAUTHORIZED;
                default -> throw invalid();
            };
        }

        private String string() {
            int length = i();
            if (length < 0 || length > limits.stringBytes() || length > buf.readableBytes()) throw invalid();
            object();
            // UTF-8 bytes plus decoder storage, immutable string storage and defensive-copy headroom.
            claimBytes(Math.multiplyExact(length, 6));
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException exception) {
                throw invalid();
            }
        }

        private int count(int minimumBytes) {
            int count = i();
            if (count < 0 || count > limits.collectionEntries() || count > buf.readableBytes() / minimumBytes) throw invalid();
            object();
            claimBytes(Math.multiplyExact(count, 64));
            for (int n = 0; n < count; n++) object();
            return count;
        }

        private <T> Map<String, T> map(java.util.function.Supplier<T> reader) {
            int count = count(4);
            Map<String, T> result = new HashMap<>();
            String previous = null;
            for (int n = 0; n < count; n++) {
                String key = string();
                if (previous != null && previous.compareTo(key) >= 0) throw invalid();
                previous = key;
                result.put(key, reader.get());
            }
            return result;
        }

        private Set<String> names() {
            int count = count(4);
            Set<String> result = new HashSet<>();
            String previous = null;
            for (int n = 0; n < count; n++) {
                String name = string();
                if (previous != null && previous.compareTo(name) >= 0) throw invalid();
                previous = name;
                result.add(name);
            }
            return result;
        }

        private Set<Integer> capabilities() {
            int count = count(4);
            Set<Integer> result = new HashSet<>();
            int previous = 0;
            for (int n = 0; n < count; n++) {
                int value = i();
                if (value <= previous) throw invalid();
                previous = value;
                result.add(value);
            }
            return result;
        }

        private Map<String, Map<String, RemoteListSnapshot>> dimensions() { return map(() -> map(this::list)); }
        private Map<String, Set<String>> removals() { return map(this::names); }

        private RemoteListSnapshot list() {
            object();
            return new RemoteListSnapshot(string(), revision(), map(this::waypoint));
        }

        private RemoteWaypointSnapshot waypoint() {
            object();
            String display = string();
            String initials = string();
            object();
            WaypointPos position = new WaypointPos(i(), i(), i());
            int rgb = i();
            int yaw = i();
            int global = buf.readUnsignedByte();
            if (global > 1) throw invalid();
            int count = count(4);
            List<String> keywords = new ArrayList<>(Math.min(count, 64));
            for (int n = 0; n < count; n++) keywords.add(string());
            return new RemoteWaypointSnapshot(display, initials, position, rgb, yaw, global == 1, keywords, string());
        }

        private RemoteWaypointKey key() { object(); return new RemoteWaypointKey(server(), string(), string(), string()); }

        private HandoffBinding binding() {
            object();
            return new HandoffBinding(uuid(), uuid(), server(), key(), action(), l());
        }

        private int total() {
            int total = i();
            if (total <= 0 || total > limits.catalogBytes()) throw invalid();
            return total;
        }

        private Bytes bytes() {
            int length = i();
            if (length < 0 || length > limits.chunkBytes() || length > buf.readableBytes()) throw invalid();
            object();
            claimBytes(Math.multiplyExact(length, 2));
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            return new Bytes(bytes);
        }
    }
}
