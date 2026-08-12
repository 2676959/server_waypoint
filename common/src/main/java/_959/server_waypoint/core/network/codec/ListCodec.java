package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class ListCodec {
    private static final int INITIAL_CAPACITY_LIMIT = 64;

    private ListCodec() {
    }

    public static <T> void encode(
            ByteBuf byteBuf,
            List<T> list,
            MessageCodec.Encoder<T> encoder,
            EncodingContext context
    ) {
        byteBuf.writeInt(list.size());
        for (T item : list) {
            encoder.encode(byteBuf, item, context);
        }
    }

    public static <T> List<T> decode(
            ByteBuf byteBuf,
            MessageCodec.Decoder<T> decoder,
            DecodingContext context
    ) {
        int arrayLength = byteBuf.readInt();
        if (arrayLength < 0) {
            throw new IllegalArgumentException("Invalid list length: " + arrayLength);
        }
        if (arrayLength > byteBuf.readableBytes()) {
            throw new IllegalArgumentException(
                    "List length cannot fit in the remaining message bytes: " + arrayLength
            );
        }
        List<T> list = new ArrayList<>(Math.min(arrayLength, INITIAL_CAPACITY_LIMIT));
        for (int i = 0; i < arrayLength; i++) {
            context.claimObject();
            int readerIndex = byteBuf.readerIndex();
            T item;
            try {
                item = decoder.decode(byteBuf, context);
            } catch (IndexOutOfBoundsException exception) {
                throw new IllegalArgumentException(
                        "List ended before its declared element count",
                        exception
                );
            }
            if (byteBuf.readerIndex() <= readerIndex) {
                throw new IllegalArgumentException("List element decoder did not consume any bytes");
            }
            list.add(item);
        }
        return list;
    }
}
