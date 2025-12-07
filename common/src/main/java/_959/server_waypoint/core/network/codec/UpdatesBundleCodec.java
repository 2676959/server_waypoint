package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.UpdatesBundleBuffer;
import io.netty.buffer.ByteBuf;

public class UpdatesBundleCodec {
    public static void encode(ByteBuf buf, UpdatesBundleBuffer updatesBundleBuffer) {
        buf.writeInt(updatesBundleBuffer.getServerId());
        DimensionWaypointsListCodec.encode(buf, updatesBundleBuffer);
    }

    public static UpdatesBundleBuffer decode(ByteBuf buf) {
        int serverId = buf.readInt();
        return DimensionWaypointsListCodec.decode(buf, bufferList -> new UpdatesBundleBuffer(bufferList, serverId));
    }
}