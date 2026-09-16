package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.codec.ServerHandshakeCodec;
import io.netty.buffer.ByteBuf;

/** The fixed-size server protocol handshake and chunk-compression capability. */
public record ServerHandshakeBuffer(
        int version,
        int serverId,
        boolean compressChunkedMessages
) implements SinglePacketMessage {
    public ServerHandshakeBuffer(int serverId, boolean compressChunkedMessages) {
        this(ProtocolVersion.PROTOCOL_VERSION, serverId, compressChunkedMessages);
    }

    @Override
    public MessageChannelID getChannelId() {
        return MessageChannelID.SERVER_HANDSHAKE_CHANNEL;
    }

    @Override
    public void encode(ByteBuf byteBuf) {
        ServerHandshakeCodec.encode(byteBuf, this);
    }
}
