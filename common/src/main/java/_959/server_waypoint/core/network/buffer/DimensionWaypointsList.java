package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.codec.DimensionWaypointsListCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collection;

public abstract class DimensionWaypointsList extends ArrayList<DimensionWaypointBuffer> implements MessageBuffer {
    public DimensionWaypointsList() {
        super();
    }

    public DimensionWaypointsList(Collection<DimensionWaypointBuffer> collection) {
        super(collection);
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        DimensionWaypointsListCodec.encode(byteBuf, this);
    }
}
