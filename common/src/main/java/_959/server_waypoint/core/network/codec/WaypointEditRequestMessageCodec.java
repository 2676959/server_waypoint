package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import io.netty.buffer.ByteBuf;

public final class WaypointEditRequestMessageCodec {
    private WaypointEditRequestMessageCodec() {
    }

    public static void encode(ByteBuf buf, WaypointEditRequestMessage request, EncodingContext context) {
        buf.writeLong(request.requestId());
        UtfStringCodec.encode(buf, request.dimensionName(), context);
        UtfStringCodec.encode(buf, request.listIdentifier(), context);
        UtfStringCodec.encode(buf, request.waypointIdentifier(), context);
        buf.writeInt(request.expectedListRevision());
        WaypointPatchCodec.encode(buf, request.patch(), context);
    }

    public static WaypointEditRequestMessage decode(ByteBuf buf, DecodingContext context) {
        return new WaypointEditRequestMessage(
                buf.readLong(),
                UtfStringCodec.decode(buf, context),
                UtfStringCodec.decode(buf, context),
                UtfStringCodec.decode(buf, context),
                buf.readInt(),
                WaypointPatchCodec.decode(buf, context)
        );
    }
}
