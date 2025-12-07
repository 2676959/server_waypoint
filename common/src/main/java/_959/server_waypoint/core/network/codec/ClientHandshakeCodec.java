package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import io.netty.buffer.ByteBuf;

public class ClientHandshakeCodec {
    public static void encode(ByteBuf buf) {
        UtfStringCodec.encode(buf, ModInfo.MOD_VERSION);
    }

    public static ClientHandshakeBuffer decode(ByteBuf buf) {
        String version = UtfStringCodec.decode(buf);
        return new ClientHandshakeBuffer(version);
    }
}
