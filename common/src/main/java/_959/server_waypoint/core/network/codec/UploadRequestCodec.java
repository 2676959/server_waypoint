package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.UUID;

public final class UploadRequestCodec {
    private UploadRequestCodec() {
    }

    public static void encode(ByteBuf buf, UploadRequestBuffer request, EncodingContext context) {
        buf.writeLong(request.requestId().getMostSignificantBits());
        buf.writeLong(request.requestId().getLeastSignificantBits());
        ListCodec.encode(buf, request.dimensionNames(), UtfStringCodec::encode, context);
        encodeOptionalString(buf, request.listName(), context);
        encodeOptionalString(buf, request.waypointName(), context);
    }

    public static UploadRequestBuffer decode(ByteBuf buf, DecodingContext context) {
        UUID requestId = new UUID(buf.readLong(), buf.readLong());
        List<String> dimensionNames = ListCodec.decode(buf, UtfStringCodec::decode, context);
        return new UploadRequestBuffer(
                requestId,
                dimensionNames,
                decodeOptionalString(buf, context),
                decodeOptionalString(buf, context)
        );
    }

    private static void encodeOptionalString(ByteBuf buf, String value, EncodingContext context) {
        buf.writeBoolean(value != null);
        if (value != null) {
            UtfStringCodec.encode(buf, value, context);
        }
    }

    private static String decodeOptionalString(ByteBuf buf, DecodingContext context) {
        return buf.readBoolean() ? UtfStringCodec.decode(buf, context) : null;
    }
}
