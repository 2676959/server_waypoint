package _959.server_waypoint.core.network.codec;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

public class UtfStringCodec {
    public static void encode(ByteBuf byteBuf, String string) {
        byte[] raw = string.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(raw.length, 255);
        byteBuf.writeByte(length);
        byteBuf.writeBytes(raw);
    }

    public static String decode(ByteBuf byteBuf) {
        int length = byteBuf.readByte();
        byte[] raw = new byte[length];
        byteBuf.readBytes(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }
}
