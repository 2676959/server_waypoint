package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.util.Pair;
import io.netty.buffer.ByteBuf;

import java.util.function.BiFunction;

public final class PairCodec {
    private PairCodec() {
    }

    public static <L, R> void encode(
            ByteBuf buf,
            Pair<L, R> pair,
            MessageCodec.Encoder<L> leftEncoder,
            MessageCodec.Encoder<R> rightEncoder,
            EncodingContext context
    ) {
        leftEncoder.encode(buf, pair.left(), context);
        rightEncoder.encode(buf, pair.right(), context);
    }

    public static <L, R, P extends Pair<L, R>> P decode(
            ByteBuf buf,
            MessageCodec.Decoder<L> leftDecoder,
            MessageCodec.Decoder<R> rightDecoder,
            BiFunction<L, R, P> pairConstructor,
            DecodingContext context
    ) {
        L left = leftDecoder.decode(buf, context);
        R right = rightDecoder.decode(buf, context);
        return pairConstructor.apply(left, right);
    }
}
