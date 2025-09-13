package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

public class WaypointListBufferCodec {
    public static void encode(ByteBuf buf, WaypointListBuffer waypointListBuffer) {
        UtfStringCodec.encode(buf, waypointListBuffer.dimensionName());
        WaypointListCodec.encode(buf, waypointListBuffer.waypointList());
    }

    public static WaypointListBuffer decode(ByteBuf buf) {
        String dimString = UtfStringCodec.decode(buf);
        WaypointList waypointList = WaypointListCodec.decode(buf);
        return new WaypointListBuffer(dimString, waypointList);
    }
}
