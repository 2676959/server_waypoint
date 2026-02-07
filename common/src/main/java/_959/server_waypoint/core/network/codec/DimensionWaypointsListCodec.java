package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.buffer.DimensionWaypointsList;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.function.Function;

public class DimensionWaypointsListCodec {
    public static void encode(ByteBuf buffer, List<DimensionWaypointBuffer> dimensionWaypointsList) {
        ListCodec.encode(buffer, dimensionWaypointsList, DimensionWaypointCodec::encode);
    }

    public static <T extends DimensionWaypointsList> T decode(ByteBuf buffer, Function<List<DimensionWaypointBuffer>, T> constructor) {
        return constructor.apply(ListCodec.decode(buffer, DimensionWaypointCodec::decode));
    }
}
