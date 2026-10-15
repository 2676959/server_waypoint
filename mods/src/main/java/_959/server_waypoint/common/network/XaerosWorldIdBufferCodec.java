package _959.server_waypoint.common.network;

import io.netty.buffer.ByteBuf;

public class XaerosWorldIdBufferCodec {
    public static void encode(ByteBuf buffer, XaerosWorldIdBuffer worldIdBuffer) {
        buffer.writeByte(0);
        buffer.writeInt(worldIdBuffer.id());
    }

    public static XaerosWorldIdBuffer decode(ByteBuf buffer) {
        buffer.readByte();
        int id = buffer.readInt();
        return new XaerosWorldIdBuffer(id);
    }
}
