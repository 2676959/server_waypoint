package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import io.netty.buffer.ByteBuf;

/** The fixed-size client protocol handshake. */
public record ClientHandshakeBuffer(int version) implements SinglePacketMessage {
    public ClientHandshakeBuffer() {
        this(ProtocolVersion.PROTOCOL_VERSION);
    }

    @Override
    public MessageChannelID getChannelId() {
        return MessageChannelID.CLIENT_HANDSHAKE_CHANNEL;
    }

    @Override
    public void encode(ByteBuf byteBuf) {
        ClientHandshakeCodec.encode(byteBuf, this);
    }
}
