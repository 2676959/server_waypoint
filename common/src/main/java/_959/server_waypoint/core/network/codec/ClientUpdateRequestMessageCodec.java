package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.message.ClientUpdateRequestMessage;
import io.netty.buffer.ByteBuf;

public final class ClientUpdateRequestMessageCodec {
    private ClientUpdateRequestMessageCodec() {
    }

    public static void encode(ByteBuf buf, ClientUpdateRequestMessage message, EncodingContext context) {
        ListCodec.encode(buf, message.dimensionSyncIds(), DimensionSyncIdentifier::encode, context);
    }

    public static ClientUpdateRequestMessage decode(ByteBuf buf, DecodingContext context) {
        return new ClientUpdateRequestMessage(
                ListCodec.decode(buf, DimensionSyncIdentifier::decode, context)
        );
    }
}
