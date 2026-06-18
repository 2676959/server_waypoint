package _959.server_waypoint.core.network.codec;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class UtfStringCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_network_codec");
    private static final int MAX_BYTES = 255;

    public static void encode(ByteBuf byteBuf, String string) {
        byte[] raw = string.getBytes(StandardCharsets.UTF_8);
        int length = raw.length;
        if (length > MAX_BYTES) {
            LOGGER.error("String is too long to encode: {} bytes. It will be truncated to {} bytes; otherwise waypoint sync packets may be corrupted or clients may disconnect.", length, MAX_BYTES);
            raw = truncateUtf8(string);
            length = raw.length;
        }
        byteBuf.writeByte(length);
        byteBuf.writeBytes(raw);
    }

    public static String decode(ByteBuf byteBuf) {
        int length = byteBuf.readUnsignedByte();
        byte[] raw = new byte[length];
        byteBuf.readBytes(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static byte[] truncateUtf8(String string) {
        StringBuilder truncated = new StringBuilder();
        int byteLength = 0;
        for (int offset = 0; offset < string.length();) {
            int codePoint = string.codePointAt(offset);
            byte[] codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
            if (byteLength + codePointBytes.length > MAX_BYTES) {
                break;
            }
            truncated.appendCodePoint(codePoint);
            byteLength += codePointBytes.length;
            offset += Character.charCount(codePoint);
        }
        return truncated.toString().getBytes(StandardCharsets.UTF_8);
    }
}
