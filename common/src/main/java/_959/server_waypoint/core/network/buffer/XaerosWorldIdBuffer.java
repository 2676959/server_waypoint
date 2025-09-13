package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.XaerosWorldIdBufferCodec;
import io.netty.buffer.ByteBuf;

public record XaerosWorldIdBuffer(int id) implements MessageBuffer {
    @Override
    public MessageChannelID getChannelId() {
        return null;
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
