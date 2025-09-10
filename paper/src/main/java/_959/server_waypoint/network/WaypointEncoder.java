package _959.server_waypoint.network;

import _959.server_waypoint.core.network.buffer.HandshakeBuffer;
import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.codec.*;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.function.BiConsumer;

public class WaypointEncoder {
    public static byte[] waypointListBuffer(WaypointListBuffer waypointListBuffer) {
        return encoderWarper(waypointListBuffer, WaypointListBufferCodec::encode);
    }

    public static byte[] dimensionWaypoint(DimensionWaypointBuffer dimensionWaypointBuffer) {
        return encoderWarper(dimensionWaypointBuffer, DimensionWaypointCodec::encode);
    }

    public static byte[] worldWaypointBuffer(WorldWaypointBuffer worldWaypoint) {
        return encoderWarper(worldWaypoint, WorldWaypointBufferCodec::encode);
    }

    public static byte[] waypointModificationBuffer(WaypointModificationBuffer waypointModification) {
        return encoderWarper(waypointModification, WaypointModificationBufferCodec::encode);
    }

    public static byte[] handshakeBuffer(HandshakeBuffer handshakeBuffer) {
        return encoderWarper(handshakeBuffer, HandshakeBufferCodec::encode);
    }

    private static <T> byte[] encoderWarper(T waypoint, BiConsumer<ByteBuf, T> encoder) {
        ByteBuf buf =  Unpooled.buffer();
        encoder.accept(buf, waypoint);
        buf.capacity(buf.writerIndex());
        return buf.array();
    }
}
