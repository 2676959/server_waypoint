package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.DimensionWaypointsListCodec;
import io.netty.buffer.ByteBuf;

import java.util.Collection;

import static _959.server_waypoint.core.network.MessageChannelID.WORLD_WAYPOINT_CHANNEL;

public class WorldWaypointBuffer extends DimensionWaypointsList {
    public WorldWaypointBuffer() {
        super();
    }

    public WorldWaypointBuffer(Collection<DimensionWaypointBuffer> collection) {
        super(collection);
    }

    @Override
    public MessageChannelID getChannelId() {
        return WORLD_WAYPOINT_CHANNEL;
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return DimensionWaypointsListCodec.decode(byteBuf, WorldWaypointBuffer::new);
    }
}
