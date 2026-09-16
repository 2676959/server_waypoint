package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import io.netty.buffer.ByteBuf;

public final class WaypointModificationMessageCodec {
    private WaypointModificationMessageCodec() {
    }

    public static void encode(ByteBuf buf, WaypointModificationMessage modification, EncodingContext context) {
        WaypointModificationType type = modification.type();
        buf.writeByte(type.ordinal());
        UtfStringCodec.encode(buf, modification.dimensionName(), context);
        UtfStringCodec.encode(buf, modification.listName(), context);
        UtfStringCodec.encode(buf, modification.listDisplayName(), context);
        switch (type) {
            case ADD -> {
                // only needs a waypoint object
                SimpleWaypointCodec.encode(buf, modification.waypoint(), context);
            }
            case UPDATE -> {
                // needs a waypoint name and a waypoint object
                UtfStringCodec.encode(buf, modification.waypointName(), context);
                SimpleWaypointCodec.encode(buf, modification.waypoint(), context);
            }
            case REMOVE -> {
                // only needs a waypoint name
                UtfStringCodec.encode(buf, modification.waypointName(), context);
            }
            // already has enough information for actions on a waypoint list
            case ADD_LIST, REMOVE_LIST -> {}
        }
        buf.writeInt(modification.syncId());
    }

    public static WaypointModificationMessage decode(ByteBuf buf, DecodingContext context) {
        int typeId = buf.readUnsignedByte();
        WaypointModificationType[] types = WaypointModificationType.values();
        if (typeId >= types.length) {
            throw new IllegalArgumentException("Invalid waypoint modification type: " + typeId);
        }
        WaypointModificationType type = types[typeId];
        String dimensionName = UtfStringCodec.decode(buf, context);
        String listName = UtfStringCodec.decode(buf, context);
        String listDisplayName = UtfStringCodec.decode(buf, context);
        String waypointName = null;
        SimpleWaypoint waypoint = null;
        switch (type) {
            case ADD -> {
                // only needs a waypoint object
                waypoint = SimpleWaypointCodec.decode(buf, context);
            }
            case UPDATE -> {
                // needs a waypoint name and a waypoint object
                waypointName = UtfStringCodec.decode(buf, context);
                waypoint = SimpleWaypointCodec.decode(buf, context);
            }
            case REMOVE -> {
                // only needs a waypoint name
                waypointName = UtfStringCodec.decode(buf, context);
            }
            case ADD_LIST, REMOVE_LIST -> {
            }
        }
        int syncId = buf.readInt();
        return new WaypointModificationMessage(
                dimensionName,
                listName,
                listDisplayName,
                waypointName,
                waypoint,
                type,
                syncId
        );
    }
}
