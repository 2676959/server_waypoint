package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.message.ClientUpdateRequestMessage;
import io.netty.buffer.ByteBuf;
import _959.server_waypoint.core.collection.WaypointScope;

public final class ClientUpdateRequestMessageCodec {
    private ClientUpdateRequestMessageCodec() {
    }

    public static void encode(ByteBuf buf, ClientUpdateRequestMessage message, EncodingContext context) {
        WaypointScopeCodec.encode(buf, message.scope());
        ListCodec.encode(buf, message.dimensionSyncIds(), DimensionSyncIdentifier::encode, context);
    }

    public static ClientUpdateRequestMessage decode(ByteBuf buf, DecodingContext context) {
        WaypointScope scope = WaypointScopeCodec.decode(buf);
        return new ClientUpdateRequestMessage(
                scope,
                ListCodec.decode(buf, DimensionSyncIdentifier::decode, context)
        );
    }
}
