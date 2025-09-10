package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class WorldWaypointBufferCodec {
    public static void encode(ByteBuf buffer, WorldWaypointBuffer worldWaypointBuffer) {
        ListCodec.encode(buffer, worldWaypointBuffer.dimensionWaypointBuffers(), DimensionWaypointCodec::encode);
        buffer.writeInt(worldWaypointBuffer.edition());
    }

    public static WorldWaypointBuffer decode(ByteBuf buffer) {
        List<DimensionWaypointBuffer> dimensionWaypointBuffers = ListCodec.decode(buffer, DimensionWaypointCodec::decode);
        int edition = buffer.readInt();
        return new WorldWaypointBuffer(dimensionWaypointBuffers, edition);
    }
}
