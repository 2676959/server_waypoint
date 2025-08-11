package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class WorldWaypointBufferCodec {
    public static void encode(ByteBuf buffer, WorldWaypointBuffer worldWaypointBuffer) {
        ListCodec.encode(buffer, worldWaypointBuffer.dimensionWaypoints(), DimensionWaypointCodec::encode);
        buffer.writeInt(worldWaypointBuffer.edition());
    }

    public static WorldWaypointBuffer decode(ByteBuf buffer) {
        List<DimensionWaypoint> dimensionWaypoints = ListCodec.decode(buffer, DimensionWaypointCodec::decode);
        int edition = buffer.readInt();
        return new WorldWaypointBuffer(dimensionWaypoints, edition);
    }
}
