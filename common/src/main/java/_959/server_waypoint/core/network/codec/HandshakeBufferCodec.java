package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.HandshakeBuffer;
import io.netty.buffer.ByteBuf;

public class HandshakeBufferCodec {
    public static void encode(ByteBuf buf, HandshakeBuffer handshakeBuffer) {
        buf.writeInt(handshakeBuffer.edition());
    }

    public static HandshakeBuffer decode(ByteBuf buf) {
        return new HandshakeBuffer(buf.readInt());
    }
}
