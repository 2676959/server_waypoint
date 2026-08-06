package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointListUpdateBuffer;
import io.netty.buffer.ByteBuf;

public final class WaypointListUpdateBufferCodec {
    private WaypointListUpdateBufferCodec() {
    }

    public static void encode(ByteBuf buf, WaypointListUpdateBuffer update) {
        UtfStringCodec.encode(buf, update.dimensionName());
        UtfStringCodec.encode(buf, update.previousListIdentifier());
        WaypointListCodec.encode(buf, update.waypointList());
    }

    public static WaypointListUpdateBuffer decode(ByteBuf buf) {
        return new WaypointListUpdateBuffer(
                UtfStringCodec.decode(buf),
                UtfStringCodec.decode(buf),
                WaypointListCodec.decode(buf)
        );
    }
}
