package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class DimensionWaypointCodec {
    public static void encode(ByteBuf buf, DimensionWaypointBuffer dimensionWaypointBuffer) {
        UtfStringCodec.encode(buf, dimensionWaypointBuffer.dimensionName());
        ListCodec.encode(buf, dimensionWaypointBuffer.waypointLists(), WaypointListCodec::encode);
    }

    public static DimensionWaypointBuffer decode(ByteBuf buf) {
        String dimensionName = UtfStringCodec.decode(buf);
        List<WaypointList> waypointLists = ListCodec.decode(buf, WaypointListCodec::decode);
        return new DimensionWaypointBuffer(dimensionName, waypointLists);
    }
}
