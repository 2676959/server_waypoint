package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import io.netty.buffer.ByteBuf;

public class ClientHandshakeCodec {
    public static void encode(ByteBuf buf) {
        buf.writeInt(ProtocolVersion.PROTOCOL_VERSION);
    }

    public static ClientHandshakeBuffer decode(ByteBuf buf) {
        return new ClientHandshakeBuffer(buf.readInt());
    }
}
