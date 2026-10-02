package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.codec.ClientUpdateRequestMessageCodec;
import _959.server_waypoint.core.network.codec.MessageCodec;
import _959.server_waypoint.core.network.codec.WaypointDataCodec;
import _959.server_waypoint.core.network.codec.WaypointEditRequestMessageCodec;
import _959.server_waypoint.core.network.codec.WaypointEditResultMessageCodec;
import _959.server_waypoint.core.network.codec.WaypointListUpdateMessageCodec;
import _959.server_waypoint.core.network.codec.WaypointModificationMessageCodec;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.ClientUpdateRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.network.message.WaypointListUpdateMessage;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import io.netty.buffer.ByteBuf;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.core.network.message.RemoteCatalogRequestMessage;
import _959.server_waypoint.core.network.codec.RemoteCatalogMessageCodec;

import java.util.Map;

/** Stable logical-message ID table shared by every platform implementation. */
public final class ChunkedMessageRegistry {
    public static final ChunkedMessageType<WaypointData> WAYPOINT_DATA = type(
            0,
            WaypointDataCodec::encode,
            WaypointDataCodec::decode
    );
    public static final ChunkedMessageType<ClientUpdateRequestMessage> CLIENT_UPDATE_REQUEST = type(
            1,
            ClientUpdateRequestMessageCodec::encode,
            ClientUpdateRequestMessageCodec::decode
    );
    public static final ChunkedMessageType<WaypointEditRequestMessage> WAYPOINT_EDIT_REQUEST = type(
            2,
            WaypointEditRequestMessageCodec::encode,
            WaypointEditRequestMessageCodec::decode
    );
    public static final ChunkedMessageType<WaypointEditResultMessage> WAYPOINT_EDIT_RESULT = type(
            3,
            WaypointEditResultMessageCodec::encode,
            WaypointEditResultMessageCodec::decode
    );
    public static final ChunkedMessageType<WaypointModificationMessage> WAYPOINT_MODIFICATION = type(
            4,
            WaypointModificationMessageCodec::encode,
            WaypointModificationMessageCodec::decode
    );
    public static final ChunkedMessageType<WaypointListUpdateMessage> WAYPOINT_LIST_UPDATE = type(
            5,
            WaypointListUpdateMessageCodec::encode,
            WaypointListUpdateMessageCodec::decode
    );

    public static final ChunkedMessageType<RemoteCatalogMessage> REMOTE_CATALOG = type(
            6, RemoteCatalogMessageCodec::encode, RemoteCatalogMessageCodec::decode);
    public static final ChunkedMessageType<RemoteCatalogRequestMessage> REMOTE_CATALOG_REQUEST = type(
            7, (buffer, message, context) -> {
                buffer.writeLong(message.requestId().getMostSignificantBits());
                buffer.writeLong(message.requestId().getLeastSignificantBits());
            }, (buffer, context) -> new RemoteCatalogRequestMessage(new java.util.UUID(buffer.readLong(), buffer.readLong())));

    private static final Map<Integer, ChunkedMessageType<?>> TYPES_BY_ID = Map.of(
            WAYPOINT_DATA.id(), WAYPOINT_DATA,
            CLIENT_UPDATE_REQUEST.id(), CLIENT_UPDATE_REQUEST,
            WAYPOINT_EDIT_REQUEST.id(), WAYPOINT_EDIT_REQUEST,
            WAYPOINT_EDIT_RESULT.id(), WAYPOINT_EDIT_RESULT,
            WAYPOINT_MODIFICATION.id(), WAYPOINT_MODIFICATION,
            WAYPOINT_LIST_UPDATE.id(), WAYPOINT_LIST_UPDATE,
            REMOTE_CATALOG.id(), REMOTE_CATALOG,
            REMOTE_CATALOG_REQUEST.id(), REMOTE_CATALOG_REQUEST
    );

    private ChunkedMessageRegistry() {
    }

    public static ChunkedMessageType<?> get(int id) {
        ChunkedMessageType<?> type = TYPES_BY_ID.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown chunked-message type ID: " + id);
        }
        return type;
    }

    public static void encode(
            ByteBuf buffer,
            ChunkedMessage message,
            EncodingContext context
    ) {
        encodeTyped(buffer, message, message.getType(), context);
    }

    public static ChunkedMessage decode(
            int typeId,
            ByteBuf buffer,
            DecodingContext context
    ) {
        return decodeTyped(get(typeId), buffer, context);
    }

    private static <T extends ChunkedMessage> ChunkedMessageType<T> type(
            int id,
            MessageCodec.Encoder<T> encoder,
            MessageCodec.Decoder<T> decoder
    ) {
        return new ChunkedMessageType<>(id, MessageCodec.of(encoder, decoder));
    }

    @SuppressWarnings("unchecked")
    private static <T extends ChunkedMessage> void encodeTyped(
            ByteBuf buffer,
            ChunkedMessage message,
            ChunkedMessageType<T> type,
            EncodingContext context
    ) {
        type.codec().encode(buffer, (T) message, context);
    }

    private static <T extends ChunkedMessage> T decodeTyped(
            ChunkedMessageType<T> type,
            ByteBuf buffer,
            DecodingContext context
    ) {
        return type.codec().decode(buffer, context);
    }
}
