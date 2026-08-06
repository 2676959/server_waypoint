package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointEditRequestBuffer;
import io.netty.buffer.ByteBuf;

public final class WaypointEditRequestBufferCodec {
    private WaypointEditRequestBufferCodec() {
    }

    public static void encode(ByteBuf buf, WaypointEditRequestBuffer request) {
        buf.writeLong(request.requestId());
        UtfStringCodec.encode(buf, request.dimensionName());
        UtfStringCodec.encode(buf, request.listIdentifier());
        UtfStringCodec.encode(buf, request.waypointIdentifier());
        buf.writeInt(request.expectedListRevision());
        WaypointPatchCodec.encode(buf, request.patch());
    }

    public static WaypointEditRequestBuffer decode(ByteBuf buf) {
        return new WaypointEditRequestBuffer(
                buf.readLong(),
                UtfStringCodec.decode(buf),
                UtfStringCodec.decode(buf),
                UtfStringCodec.decode(buf),
                buf.readInt(),
                WaypointPatchCodec.decode(buf)
        );
    }
}
