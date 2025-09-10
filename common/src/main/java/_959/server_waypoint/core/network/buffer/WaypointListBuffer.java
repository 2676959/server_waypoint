package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WaypointListBufferCodec;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.core.network.MessageChannelID.WAYPOINT_LIST_CHANNEL;

public record WaypointListBuffer(String dimString, WaypointList waypointList) implements MessageBuffer {
    @Override
    public MessageChannelID getChannelId() {
        return WAYPOINT_LIST_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WaypointListBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WaypointListBufferCodec.decode(byteBuf);
    }
}
