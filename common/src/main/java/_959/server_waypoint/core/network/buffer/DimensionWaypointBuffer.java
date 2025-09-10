package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.DimensionWaypointCodec;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.List;

import static _959.server_waypoint.core.network.MessageChannelID.DIMENSION_WAYPOINT_CHANNEL;

public record DimensionWaypointBuffer(String dimensionName, List<WaypointList> waypointLists) implements MessageBuffer {

    @Override
    public MessageChannelID getChannelId() {
        return DIMENSION_WAYPOINT_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        DimensionWaypointCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return DimensionWaypointCodec.decode(byteBuf);
    }
}
