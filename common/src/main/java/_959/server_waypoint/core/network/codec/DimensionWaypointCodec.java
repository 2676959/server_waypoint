package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class DimensionWaypointCodec {
    public static void encode(ByteBuf buf, DimensionWaypoint dimensionWaypoint) {
        UtfStringCodec.encode(buf, dimensionWaypoint.dimString());
        ListCodec.encode(buf, dimensionWaypoint.waypointLists(), WaypointListCodec::encode);
    }

    public static DimensionWaypoint decode(ByteBuf buf) {
        String dimString = UtfStringCodec.decode(buf);
        List<WaypointList> waypointLists = ListCodec.decode(buf, WaypointListCodec::decode);
        return new DimensionWaypoint(dimString, waypointLists);
    }
}
