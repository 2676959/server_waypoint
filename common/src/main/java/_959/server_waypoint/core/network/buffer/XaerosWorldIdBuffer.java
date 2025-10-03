package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.XaerosWorldIdBufferCodec;
import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.core.network.MessageChannelID.XAEROS_WORLD_ID_CHANNEL;

public record XaerosWorldIdBuffer(int id) implements MessageBuffer {
    @Override
    public MessageChannelID getChannelId() {
        return XAEROS_WORLD_ID_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        XaerosWorldIdBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return XaerosWorldIdBufferCodec.decode(byteBuf);
    }
}
