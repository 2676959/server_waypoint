package _959.server_waypoint.core.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class UtfStringCodec {
    public static void encode(ByteBuf byteBuf, String string) {
        int length = Math.min(string.length(), 255);
        byteBuf.writeByte(length);
        byteBuf.writeCharSequence(string, CharsetUtil.UTF_8);
    }

    public static String decode(ByteBuf byteBuf) {
        int length = byteBuf.readByte();
        return byteBuf.readCharSequence(length, CharsetUtil.UTF_8).toString();
    }
}
