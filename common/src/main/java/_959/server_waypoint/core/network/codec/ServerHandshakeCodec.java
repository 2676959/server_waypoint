package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import io.netty.buffer.ByteBuf;

public class ServerHandshakeCodec {
    public static void encode(ByteBuf buf, ServerHandshakeBuffer handshake) {
        buf.writeInt(handshake.version());
        buf.writeInt(handshake.serverId());
        buf.writeBoolean(handshake.compressChunkedMessages());
    }

    public static ServerHandshakeBuffer decode(ByteBuf buf) {
        int version = buf.readInt();
        int serverId = buf.readInt();
        boolean compressChunkedMessages = buf.readBoolean();
        return new ServerHandshakeBuffer(version, serverId, compressChunkedMessages);
    }
}
