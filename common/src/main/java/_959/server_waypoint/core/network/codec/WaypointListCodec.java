package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointSyncMode;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class WaypointListCodec {
    public static void encode(ByteBuf buf, WaypointList waypointList) {
        UtfStringCodec.encode(buf, waypointList.name());
        buf.writeInt(waypointList.getSyncNum());
        buf.writeByte(waypointList.getSyncMode().ordinal());
        List<SimpleWaypoint> waypoints = waypointList.simpleWaypoints();
        ListCodec.encode(buf, waypoints, SimpleWaypointCodec::encode);
    }

    public static WaypointList decode(ByteBuf byteBuf) {
        String name = UtfStringCodec.decode(byteBuf);
        int syncId = byteBuf.readInt();
        WaypointSyncMode syncMode = WaypointSyncMode.values()[byteBuf.readByte()];
        List<SimpleWaypoint> waypoints = ListCodec.decode(byteBuf, SimpleWaypointCodec::decode);
        return new WaypointList(name, syncId, syncMode, waypoints);
    }
}
