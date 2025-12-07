package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.core.network.MessageChannelID.WAYPOINT_MODIFICATION_CHANNEL;

public record WaypointModificationBuffer(
        String dimensionName,
        String listName,
        SimpleWaypoint waypoint,
        WaypointModificationType type,
        int syncId) implements MessageBuffer {

    @Override
    public MessageChannelID getChannelId() {
        return WAYPOINT_MODIFICATION_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WaypointModificationBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WaypointModificationBufferCodec.decode(byteBuf);
    }
}