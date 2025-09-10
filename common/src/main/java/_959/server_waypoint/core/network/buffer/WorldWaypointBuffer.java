package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WorldWaypointBufferCodec;
import io.netty.buffer.ByteBuf;

import java.util.List;

import static _959.server_waypoint.core.network.MessageChannelID.WORLD_WAYPOINT_CHANNEL;

public record WorldWaypointBuffer(List<DimensionWaypointBuffer> dimensionWaypointBuffers, int edition) implements MessageBuffer {
    @Override
    public MessageChannelID getChannelId() {
        return WORLD_WAYPOINT_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WorldWaypointBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WorldWaypointBufferCodec.decode(byteBuf);
    }
}
