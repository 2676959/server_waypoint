package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.buffer.ClientUpdateRequestBuffer;
import io.netty.buffer.ByteBuf;

public class ClientUpdateRequestBufferCodec {
    public static void encode(ByteBuf buf, ClientUpdateRequestBuffer clientHandshakeBuffer) {
        ListCodec.encode(buf, clientHandshakeBuffer.dimensionSyncIds(), DimensionSyncIdentifier::encode);
    }

    public static ClientUpdateRequestBuffer decode(ByteBuf buf) {
        return new ClientUpdateRequestBuffer(ListCodec.decode(buf, DimensionSyncIdentifier::decode));
    }
}
