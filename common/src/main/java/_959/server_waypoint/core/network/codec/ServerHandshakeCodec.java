package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import io.netty.buffer.ByteBuf;

public class ServerHandshakeCodec {
    public static void encode(ByteBuf buf, ServerHandshakeBuffer handshake) {
        buf.writeInt(ProtocolVersion.PROTOCOL_VERSION);
        buf.writeInt(handshake.serverId());
    }

    public static ServerHandshakeBuffer decode(ByteBuf buf) {
        int version = buf.readInt();
        int serverId = buf.readInt();
        return new ServerHandshakeBuffer(version, serverId);
    }
}
