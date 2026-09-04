package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public final class UtfStringCodec {
    private UtfStringCodec() {
    }

    public static void encode(ByteBuf byteBuf, String string, EncodingContext context) {
        byte[] raw = string.getBytes(StandardCharsets.UTF_8);
        context.claimBytes(raw.length);
        byteBuf.writeInt(raw.length);
        byteBuf.writeBytes(raw);
    }

    public static String decode(ByteBuf byteBuf, DecodingContext context) {
        int length = byteBuf.readInt();
        if (length < 0 || length > byteBuf.readableBytes()) {
            throw new IllegalArgumentException("Invalid UTF-8 string length: " + length);
        }
        context.claimBytes(length);
        byte[] raw = new byte[length];
        byteBuf.readBytes(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }
}
