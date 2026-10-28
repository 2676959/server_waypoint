package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.collection.WaypointScope;
import io.netty.buffer.ByteBuf;

/** Protocol 3 uses only backend scopes; no owner identity is accepted from the wire. */
public final class WaypointScopeCodec {
    private WaypointScopeCodec() { }

    public static void encode(ByteBuf buffer, WaypointScope scope) {
        buffer.writeByte(scope.ordinal());
    }

    public static WaypointScope decode(ByteBuf buffer) {
        int id = buffer.readUnsignedByte();
        if (id >= WaypointScope.values().length) throw new IllegalArgumentException("Invalid waypoint scope: " + id);
        return WaypointScope.values()[id];
    }
}
