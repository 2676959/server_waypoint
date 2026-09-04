package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class DimensionWaypointsListCodec {
    private DimensionWaypointsListCodec() {
    }

    public static void encode(
            ByteBuf buffer,
            List<DimensionWaypointData> dimensionWaypointsList,
            EncodingContext context
    ) {
        ListCodec.encode(buffer, dimensionWaypointsList, DimensionWaypointCodec::encode, context);
    }

    public static List<DimensionWaypointData> decode(ByteBuf buffer, DecodingContext context) {
        return ListCodec.decode(buffer, DimensionWaypointCodec::decode, context);
    }
}
