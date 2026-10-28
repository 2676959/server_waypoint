package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.message.WaypointCollectionStateMessage;
import io.netty.buffer.ByteBuf;

public final class WaypointCollectionStateMessageCodec {
    private WaypointCollectionStateMessageCodec() { }

    public static void encode(ByteBuf buffer, WaypointCollectionStateMessage message, EncodingContext context) {
        WaypointScopeCodec.encode(buffer, message.scope());
        buffer.writeBoolean(message.readable());
    }

    public static WaypointCollectionStateMessage decode(ByteBuf buffer, DecodingContext context) {
        return new WaypointCollectionStateMessage(WaypointScopeCodec.decode(buffer), buffer.readBoolean());
    }
}
