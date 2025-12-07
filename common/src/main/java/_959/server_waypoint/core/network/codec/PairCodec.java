package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.util.Pair;
import io.netty.buffer.ByteBuf;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PairCodec {
    public static <L, R> void encode(ByteBuf buf, Pair<L, R> pair, BiConsumer<ByteBuf, L> leftEncoder, BiConsumer<ByteBuf, R> rightEncoder) {
        leftEncoder.accept(buf, pair.left());
        rightEncoder.accept(buf, pair.right());
    }

    public static <L, R, P extends Pair<L, R>> P decode(ByteBuf buf, Function<ByteBuf, L> leftDecoder, Function<ByteBuf, R> rightDecoder, BiFunction<L, R, P> pairConstructor) {
        L left = leftDecoder.apply(buf);
        R right = rightDecoder.apply(buf);
        return pairConstructor.apply(left, right);
    }
}
