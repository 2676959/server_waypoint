package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import io.netty.buffer.ByteBuf;

public final class WaypointEditResultMessageCodec {
    private WaypointEditResultMessageCodec() {
    }

    public static void encode(ByteBuf buf, WaypointEditResultMessage result, EncodingContext context) {
        buf.writeLong(result.requestId());
        buf.writeByte(result.status().ordinal());
        UtfStringCodec.encode(buf, result.dimensionName(), context);
        UtfStringCodec.encode(buf, result.listIdentifier(), context);
        UtfStringCodec.encode(buf, result.previousWaypointIdentifier(), context);
        buf.writeBoolean(result.waypoint() != null);
        if (result.waypoint() != null) {
            SimpleWaypointCodec.encode(buf, result.waypoint(), context);
        }
        buf.writeInt(result.listRevision());
    }

    public static WaypointEditResultMessage decode(ByteBuf buf, DecodingContext context) {
        long requestId = buf.readLong();
        int statusIndex = buf.readUnsignedByte();
        EditResultStatus[] statuses = EditResultStatus.values();
        if (statusIndex >= statuses.length) {
            throw new IllegalArgumentException("Unknown edit result status " + statusIndex);
        }
        String dimensionName = UtfStringCodec.decode(buf, context);
        String listIdentifier = UtfStringCodec.decode(buf, context);
        String previousWaypointIdentifier = UtfStringCodec.decode(buf, context);
        SimpleWaypoint waypoint = buf.readBoolean() ? SimpleWaypointCodec.decode(buf, context) : null;
        int listRevision = buf.readInt();
        return new WaypointEditResultMessage(
                requestId,
                statuses[statusIndex],
                dimensionName,
                listIdentifier,
                previousWaypointIdentifier,
                waypoint,
                listRevision
        );
    }
}
