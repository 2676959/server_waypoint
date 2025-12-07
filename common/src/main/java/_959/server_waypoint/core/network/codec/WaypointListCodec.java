package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class WaypointListCodec {
    public static void encode(ByteBuf buf, WaypointList waypointList) {
        UtfStringCodec.encode(buf, waypointList.name());
        buf.writeInt(waypointList.getSyncNum());
        List<SimpleWaypoint> waypoints = waypointList.simpleWaypoints();
        ListCodec.encode(buf, waypoints, SimpleWaypointCodec::encode);
    }

    public static WaypointList decode(ByteBuf byteBuf) {
        String name = UtfStringCodec.decode(byteBuf);
        int syncId = byteBuf.readInt();
        List<SimpleWaypoint> waypoints = ListCodec.decode(byteBuf, SimpleWaypointCodec::decode);
        return new WaypointList(name, syncId, waypoints);
    }
}
