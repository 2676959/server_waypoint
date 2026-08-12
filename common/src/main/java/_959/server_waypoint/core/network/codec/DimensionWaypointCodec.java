package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class DimensionWaypointCodec {
    private DimensionWaypointCodec() {
    }

    public static void encode(ByteBuf buf, DimensionWaypointData dimensionMessageChunk, EncodingContext context) {
        UtfStringCodec.encode(buf, dimensionMessageChunk.dimensionName(), context);
        ListCodec.encode(buf, dimensionMessageChunk.waypointLists(), WaypointListCodec::encode, context);
    }

    public static DimensionWaypointData decode(ByteBuf buf, DecodingContext context) {
        String dimensionName = UtfStringCodec.decode(buf, context);
        List<WaypointList> waypointLists = ListCodec.decode(buf, WaypointListCodec::decode, context);
        return new DimensionWaypointData(dimensionName, waypointLists);
    }
}
