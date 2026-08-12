package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UploadRequestCodec {
    private UploadRequestCodec() {
    }

    public static void encode(ByteBuf buf, UploadRequestBuffer request) {
        buf.writeLong(request.requestId().getMostSignificantBits());
        buf.writeLong(request.requestId().getLeastSignificantBits());
        if (request.dimensionNames().size() > UploadCodecSupport.MAX_DIMENSIONS) {
            throw new IllegalArgumentException("Too many dimensions in upload request");
        }
        buf.writeShort(request.dimensionNames().size());
        for (String dimensionName : request.dimensionNames()) {
            UploadCodecSupport.encodeString(buf, dimensionName);
        }
        UploadCodecSupport.encodeOptionalString(buf, request.listName());
        UploadCodecSupport.encodeOptionalString(buf, request.waypointName());
    }

    public static UploadRequestBuffer decode(ByteBuf buf) {
        UUID requestId = new UUID(buf.readLong(), buf.readLong());
        int dimensionCount = buf.readUnsignedShort();
        if (dimensionCount > UploadCodecSupport.MAX_DIMENSIONS) {
            throw new IllegalArgumentException("Too many dimensions in upload request: " + dimensionCount);
        }
        List<String> dimensionNames = new ArrayList<>(dimensionCount);
        for (int i = 0; i < dimensionCount; i++) {
            dimensionNames.add(UploadCodecSupport.decodeString(buf));
        }
        return new UploadRequestBuffer(
                requestId,
                dimensionNames,
                UploadCodecSupport.decodeOptionalString(buf),
                UploadCodecSupport.decodeOptionalString(buf)
        );
    }
}
