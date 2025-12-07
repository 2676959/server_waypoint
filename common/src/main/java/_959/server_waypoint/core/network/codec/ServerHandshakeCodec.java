package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import io.netty.buffer.ByteBuf;

public class ServerHandshakeCodec {
    public static void encode(ByteBuf buf, ServerHandshakeBuffer handshake) {
        UtfStringCodec.encode(buf, ModInfo.MOD_VERSION);
        buf.writeInt(handshake.serverId());
    }

    public static ServerHandshakeBuffer decode(ByteBuf buf) {
        String version = UtfStringCodec.decode(buf);
        int serverId = buf.readInt();
        return new ServerHandshakeBuffer(version, serverId);
    }
}
