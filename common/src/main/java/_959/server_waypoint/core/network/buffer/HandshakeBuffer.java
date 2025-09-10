package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.HandshakeBufferCodec;
import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.core.network.MessageChannelID.HANDSHAKE_CHANNEL;

public record HandshakeBuffer(int edition) implements MessageBuffer {
    @Override
    public MessageChannelID getChannelId() {
        return HANDSHAKE_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        HandshakeBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return HandshakeBufferCodec.decode(byteBuf);
    }
}
