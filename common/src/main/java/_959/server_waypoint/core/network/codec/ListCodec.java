package _959.server_waypoint.core.network.codec;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ListCodec {
    public static <T> void encode(ByteBuf byteBuf, List<T> list, BiConsumer<ByteBuf, T> encoder) {
        byteBuf.writeInt(list.size());
        for (T item : list) {
            encoder.accept(byteBuf, item);
        }
    }

    public static <T> List<T> decode(ByteBuf byteBuf, Function<ByteBuf, T> decoder) {
        int arrayLength = byteBuf.readInt();
        List<T> list = new ArrayList<>(arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            T item = decoder.apply(byteBuf);
            list.add(item);
        }
        return list;
    }
}
