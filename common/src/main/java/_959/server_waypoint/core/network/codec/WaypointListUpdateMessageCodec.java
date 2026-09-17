package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.message.WaypointListUpdateMessage;
import io.netty.buffer.ByteBuf;

public final class WaypointListUpdateMessageCodec {
    private WaypointListUpdateMessageCodec() {
    }

    public static void encode(ByteBuf buf, WaypointListUpdateMessage update, EncodingContext context) {
        UtfStringCodec.encode(buf, update.dimensionName(), context);
        UtfStringCodec.encode(buf, update.previousListIdentifier(), context);
        WaypointListCodec.encode(buf, update.waypointList(), context);
    }

    public static WaypointListUpdateMessage decode(ByteBuf buf, DecodingContext context) {
        return new WaypointListUpdateMessage(
                UtfStringCodec.decode(buf, context),
                UtfStringCodec.decode(buf, context),
                WaypointListCodec.decode(buf, context)
        );
    }
}
