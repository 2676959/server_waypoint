package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import io.netty.buffer.ByteBuf;

public class WaypointModificationBufferCodec {
    public static void encode(ByteBuf buf, WaypointModificationBuffer modification) {
        UtfStringCodec.encode(buf, modification.dimensionName());
        UtfStringCodec.encode(buf, modification.listName());
        SimpleWaypointCodec.encode(buf, modification.waypoint());
        buf.writeByte(modification.type().ordinal());
        buf.writeInt(modification.syncId());
    }

    public static WaypointModificationBuffer decode(ByteBuf buf) {
        String dimString = UtfStringCodec.decode(buf);
        String listName = UtfStringCodec.decode(buf);
        SimpleWaypoint waypoint = SimpleWaypointCodec.decode(buf);
        WaypointModificationType type = WaypointModificationType.values()[buf.readByte()];
        int edition = buf.readInt();
        return new WaypointModificationBuffer(dimString, listName, waypoint, type, edition);
    }
}
