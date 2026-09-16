package _959.server_waypoint.core.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Encodes a direct message completely before a platform networking API sees it. */
public final class SinglePacketMessageEncoder {
    public static final int MAX_ENCODED_BYTES = 30 * 1_024;

    private SinglePacketMessageEncoder() {
    }

    public static byte[] encode(SinglePacketMessage message) {
        ByteBuf buffer = Unpooled.buffer(Math.min(256, MAX_ENCODED_BYTES), MAX_ENCODED_BYTES);
        try {
            message.encode(buffer);
            int size = buffer.readableBytes();
            if (size > MAX_ENCODED_BYTES) {
                throw new MessageEncodingException(
                        "Single-packet message exceeds " + MAX_ENCODED_BYTES + " bytes"
                );
            }
            byte[] encoded = new byte[size];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } catch (MessageEncodingException exception) {
            throw exception;
        } catch (IndexOutOfBoundsException exception) {
            throw new MessageEncodingException(
                    "Single-packet message exceeds " + MAX_ENCODED_BYTES + " bytes",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new MessageEncodingException("Failed to encode single-packet message", exception);
        } finally {
            buffer.release();
        }
    }
}
