package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import io.netty.buffer.ByteBuf;

/**
 * First packet in the communication between client and server
 * */
public record ClientHandshakeBuffer(String version) implements MessageBuffer {
    public ClientHandshakeBuffer() {
        this(ModInfo.MOD_VERSION);
    }

    @Override
    public MessageChannelID getChannelId() {
        return MessageChannelID.CLIENT_HANDSHAKE_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        ClientHandshakeCodec.encode(byteBuf);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return ClientHandshakeCodec.decode(byteBuf);
    }
}
