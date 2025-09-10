package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public interface MessageBuffer {
    MessageChannelID getChannelId();
    void encoderFunction(ByteBuf byteBuf);
    MessageBuffer decoderFunction(ByteBuf byteBuf);

    default MessageBuffer decode(byte[] bytes) {
        ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(bytes);
        return decoderFunction(wrappedBuffer);
    }

    default byte[] encode() {
        ByteBuf buf =  Unpooled.buffer();
        encoderFunction(buf);
        buf.capacity(buf.writerIndex());
        return buf.array();
    }
}
