package _959.server_waypoint.core.network.codec;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ListCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_network_codec");
    private static final int MAX_ITEMS = 10_000;

    public static <T> void encode(ByteBuf byteBuf, List<T> list, BiConsumer<ByteBuf, T> encoder) {
        byteBuf.writeInt(list.size());
        for (T item : list) {
            encoder.accept(byteBuf, item);
        }
    }

    public static <T> List<T> decode(ByteBuf byteBuf, Function<ByteBuf, T> decoder) {
        int arrayLength = byteBuf.readInt();
        if (arrayLength < 0 || arrayLength > MAX_ITEMS) {
            LOGGER.error("Invalid list length in network payload: {}. Ignoring this list; otherwise decoding may allocate excessive memory and crash the game.", arrayLength);
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>(arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            T item = decoder.apply(byteBuf);
            list.add(item);
        }
        return list;
    }
}
