package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class WaypointListCodec {
    private WaypointListCodec() {
    }

    public static void encode(ByteBuf buf, WaypointList waypointList, EncodingContext context) {
        WaypointList snapshot = waypointList.deepCopy();
        UtfStringCodec.encode(buf, snapshot.name(), context);
        UtfStringCodec.encode(buf, snapshot.displayName(), context);
        buf.writeInt(snapshot.getSyncNum());
        List<SimpleWaypoint> waypoints = snapshot.simpleWaypoints();
        ListCodec.encode(buf, waypoints, SimpleWaypointCodec::encode, context);
    }

    public static WaypointList decode(ByteBuf byteBuf, DecodingContext context) {
        String name = UtfStringCodec.decode(byteBuf, context);
        String displayName = UtfStringCodec.decode(byteBuf, context);
        int syncId = byteBuf.readInt();
        List<SimpleWaypoint> waypoints = ListCodec.decode(byteBuf, SimpleWaypointCodec::decode, context);
        return new WaypointList(name, displayName, syncId, waypoints);
    }
}
