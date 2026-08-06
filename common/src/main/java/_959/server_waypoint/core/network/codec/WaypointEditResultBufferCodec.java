package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.network.buffer.WaypointEditResultBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import io.netty.buffer.ByteBuf;

public final class WaypointEditResultBufferCodec {
    private WaypointEditResultBufferCodec() {
    }

    public static void encode(ByteBuf buf, WaypointEditResultBuffer result) {
        buf.writeLong(result.requestId());
        buf.writeByte(result.status().ordinal());
        UtfStringCodec.encode(buf, result.dimensionName());
        UtfStringCodec.encode(buf, result.listIdentifier());
        UtfStringCodec.encode(buf, result.previousWaypointIdentifier());
        buf.writeBoolean(result.waypoint() != null);
        if (result.waypoint() != null) {
            SimpleWaypointCodec.encode(buf, result.waypoint());
        }
        buf.writeInt(result.listRevision());
    }

    public static WaypointEditResultBuffer decode(ByteBuf buf) {
        long requestId = buf.readLong();
        int statusIndex = buf.readUnsignedByte();
        EditResultStatus[] statuses = EditResultStatus.values();
        if (statusIndex >= statuses.length) {
            throw new IllegalArgumentException("Unknown edit result status " + statusIndex);
        }
        String dimensionName = UtfStringCodec.decode(buf);
        String listIdentifier = UtfStringCodec.decode(buf);
        String previousWaypointIdentifier = UtfStringCodec.decode(buf);
        SimpleWaypoint waypoint = buf.readBoolean() ? SimpleWaypointCodec.decode(buf) : null;
        int listRevision = buf.readInt();
        return new WaypointEditResultBuffer(
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
