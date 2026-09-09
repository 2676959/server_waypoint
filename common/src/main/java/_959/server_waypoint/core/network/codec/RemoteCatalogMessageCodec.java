package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.*;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.transport.TransportMode;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import _959.server_waypoint.crossserver.protocol.*;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import java.util.*;

/** Bounded outer message; remote catalog bytes retain their canonical v1 representation. */
public final class RemoteCatalogMessageCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final ApplicationCodec CATALOG = new ApplicationCodec(ProtocolLimits.DEFAULT);
    private RemoteCatalogMessageCodec() { }
    public static void encode(ByteBuf out, RemoteCatalogMessage message, EncodingContext context) {
        int start = out.writerIndex();
        out.writeLong(message.requestId().getMostSignificantBits()); out.writeLong(message.requestId().getLeastSignificantBits());
        out.writeInt(stateId(message.state())); out.writeInt(message.servers().size());
        for (var entry : message.servers().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value))).toList()) {
            var view = entry.getValue();
            UtfStringCodec.encode(out, entry.getKey().value(), context);
            if (view.displayName().length() > 65536) throw new IllegalArgumentException("Display name too long");
            UtfStringCodec.encode(out, view.displayName(), context);
            out.writeInt(stateId(view.state()));
            out.writeInt(view.mode() == null ? 0 : view.mode() == TransportMode.NOISE_KK ? 1 : 2);
            byte[] data = view.snapshot() == null ? new byte[0] : CATALOG.encodeCatalog(view.snapshot());
            if ((long) out.writerIndex() - start + 4 + data.length > MAX_BYTES) throw new IllegalArgumentException("Remote cache too large");
            context.claimBytes(data.length); out.writeInt(data.length); out.writeBytes(data);
        }
    }
    public static RemoteCatalogMessage decode(ByteBuf in, DecodingContext context) {
        if (in.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Remote cache too large");
        UUID request = new UUID(in.readLong(), in.readLong());
        RemoteCatalogState state = state(in.readInt());
        int count = in.readInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Too many servers");
        Map<RemoteServerId, CatalogReceiver.View> servers = new HashMap<>();
        for (int i = 0; i < count; i++) {
            context.claimObject(); context.claimBytes(128);
            RemoteServerId id = new RemoteServerId(text(in, context, 64));
            String display = text(in, context, 262144);
            RemoteCatalogState status = state(in.readInt());
            TransportMode mode = switch (in.readInt()) {
                case 0 -> null; case 1 -> TransportMode.NOISE_KK; case 2 -> TransportMode.PLAINTEXT;
                default -> throw new IllegalArgumentException("Unknown transport mode");
            };
            int length = in.readInt();
            if (length < 0 || length > ProtocolLimits.DEFAULT.catalogBytes() || length > in.readableBytes()) throw new IllegalArgumentException("Invalid catalog length");
            context.claimBytes(length);
            byte[] bytes = new byte[length]; in.readBytes(bytes);
            var snapshot = length == 0 ? null : CATALOG.decodeCatalog(bytes, Instant.now(), context);
            if (servers.put(id, new CatalogReceiver.View(snapshot, status, display, mode)) != null) throw new IllegalArgumentException("Duplicate server");
        }
        return new RemoteCatalogMessage(request, state, servers);
    }
    private static String text(ByteBuf in, DecodingContext context, int maximum) {
        int length = in.getInt(in.readerIndex());
        if (length < 0 || length > maximum) throw new IllegalArgumentException("Invalid text length");
        context.claimBytes(Math.multiplyExact(length, 2));
        return UtfStringCodec.decode(in, context);
    }
    private static int stateId(RemoteCatalogState state) {
        return switch (state) { case AVAILABLE -> 1; case STALE -> 2; case UNAVAILABLE -> 3; case UNAUTHORIZED -> 4; };
    }
    private static RemoteCatalogState state(int id) {
        return switch (id) { case 1 -> RemoteCatalogState.AVAILABLE; case 2 -> RemoteCatalogState.STALE;
            case 3 -> RemoteCatalogState.UNAVAILABLE; case 4 -> RemoteCatalogState.UNAUTHORIZED;
            default -> throw new IllegalArgumentException("Unknown catalog state"); };
    }
}
