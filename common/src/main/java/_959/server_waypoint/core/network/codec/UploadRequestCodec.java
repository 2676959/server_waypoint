package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.upload.UploadTarget;
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
        buf.writeByte(request.target().ordinal());
    }

    public static UploadRequestBuffer decode(ByteBuf buf, DecodingContext context) {
        UUID requestId = new UUID(buf.readLong(), buf.readLong());
        List<String> dimensionNames = ListCodec.decode(buf, UtfStringCodec::decode, context);
        String listName = decodeOptionalString(buf, context);
        String waypointName = decodeOptionalString(buf, context);
        int targetId = buf.readUnsignedByte();
        UploadTarget[] targets = UploadTarget.values();
        if (targetId >= targets.length) {
            throw new IllegalArgumentException("Invalid upload target: " + targetId);
        }
        return new UploadRequestBuffer(
                requestId,
                dimensionNames,
                listName,
                waypointName,
                targets[targetId]
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
