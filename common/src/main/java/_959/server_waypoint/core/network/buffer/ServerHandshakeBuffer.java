package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.ServerHandshakeCodec;
import io.netty.buffer.ByteBuf;

/**
 * Second packet in the communication between client and server
 * */
public record ServerHandshakeBuffer(String version, int serverId) implements MessageBuffer {
    public ServerHandshakeBuffer(int serverId) {
        this(ModInfo.MOD_VERSION, serverId);
    }

    @Override
    public MessageChannelID getChannelId() {
        return MessageChannelID.SERVER_HANDSHAKE_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        ServerHandshakeCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return ServerHandshakeCodec.decode(byteBuf);
    }
}
