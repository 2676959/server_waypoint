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
            case ADD -> {
                // only needs a waypoint object
                SimpleWaypointCodec.encode(buf, modification.waypoint());
                buf.writeInt(modification.syncId());
            }
            case UPDATE -> {
                // needs a waypoint name and a waypoint object
                UtfStringCodec.encode(buf, modification.waypointName());
                SimpleWaypointCodec.encode(buf, modification.waypoint());
                buf.writeInt(modification.syncId());
            }
            case REMOVE -> {
                // only needs a waypoint name
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
        // already has enough information for actions on a waypoint list
        if (type == WaypointModificationType.ADD_LIST || type == WaypointModificationType.REMOVE_LIST) return new WaypointModificationBuffer(dimensionName, listName, null, null, type, 0);
        String waypointName = null;
        SimpleWaypoint waypoint = null;
        int syncId = 0;
        switch (type) {
            case ADD -> {
                // only needs a waypoint object
                waypoint = SimpleWaypointCodec.decode(buf);
                syncId = buf.readInt();
            }
            case UPDATE -> {
                // needs a waypoint name and a waypoint object
                waypointName = UtfStringCodec.decode(buf);
                waypoint = SimpleWaypointCodec.decode(buf);
                syncId = buf.readInt();
            }
            case REMOVE -> {
                // only needs a waypoint name
                waypointName = UtfStringCodec.decode(buf);
                syncId = buf.readInt();
            }
        }
        return new WaypointModificationBuffer(dimensionName, listName, waypointName, waypoint, type, syncId);
    }
}
