package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import io.netty.buffer.ByteBuf;

public class WaypointModificationBufferCodec {
    public static void encode(ByteBuf buf, WaypointModificationBuffer modification) {
        WaypointModificationType type = modification.type();
        buf.writeByte(type.ordinal());
        UtfStringCodec.encode(buf, modification.dimensionName());
        UtfStringCodec.encode(buf, modification.listName());
        switch (type) {
            case ADD, UPDATE -> {
                SimpleWaypoint waypoint = modification.waypoint();
                // this should never be null, check just in case
                if (waypoint == null) {
                    waypoint = new SimpleWaypoint("NPE fallback", "NPE", 0, 0, 0, 0, 0, false);
                }
                SimpleWaypointCodec.encode(buf, waypoint);
                buf.writeInt(modification.syncId());
            }
            case REMOVE -> {
                // removing a waypoint only needs its name
                UtfStringCodec.encode(buf, modification.waypointName());
                buf.writeInt(modification.syncId());
            }
            // already has enough information for actions on a waypoint list
            case ADD_LIST, REMOVE_LIST -> {}
        }
    }

    public static WaypointModificationBuffer decode(ByteBuf buf) {
        WaypointModificationType type = WaypointModificationType.values()[buf.readByte()];
        String dimensionName = UtfStringCodec.decode(buf);
        String listName = UtfStringCodec.decode(buf);
        Object waypointOrName = null;
        int syncId = 0;
        switch (type) {
            case ADD, UPDATE -> {
                waypointOrName = SimpleWaypointCodec.decode(buf);
                syncId = buf.readInt();
            }
            case REMOVE -> {
                waypointOrName = UtfStringCodec.decode(buf);
                syncId = buf.readInt();
            }
            case ADD_LIST, REMOVE_LIST -> waypointOrName = "";
        }
        return new WaypointModificationBuffer(dimensionName, listName, waypointOrName, type, syncId);
    }
}
